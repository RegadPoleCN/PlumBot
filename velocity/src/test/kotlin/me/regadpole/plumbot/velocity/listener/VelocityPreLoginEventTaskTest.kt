// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.velocity.listener

import com.velocitypowered.api.event.Continuation
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.proxy.InboundConnection
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.server.GameEventBridge
import me.regadpole.plumbot.server.PlayerLoginService
import net.kyori.adventure.text.Component
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * PreLogin 白名单校验的异步化契约：校验跑在 Velocity 的事件任务里（不再阻塞事件线程），
 * 且 Continuation 必须无条件 resume（放行、拒绝、fail-closed 三条路径都要返回事件）。
 */
class VelocityPreLoginEventTaskTest {

    /** 全键缺省（功能门全关）的空配置；booleans=true 时 getBoolean 恒真，用于打开白名单门。 */
    private class StubConfig(private val booleans: Boolean = false) : PlatformConfig {
        override fun getString(vararg path: String?): String? = null
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = booleans
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class StubContext(config: PlatformConfig) : PlatformContext {
        override val config: PlatformConfig = config
        override val datasource: PlatformConfig = StubConfig()
        override val dataDirectory: Path = Path.of("build/tmp/plumbot-velocity-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { false }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { false }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { false }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { false }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { false }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { false }
        }
        override val playerService: PlatformPlayerService = object : PlatformPlayerService {
            override fun kickPlayer(name: String) {}
            override fun listPlayers(): List<String> = emptyList()
            override fun listPlayerString(): String = ""
        }
        override val platformType: PlatformType = PlatformType.VELOCITY
        val logs = mutableListOf<String>()
        override fun log(level: LogLevel, message: String) { logs += message }
        override fun sendMessage(message: Component) {}
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun stubConnection(): InboundConnection = java.lang.reflect.Proxy.newProxyInstance(
        InboundConnection::class.java.classLoader,
        arrayOf<Class<*>>(InboundConnection::class.java)
    ) { _, method, _ -> if (method.name == "isActive") true else null } as InboundConnection

    private fun runTask(listener: VelocityServerListener, username: String): PreLoginEvent {
        val event = PreLoginEvent(stubConnection(), username)
        val task = listener.onPreLogin(event)
        val resumed = AtomicBoolean(false)
        val continuation = object : Continuation {
            override fun resume() { resumed.set(true) }
            override fun resumeWithException(exception: Throwable) { resumed.set(true) }
        }
        task.execute(continuation)
        assertTrue(resumed.get(), "Continuation 必须无条件 resume，否则事件处理挂起")
        return event
    }

    @Test
    fun `onPreLogin defers the check to an async event task and resumes`() {
        val context = StubContext(StubConfig())
        val listener = VelocityServerListener(
            platform = context,
            eventBridge = GameEventBridge(context),
            loginService = PlayerLoginService(context)
        )

        val event = runTask(listener, "Steve")

        assertTrue(event.result.isAllowed, "功能门关闭时放行")
    }

    @Test
    fun `onPreLogin denies via the async task when the database is unavailable`() {
        // whitelist 开着但数据库未启动：fail-closed 拒绝路径（LOOKUP_UNAVAILABLE）
        val context = StubContext(StubConfig(booleans = true))
        val listener = VelocityServerListener(
            platform = context,
            eventBridge = GameEventBridge(context),
            loginService = PlayerLoginService(context)
        )

        val event = runTask(listener, "Steve")

        assertFalse(event.result.isAllowed, "fail-closed：查不清就拒绝")
    }
}
