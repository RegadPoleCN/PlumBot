/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.bot.command

import kotlinx.coroutines.Dispatchers
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.RenderCard
import me.regadpole.plumbot.render.RenderConfig
import me.regadpole.plumbot.render.RenderPortal
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import kotlinx.datetime.Instant
import net.kyori.adventure.text.Component
import java.io.File
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 头像卡在**旧模板**（没有 `avatar` 路由）上的回落回归测试。
 *
 * 插件已改发 [RenderRoute.AVATAR]，但 PlumBot-Web 模板的 `#/avatar` 视图尚未发版，模板
 * manifest 会把未声明的路由判为 `PW_TEMPLATE_INVALID`（`模板未实现 route 'avatar'（已实现：…）`），
 * 头像卡整体退化成纯文本。
 *
 * `sendAvatarCard` 有**两道防线**：① 先用 [RenderPortal.supportsRoute] 探测，已知未声明就直走
 * 旧路由（否则默认 `fallback_to_awt=true` 会把它画成一张没有头像的文字图，回落逻辑看不到
 * Failed）；② 探测未知时按"主失败 → 旧路由重试一次"处理。本测试钉住：
 *
 * 1. 探测到模板未声明 `avatar` → **不发起** `avatar` 渲染，直接旧路由出图；
 * 2. 探测到已声明 → 只走 `avatar`；
 * 3. 探测未知且主路由因"路由未实现"失败 → 自动回落并**成功发出图片**；
 * 4. 两条路由都失败 → 只发一条纯文本，且**不循环**（路由序列恰好两条）；
 * 5. 失败原因不是"路由未实现"（如 manifest 缺失）→ **不重试**，直接纯文本；
 * 6. 头像开关 `render.outputs.avatar=false` → 根本不去渲染；
 * 7. 纯文本降级带卡片标题（`RenderCards.plain(card)`，与图片侧同口径）。
 *
 * `RenderProvider.portal` 是私有单例，`common` 无法装配真实引擎；[BotCommandService] 因此接收
 * `portalSupplier` 与 `renderDispatcher`：测试注入替身门户 + `Dispatchers.Unconfined` 同步执行。
 */
class AvatarRouteFallbackTest {

    private val groupId = 100L
    private val playerName = "Steve"

    /** 模板 manifest 拒绝未声明路由时的真实判词形状（见浏览器引擎驱动层的 manifest 校验）。 */
    private val routeMissingReason =
        "[PW_TEMPLATE_INVALID] stage=handshake route=avatar: 模板未实现 route 'avatar'（已实现：status,hytaleAvatar）" +
            " | 处理建议: 升级 PlumBot-Web 模板"

    /** 同样是 PW_TEMPLATE_INVALID，但原因不是"缺路由"，回落旧路由也救不了。 */
    private val manifestMissingReason =
        "[PW_TEMPLATE_INVALID] stage=handshake route=avatar: 模板未在挂载前调用 installManifest()" +
            " | 处理建议: 重新生成模板"

    private lateinit var database: InMemoryDatabase
    private lateinit var context: FakeContext
    private lateinit var bot: FakeBot
    private lateinit var portal: FakePortal

    @BeforeTest
    fun setUp() {
        database = InMemoryDatabase()
        DatabaseProvider.start(database)
        context = FakeContext(FakeConfig())
        bot = FakeBot()
        context.set("render.outputs.avatar", true)
    }

    @AfterTest
    fun tearDown() {
        DatabaseProvider.shutdown()
    }

    private fun service() = BotCommandService(
        context,
        bot,
        portalSupplier = { portal },
        renderDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun `旧模板缺少 avatar 路由时自动回落到 hytaleAvatar 并出图`() {
        portal = FakePortal(
            avatarResult = RenderResult.Failed(routeMissingReason),
            hytaleResult = RenderResult.Image(byteArrayOf(1, 2, 3))
        )

        service().sendAvatarCard(groupId, playerName, "绑定成功")

        assertEquals(
            listOf(RenderRoute.AVATAR.route, RenderRoute.HYTALE_AVATAR.route),
            portal.routes,
            "主路由失败后必须且只能用旧路由重试一次"
        )
        assertEquals(1, bot.imageSends, "回落成功时应真正发出图片，而不是降级为纯文本")
        assertEquals(emptyList<String>(), bot.sentMessages, "既然图片已发出，就不应再发降级文本")
        assertTrue(
            context.logs.any { it.contains("回落") && it.contains("hytaleAvatar") },
            "回落必须留一条可诊断的 INFO（否则日志上完全看不出用户为什么没换样式），实际：${context.logs}"
        )
    }

    /**
     * 探测先行：模板 manifest 明确没声明 `avatar` 时**不发起** `avatar` 渲染。
     *
     * 这是 AWT 兜底场景的核心回归：`fallback_to_awt=true` 会把 `avatar` 的握手失败用纯文字图
     * 接住（轻量引擎忽略 viewRoute），"失败后重试"永远等不到 Failed。
     */
    @Test
    fun `模板未声明 avatar 时直接走旧路由且不发起 avatar 渲染`() {
        portal = FakePortal(
            avatarResult = RenderResult.Failed(routeMissingReason),
            hytaleResult = RenderResult.Image(byteArrayOf(1, 2, 3)),
            routeSupport = false
        )

        service().sendAvatarCard(groupId, playerName, "绑定成功")

        assertEquals(
            listOf(RenderRoute.HYTALE_AVATAR.route),
            portal.routes,
            "已知模板缺 avatar 就该直接走旧路由；再试一次 avatar 只会被 AWT 兜成文字图"
        )
        assertEquals(1, bot.imageSends, "旧路由命中时仍应真正发出图片")
        assertEquals(emptyList<String>(), bot.sentMessages)
        assertTrue(
            context.logs.any { it.contains("回落") && it.contains("hytaleAvatar") },
            "探测到旧模板同样要留 INFO，实际：${context.logs}"
        )
    }

    @Test
    fun `模板已声明 avatar 时只走 avatar 路由`() {
        portal = FakePortal(
            avatarResult = RenderResult.Image(byteArrayOf(9)),
            hytaleResult = RenderResult.Image(byteArrayOf(1)),
            routeSupport = true
        )

        service().sendAvatarCard(groupId, playerName, "绑定成功")

        assertEquals(listOf(RenderRoute.AVATAR.route), portal.routes, "新模板不该被旧路由抢走")
        assertEquals(1, bot.imageSends)
    }

    /** 降级文本必须与图片侧同口径：标题 + 正文（见 `RenderCard.displayText`）。 */
    @Test
    fun `降级文本保留卡片标题`() {
        service().sendCard(
            groupId,
            RenderRoute.BIND_LIST,
            RenderCard(text = "已删除 Steve", title = "白名单已删除 · Steve"),
            pic = false
        )

        // 载荷在协程内构建的重载（如玩家列表）同样要带上标题。
        service().sendCard(
            groupId,
            RenderRoute.PLAYER_LIST,
            text = "玩家列表内容",
            title = "在线玩家",
            pic = false
        )

        assertEquals(
            listOf(
                "白名单已删除 · Steve\n已删除 Steve",
                "在线玩家\n玩家列表内容"
            ),
            bot.sentMessages,
            "只投影正文会丢掉标题里的对象信息，与图片侧口径不一致"
        )
    }

    @Test
    fun `两条路由都失败时只发一条纯文本且不循环`() {
        portal = FakePortal(
            avatarResult = RenderResult.Failed(routeMissingReason),
            hytaleResult = RenderResult.Failed("[PW_TEMPLATE_INVALID] stage=render route=hytaleAvatar: boom")
        )

        service().sendAvatarCard(groupId, playerName, "绑定成功")

        assertEquals(
            listOf(RenderRoute.AVATAR.route, RenderRoute.HYTALE_AVATAR.route),
            portal.routes,
            "重试只能一次：不允许在两条路由之间循环"
        )
        assertEquals(0, bot.imageSends)
        assertEquals(listOf("绑定成功"), bot.sentMessages, "降级文本必须与卡片正文同源")
        assertEquals(listOf(false), bot.picFlags)
    }

    @Test
    fun `manifest 缺失不属于路由未实现，不触发回落`() {
        portal = FakePortal(
            avatarResult = RenderResult.Failed(manifestMissingReason),
            hytaleResult = RenderResult.Image(byteArrayOf(1))
        )

        service().sendAvatarCard(groupId, playerName, "绑定成功")

        assertEquals(listOf(RenderRoute.AVATAR.route), portal.routes, "换旧路由同样画不出来，不应白跑第二次")
        assertEquals(0, bot.imageSends)
        assertEquals(listOf("绑定成功"), bot.sentMessages)
    }

    @Test
    fun `头像开关关闭时直接纯文本且不碰渲染引擎`() {
        portal = FakePortal(RenderResult.Image(byteArrayOf(1)), RenderResult.Image(byteArrayOf(1)))
        context.set("render.outputs.avatar", false)

        service().sendAvatarCard(groupId, playerName, "绑定成功")

        assertEquals(emptyList<String>(), portal.routes, "门控先于一切异步准备：开关关闭时连 payload 都不该求值")
        assertEquals(0, bot.imageSends)
        assertEquals(listOf("绑定成功"), bot.sentMessages)
    }

    /** 判词识别本身也钉住：两种 PW_TEMPLATE_INVALID 必须被区分开。 */
    @Test
    fun `判词识别区分缺路由与其它模板错误`() {
        val service = service()
        assertTrue(service.avatarRouteMissing(routeMissingReason))
        assertFalse(service.avatarRouteMissing(manifestMissingReason))
        assertFalse(service.avatarRouteMissing("[PW_TIMEOUT] stage=render route=avatar: 30s"))
        assertFalse(
            service.avatarRouteMissing("模板未实现 route 'hytaleAvatar'（已实现：avatar）"),
            "只有主路由 avatar 缺失才值得回落"
        )
    }

    private class FakePortal(
        private val avatarResult: RenderResult,
        private val hytaleResult: RenderResult,
        /** 模板探测结论：null = 未知（默认，等价于真实门户"还没握手过"）。 */
        private val routeSupport: Boolean? = null
    ) : RenderPortal {

        /** 实际被请求的路由序列：用来证明"只重试一次、不循环"。 */
        val routes = mutableListOf<String>()

        override val isImageCapable: Boolean get() = true

        override fun configure(config: RenderConfig) = Unit
        override fun reload() = Unit
        override fun shutdown() = Unit

        override suspend fun render(request: RenderRequest): RenderResult {
            routes += request.route
            return when (request.route) {
                RenderRoute.AVATAR.route -> avatarResult
                RenderRoute.HYTALE_AVATAR.route -> hytaleResult
                else -> RenderResult.Unavailable
            }
        }

        override fun supportsRoute(route: RenderRoute): Boolean? = routeSupport

        override suspend fun fetchAvatarAsDataUrl(cacheKey: String, url: String): String? = null
        override suspend fun resolveSkinAvatar(playerName: String): String? = null
        override suspend fun composeAvatar(request: AvatarComposeRequest): String? = null
    }

    private class FakeConfig : PlatformConfig {
        private val values = mutableMapOf<String, Any>(
            "feature.bind.enable" to true,
            "feature.bind.maxNum" to 5,
            "render.outputs.avatar" to false
        )

        fun set(key: String, value: Any) {
            values[key] = value
        }

        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")

        override fun getString(vararg path: String?): String? = values[key(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> =
            @Suppress("UNCHECKED_CAST")
            (values[key(path)] as? List<Long>) ?: emptyList()

        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class FakePlayerService : PlatformPlayerService {
        override fun kickPlayer(name: String) = Unit
        override fun listPlayers(): List<String> = emptyList()
        override fun listPlayerString(): String = ""
    }

    private class FakeContext(private val fakeConfig: FakeConfig) : PlatformContext {
        val logs = mutableListOf<String>()
        private val fakePlayerService = FakePlayerService()

        fun set(key: String, value: Any) = fakeConfig.set(key, value)

        override val config: PlatformConfig get() = fakeConfig
        override val datasource: PlatformConfig get() = fakeConfig
        override val dataDirectory: Path get() = TODO("测试未使用")
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = fakePlayerService
        override val platformType: PlatformType get() = PlatformType.BUKKIT

        override fun log(level: LogLevel, message: String) {
            logs += "$level $message"
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class FakeBot : IBot {
        val sentMessages = mutableListOf<String>()
        val picFlags = mutableListOf<Boolean>()
        var imageSends = 0

        override var handler: BotHandler? = null

        override val metadata: BotAdapterMetadata
            get() = BotAdapterMetadata(
                type = "fake",
                displayName = "Fake",
                capabilities = setOf(BotCapability.GROUP_MESSAGE_SEND, BotCapability.IMAGE_SEND)
            )

        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = true

        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
            message?.let {
                sentMessages += it
                picFlags += isPic
            }
        }

        override fun sendGroupMsg(targetId: Long, message: String) {
            sentMessages += message
            picFlags += false
        }

        override fun sendUserMsg(targetId: Long, message: String) {
            sentMessages += message
            picFlags += false
        }

        override fun sendGroupPicWithText(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun sendUserPicWithText(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun getGroupUserName(groupId: Long, targetId: Long): String = "user$targetId"
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = "card$targetId"

        override fun sendGroupImage(targetId: Long, imageFile: File): Boolean {
            imageSends++
            return true
        }

        override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) = Unit
        override fun sendGroupMsgAtAll(targetId: Long, message: String) = Unit
    }

    private class InMemoryDatabase : IDatabase {
        val bindings = mutableListOf<Binding>()
        private var nextId = 1

        override fun initialize() = Unit
        override fun close() = Unit
        override fun getConnection(): DataSource = TODO("测试未使用")

        override fun getByUser(user: String): List<Binding> = bindings.filter { it.userId == user }
        override fun getByName(name: String): Binding? =
            bindings.firstOrNull { it.playerName.equals(name, ignoreCase = true) }

        override fun getById(id: Int): Binding? = bindings.firstOrNull { it.id == id }

        override fun addBind(user: String, name: String) {
            bindings += Binding(nextId++, user, name, null, Instant.fromEpochMilliseconds(0))
        }

        override fun removeBindByNum(user: String, id: Int): String? {
            val target = bindings.firstOrNull { it.userId == user && it.id == id } ?: return null
            bindings.remove(target)
            return target.playerName
        }

        override fun removeBindByUser(user: String) {
            bindings.removeIf { it.userId == user }
        }

        override fun removeBind(name: String) {
            bindings.removeIf { it.playerName.equals(name, ignoreCase = true) }
        }

        override fun setUUID(name: String, uuid: UUID) = Unit
    }
}
