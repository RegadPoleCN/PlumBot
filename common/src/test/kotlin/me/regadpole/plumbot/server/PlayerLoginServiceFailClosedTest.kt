package me.regadpole.plumbot.server

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.database.BindingLookupService
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.database.SQLite
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.testing.FakePlatformContext
import taboolib.module.database.Database
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 登录白名单（fail-closed）的行为契约。
 *
 * 三条被测语义：
 * 1. `feature.bind.enable` / `feature.bind.whitelist` 任一为假（含缺失）就放行 —— 门在查询之前；
 * 2. 查清「没绑定」→ NOT_BOUND（引导加群）；查不清（超时/未就绪/异常）→ LOOKUP_UNAVAILABLE（拒绝 + 计数 + 限频 WARN）；
 * 3. 两种拒绝必须给出**不同**的玩家文案：校验通道故障时不该让玩家以为是自己没申请。
 *
 * 数据库用真实 SQLite：这几条断言的价值在于「未就绪 / 慢查询」确实被观察到，而不是被 mock 假造。
 */
class PlayerLoginServiceFailClosedTest {

    private val tempDir: Path = Files.createTempDirectory("plumbot-login-test")
    private val databases = mutableListOf<IDatabase>()

    private var savedKickServer: String = ""
    private var savedKickPlatform: String = ""
    private var savedKickUnavailable: String = ""

    /** 只覆写 getByName 的计数/延迟包装，其余委托真实 SQLite。 */
    private class CountingDatabase(
        private val delegate: IDatabase,
        private val delayMillis: Long,
    ) : IDatabase by delegate {

        val calls = AtomicInteger()

        override fun getByName(name: String): Binding? {
            calls.incrementAndGet()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            return delegate.getByName(name)
        }
    }

    @BeforeTest
    fun setUp() {
        BindingLookupService.resetForTests()
        HealthCounters.reset()
        savedKickServer = Messages.kickServer
        savedKickPlatform = Messages.kickPlatform
        savedKickUnavailable = Messages.kickUnavailable
        // 默认文案是 <missing:xxx> 占位符，MiniMessage 解析会炸；测试里换成真实文案。
        Messages.kickServer = "请加入qq群:123456 申请白名单"
        Messages.kickPlatform = "%player_name% 未在白名单内而被踢出服务器"
        Messages.kickUnavailable = "服务器暂时无法校验白名单，请稍后重试"
    }

    @AfterTest
    fun tearDown() {
        Messages.kickServer = savedKickServer
        Messages.kickPlatform = savedKickPlatform
        Messages.kickUnavailable = savedKickUnavailable
        DatabaseProvider.shutdown()
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        BindingLookupService.resetForTests()
        HealthCounters.reset()
        runCatching {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun startDatabase(delayMillis: Long = 0L): CountingDatabase {
        if (!settingsInitialized) {
            val config = YamlConfigurator.createConfig(tempDir, "datasource.yml")
                ?: error("测试无法加载 datasource.yml")
            Database.settingsFile = DatabaseSource(config.getNode())
            settingsInitialized = true
        }
        val sqlite = SQLite(tempDir.resolve("binding.db").absolutePathString())
        databases += sqlite
        val counting = CountingDatabase(sqlite, delayMillis)
        DatabaseProvider.start(counting)
        return counting
    }

    private fun service(
        config: Map<String, Any> = enabledConfig(),
        context: FakePlatformContext? = null,
    ): PlayerLoginService {
        val ctx = context ?: FakePlatformContext(config)
        return PlayerLoginService(ctx)
    }

    private fun unavailableCounter(): Long = HealthCounters.get(HealthCounters.Key.LOGIN_LOOKUP_UNAVAILABLE)

    private fun unavailableWarns(context: FakePlatformContext): List<String> =
        context.logs.filter { it.contains("白名单校验暂时不可用") }

    @Test
    fun `the bind switch turned off skips the check entirely`() {
        val context = FakePlatformContext(enabledConfig() + mapOf("feature.bind.enable" to false))

        val result = service(context = context).check("Alex")

        assertTrue(result.allowed, "绑定功能关掉后登录不该再被白名单挡住")
        assertEquals(PreLoginDenial.NONE, result.denial)
    }

    @Test
    fun `the whitelist switch turned off is the emergency release`() {
        val context = FakePlatformContext(enabledConfig() + mapOf("feature.bind.whitelist" to false))

        val result = service(context = context).check("Alex")

        assertTrue(result.allowed, "whitelist=false 是数据库故障时的应急放行通道")
    }

    @Test
    fun `a missing bind switch is treated as off`() {
        val context = FakePlatformContext(mapOf("feature.bind.whitelist" to true))

        assertTrue(service(context = context).check("Alex").allowed)
    }

    @Test
    fun `a bound player is allowed`() {
        startDatabase().addBind("10001", "Steve")

        val result = service().check("Steve")

        assertTrue(result.allowed)
        assertEquals(PreLoginDenial.NONE, result.denial)
    }

    @Test
    fun `an unbound player is denied as not bound`() {
        startDatabase()

        val result = service().check("Alex")

        assertFalse(result.allowed)
        assertEquals(PreLoginDenial.NOT_BOUND, result.denial, "查清没绑定必须与查不清区分开")
        assertTrue(result.kickMessage!!.contains("申请白名单"), "要用加群引导：${result.kickMessage}")
    }

    @Test
    fun `an unavailable lookup denies the player and is counted`() {
        DatabaseProvider.shutdown()
        val context = FakePlatformContext(enabledConfig())

        val result = service(context = context).check("Steve")

        assertFalse(result.allowed, "fail-closed：查不清就拒绝")
        assertEquals(PreLoginDenial.LOOKUP_UNAVAILABLE, result.denial)
        assertEquals(1L, unavailableCounter())
        assertEquals(1, unavailableWarns(context).size, "第一场故障要留下一条 WARN：${context.logs}")
        assertTrue(unavailableWarns(context).first().contains("feature.bind.whitelist"), "WARN 要给出应急通道")
    }

    @Test
    fun `the unavailable warning is throttled but every rejection is counted`() {
        DatabaseProvider.shutdown()
        val context = FakePlatformContext(enabledConfig())
        val login = service(context = context)

        login.check("Steve")
        login.check("Alex")
        login.check("Notch")

        assertEquals(3L, unavailableCounter(), "每一次拒绝都要计数，不能只计第一条")
        assertEquals(1, unavailableWarns(context).size, "同一场故障只留一条 WARN：${context.logs}")
    }

    @Test
    fun `a slow lookup beyond the timeout is denied as unavailable`() {
        startDatabase(delayMillis = 400L)
        val context = FakePlatformContext(
            enabledConfig() + mapOf("feature.bind.login_lookup_timeout_ms" to 50L)
        )

        val result = service(context = context).check("Steve")

        assertFalse(result.allowed)
        assertEquals(PreLoginDenial.LOOKUP_UNAVAILABLE, result.denial)
        assertEquals(1L, unavailableCounter())
    }

    @Test
    fun `the unavailable message is not the join group guide`() {
        DatabaseProvider.shutdown()
        val context = FakePlatformContext(enabledConfig())

        val result = service(context = context).check("Steve")

        val message = result.kickMessage!!
        assertTrue(message.contains("暂时无法校验"), "玩家应看到故障文案：$message")
        assertFalse(message.contains("申请白名单"), "故障时不该让玩家以为是自己没申请：$message")
    }

    private companion object {
        var settingsInitialized = false

        fun enabledConfig(): Map<String, Any> = mapOf(
            "feature.bind.enable" to true,
            "feature.bind.whitelist" to true
        )
    }
}
