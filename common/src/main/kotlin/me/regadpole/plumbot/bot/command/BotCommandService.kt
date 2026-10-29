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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.bot.command.whitelist.BindPolicy
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.AvatarBlend
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.AvatarLayer
import me.regadpole.plumbot.render.RenderCard
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderPortal
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute

class BotCommandService(
    val context: PlatformContext,
    val bot: IBot,
    /**
     * 渲染门户来源。生产路径恒为 [RenderProvider.getOrNull]；保留构造参数只为**可测** ——
     * 单元测试无法启动真实引擎，需注入替身验证「主路由失败 → 回落旧路由」。
     * [shutdown] 只取消协程，不会替调用方关闭门户。
     */
    private val portalSupplier: () -> RenderPortal? = { RenderProvider.getOrNull() },
    /** 出图协程的调度器；测试用 Unconfined 让 launch 同步执行，避免靠 sleep 等待。 */
    renderDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * 配置读取入口。**必须是 getter，不能缓存成 val**：`/plumbot reload` 会替换
     * `context.config`，缓存旧引用会让门控、别名与文案继续读上一份配置，且不报任何错。
     */
    val config: PlatformConfig get() = context.config

    /** 出图协程作用域（渲染是挂起能力，不能阻塞调用线程）。 */
    private val renderScope = CoroutineScope(renderDispatcher + SupervisorJob())

    fun shutdown() {
        renderScope.cancel()
    }

    fun isFeatureEnabled(feature: String): Boolean = config.getBoolean("feature", feature, "enable")

    /** 绑定写入的唯一决策点（三条写入路径共用，避免各自复制判断）。 */
    val bindPolicy = BindPolicy(this)

    fun isAdmin(userId: Long): Boolean = config.getLongList("admins").contains(userId)

    /**
     * 头像指令是否出图（`render.outputs.avatar`）。
     *
     * [RenderRoute.AVATAR] 与旧 [RenderRoute.HYTALE_AVATAR] 共用同一开关；这里提前判断是为了
     * 在关闭时直接回纯文本，省去皮肤抓取与图层合成。
     */
    private fun avatarPicEnabled(): Boolean = config.getBoolean("render", "outputs", "avatar")

    /** 已提示过"路由出图被配置关闭"（键为 route，避免每条消息刷屏）。 */
    private val notifiedDisabledRoutes = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过"没有任何出图能力"（全局一次）。 */
    @Volatile
    private var warnedRenderUnavailable = false

    /**
     * 路由出图关闭时**一次性 INFO**。这是"说好渲染卡片却只发纯文字"最常见的原因
     * （`render.outputs.<gate>=false` 或 `engine=none`）；静默降级会让服主无从判断是开关还是故障。
     */
    private fun noticeImageDisabled(route: RenderRoute) {
        if (!notifiedDisabledRoutes.add(route.route)) return
        val gate = route.featureGate
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 路由 ${route.route} 的图片输出已关闭" +
                (if (gate != null) "（render.outputs.$gate=false 或引擎为 none）" else "") +
                "，本次及后续回复使用纯文本"
        )
    }

    /** 门户确认"完全没有出图能力"时**一次性 WARN**：没有任何引擎可用，图片永远不会出现。 */
    private fun noticeRenderUnavailable() {
        if (warnedRenderUnavailable) return
        warnedRenderUnavailable = true
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 当前平台没有任何可用的渲染引擎（render.engine=awt 时需 AWT 可用，" +
                "playwright 需装载完成），图片回复将全部降级为纯文本"
        )
    }

    /** 已提示过"旧模板没有 avatar 路由，已自动回落 hytaleAvatar"（会话内一次）。 */
    @Volatile
    private var warnedAvatarRouteFallback = false

    /**
     * 主路由 `avatar` 在**模板层**未实现时的一次性 INFO。插件先于 PlumBot-Web 模板发版时
     * 旧模板只有 `hytaleAvatar`；静默回落会让用户只觉得"头像卡样式没变"。
     */
    private fun noticeAvatarRouteFallback() {
        if (warnedAvatarRouteFallback) return
        warnedAvatarRouteFallback = true
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 模板未实现 route '${RenderRoute.AVATAR.route}'，头像卡已自动回落旧路由 " +
                "'${RenderRoute.HYTALE_AVATAR.route}'（旧模板兼容）。升级 PlumBot-Web 模板后自动恢复新路由，无需改配置"
        )
    }

    /**
     * 平台品牌名，写进头像卡片的 `data.platform`。前端模板据此挑选皮肤/头像图层，
     * 取值与 PlumBot-Web 约定一致：`bukkit` / `velocity` / `hytale` / `unknown`。
     */
    private fun platformBrand(): String = when (context.platformType) {
        PlatformType.BUKKIT -> "bukkit"
        PlatformType.VELOCITY, PlatformType.BUNGEE -> "velocity"
        PlatformType.HYTALE -> "hytale"
        PlatformType.STANDALONE -> "unknown"
    }

    /** 已提示过"适配器不支持成员校验"（去重，避免每条指令刷屏）。 */
    @Volatile
    private var warnedNoMemberCheck = false

    /**
     * 管理员**代绑**前的成员校验（`feature.bind.require_in_group`）。
     *
     * ## 只用于代绑路径
     *
     * 本方法**只应被"管理员为第三方 QQ 代绑"这一条路径调用**。理由：
     * 所有指令都经 `BotHandler.onGroupMessage` 进入，**没有私聊入口** ——
     * 自助申请（群员或管理员给自己绑）的人，其成员身份已由"能在本群发消息"这一事实确立。
     * 对他再查一次不带来任何新信息，却会引入"查询超时被误判为不在群 → 误拒"的风险。
     * 被代绑的 QQ 没发过任何消息，这才是唯一真正未知的量。
     *
     * ## 定位：防呆，不是安全边界
     *
     * 它拦的是"管理员打错 QQ 号，把白名单绑到不存在/无关的账号上"。
     * **不能**被当作"白名单里的 QQ 一定在群内"这一全局保证 —— 游戏内
     * `/plumbot bind add`（`plumbot.admin` 权限）与 `PlumBotAPI.addBinding` 两条写入路径
     * 在架构上无法查询 QQ 群成员，不经过本方法。
     *
     * 行为：
     * - 开关关闭 → 直接放行，**完全不调用** [IBot.checkUserInGroup]（零开销、零阻塞）；
     * - 适配器未声明 [BotCapability.GROUP_MEMBER_CHECK] → **放行 + 一次性 WARN**
     *   （无法校验时若一律拒绝，会让该开关在缺少此能力的适配器上使代绑功能整体不可用）；
     * - 其余按 [IBot.checkUserInGroup] 判定，不在群则发拒绝文案并返回 false。
     *
     * 注意：`checkUserInGroup` **无法区分"确认不在群"与"查询超时"**，两者都返回 `false`。
     * 因此拒绝文案对两种情况都成立（见 `messages.yml` 的 `notInGroup`）。
     *
     * @param targetUserId 被代绑的 QQ
     * @return true 表示允许继续创建绑定
     */
    fun ensureTargetInGroup(groupId: Long, targetUserId: Long): Boolean {
        if (!config.getBoolean("feature", "bind", "require_in_group")) return true

        if (!bot.metadata.capabilities.contains(BotCapability.GROUP_MEMBER_CHECK)) {
            if (!warnedNoMemberCheck) {
                warnedNoMemberCheck = true
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Bind] 已开启 require_in_group，但适配器 ${bot.metadata.type} " +
                        "未声明 GROUP_MEMBER_CHECK 能力，无法校验成员身份 —— 本次及后续绑定将**直接放行**。"
                )
            }
            return true
        }

        if (bot.checkUserInGroup(targetUserId, groupId)) return true

        sendRawMessage(groupId, render(Messages.notInGroup, "%user_id%" to targetUserId.toString()))
        return false
    }

    fun render(template: String, vararg replacements: Pair<String, String>): String =
        replacements.fold(template) { result, (placeholder, value) -> result.replace(placeholder, value) }

    /**
     * 以**指定路由**发送结构化载荷；门户不可用或渲染失败时自动降级为 [fallbackText]。
     * 业务层只决定"哪个 route + 什么 payload + 纯文本兜底长什么样"。
     */
    fun sendRendered(
        groupId: Long,
        route: RenderRoute,
        payload: Any,
        fallbackText: String,
        pic: Boolean
    ) = sendRendered(groupId, route, effectiveFallback(payload, fallbackText), pic) { payload }

    /**
     * 以 [RenderCard] 发送；纯文本降级**只由卡片正文投影**，调用方不再单独拼 fallbackText。
     */
    fun sendCard(groupId: Long, route: RenderRoute, card: RenderCard, pic: Boolean = true) {
        sendRendered(groupId, route, card, RenderCards.plain(card), pic)
    }

    /**
     * 在渲染协程内补齐 [RenderCard.data] 的变体。[text] 是正文唯一真源。
     *
     * 降级文本必须在这里算好（**含 [title]**）：载荷在协程内才构建，降级路径拿不到它；
     * 只投影 [text] 会让「在线玩家」这类标题只出现在图片里，与 [sendCard] 及事件派发路径口径不一致。
     */
    fun sendCard(
        groupId: Long,
        route: RenderRoute,
        text: String,
        title: String? = null,
        pic: Boolean = true,
        data: suspend (RenderPortal) -> Map<String, Any?> = { emptyMap() }
    ) {
        val fallback = RenderCards.plain(RenderCard(text = text, title = title))
        sendRendered(groupId, route, fallback, pic) { portal ->
            RenderCard(text = text, title = title, data = data(portal))
        }
    }

    /**
     * [RenderCard] 的降级文本必须与**要发的卡片内容一致**；旧载荷保留调用方给出的 fallback。
     *
     * 走 [RenderCard.displayText]（标题 + 正文）而不是只投影 [RenderCard.text]：标题承载着
     * 正文里没有的事实，只取正文会让纯文本丢信息，而图片侧却带着它。
     */
    private fun effectiveFallback(payload: Any, fallbackText: String): String =
        if (payload is RenderCard) RenderCards.plain(payload) else fallbackText

    /**
     * 变体：载荷在**渲染协程内**构建，供异步准备（如预取头像写磁盘缓存）的场景使用 ——
     * 准备开销既不占用调用线程，也不落在渲染超时窗口内。
     */
    fun sendRendered(
        groupId: Long,
        route: RenderRoute,
        fallbackText: String,
        pic: Boolean,
        payload: suspend (RenderPortal) -> Any
    ) {
        val portal = openRenderPortal(groupId, route, fallbackText, pic) ?: return
        renderScope.launch {
            deliverRendered(groupId, route, renderOnce(portal, route, groupId, payload), fallbackText)
        }
    }

    /**
     * 出图前置门控：能力声明 → 门户 → 路由开关 → 引擎可用性。
     *
     * 返回 null 表示"已降级为纯文本并投递"（或 [pic] 为 false）。门控先于一切异步准备：
     * 路由被关掉时连 payload（可能含皮肤抓取/图层合成）都不该求值。
     */
    private fun openRenderPortal(
        groupId: Long,
        route: RenderRoute,
        fallbackText: String,
        pic: Boolean
    ): RenderPortal? {
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)

        val portal = if (pic) portalSupplier() else null
        if (portal == null) {
            // 与事件派发路径对齐：没有门户 = 配置/平台关闭，计入 RENDER_DISABLED，
            // 否则命令回复长期降级为文本而状态卡上一切正常。
            HealthCounters.increment(HealthCounters.Key.RENDER_DISABLED)
            bot.sendMsg(true, groupId, fallbackText, false)
            return null
        }
        // 计数放在这里而不是门户内部：门户对 Unavailable 不计数，否则统计里的"配置关闭"恒为 0。
        if (!portal.isImageEnabled(route.route)) {
            HealthCounters.increment(HealthCounters.Key.RENDER_DISABLED)
            noticeImageDisabled(route)
            bot.sendMsg(true, groupId, fallbackText, false)
            return null
        }
        // 命令回复是纯命令操作服务器时唯一的出图入口，必须预热：否则首次出图会因
        // "Playwright 尚未装载"降级为文本。warmUpEngines() 只调度异步装载，不阻塞调用线程。
        if (!portal.warmUpEngines()) {
            HealthCounters.increment(HealthCounters.Key.RENDER_FALLBACK)
            // 引擎确认"完全没有出图能力"时不能静默降级，否则"渲染开关失灵"无从排查。
            noticeRenderUnavailable()
            bot.sendMsg(true, groupId, fallbackText, false)
            return null
        }
        return portal
    }

    /** 出图一次：把渲染异常收敛为 [RenderResult.Failed]，交给 [deliverRendered] 统一降级。 */
    private suspend fun renderOnce(
        portal: RenderPortal,
        route: RenderRoute,
        groupId: Long,
        payload: suspend (RenderPortal) -> Any
    ): RenderResult = try {
        portal.render(
            RenderRequest(
                route = route.route,
                payload = payload(portal),
                targetKey = "group:$groupId"
            )
        )
    } catch (t: Throwable) {
        RenderResult.Failed(t.message ?: "exception")
    }

    /** 投递渲染结果：无图 → DEBUG + 纯文本；有图但发送失败 → WARN + 纯文本。 */
    private fun deliverRendered(
        groupId: Long,
        route: RenderRoute,
        result: RenderResult,
        fallbackText: String
    ) {
        val png = (result as? RenderResult.Image)?.png
        if (png == null) {
            // 分类由门户负责（RenderHealth）；这里只留一条 DEBUG 线索便于按路由排查
            val reason = (result as? RenderResult.Failed)?.reason
            context.log(
                LogLevel.DEBUG,
                "[PlumBot-Render] 渲染未产出图片（${route.route}）${reason?.let { ": " + it } ?: ""}，降级为纯文本"
            )
            bot.sendMsg(true, groupId, fallbackText, false)
            return
        }
        // sendGroupImage 返回 Boolean（true=已交给协议层）：只判"没抛异常"会把适配器的 false
        // 当成发送成功，用户看到的是"消息没了、也没有降级文本"。
        val sent = runCatching { bot.sendGroupImage(groupId, png) }.getOrDefault(false)
        if (!sent) {
            HealthCounters.increment(HealthCounters.Key.RENDER_SEND_FAILED)
            context.log(LogLevel.WARN, "[PlumBot-Render] 图片发送失败（${route.route}），降级为纯文本")
            bot.sendMsg(true, groupId, fallbackText, false)
        }
    }

    /** 列表型绑定结果统一走 `bindList` 路由（`whitelist` 路由只承接单人成功绑定）。 */
    fun sendBindList(
        groupId: Long,
        title: String,
        players: List<String>,
        userId: String,
        limit: Int,
        fallbackText: String
    ) {
        val card = RenderCards.bindList(
            text = fallbackText,
            title = title,
            userId = userId,
            players = players,
            limit = limit
        )
        sendCard(groupId, RenderRoute.BIND_LIST, card)
    }

    /** 单人绑定成功卡片（`whitelist` 路由）：含头像与绑定时间；其余列表型回复走 [sendBindList]。 */
    fun sendWhitelistCard(
        groupId: Long,
        playerName: String,
        qq: String,
        bindTime: String?,
        fallbackText: String,
        title: String = "白名单绑定成功",
        originId: String? = null,
        originName: String? = null,
        bindCount: Int = 0,
        current: List<String> = emptyList()
    ) {
        sendRendered(
            groupId = groupId,
            route = RenderRoute.WHITELIST,
            fallbackText = RenderCards.plain(fallbackText),
            pic = true
        ) { portal ->
            val avatarUrl = runCatching {
                portal.fetchAvatarAsDataUrl("qq:$qq", "https://q1.qlogo.cn/g?b=qq&nk=$qq&s=640")
            }.getOrNull()
            RenderCards.whitelist(
                text = fallbackText,
                title = title,
                playerName = playerName,
                qq = qq,
                bindTime = bindTime,
                avatarUrl = avatarUrl,
                originId = originId,
                originName = originName,
                bindCount = bindCount,
                current = current
            )
        }
    }

    /**
     * 平台原生头像卡片（[RenderRoute.AVATAR] 路由）。
     *
     * 解析顺序：平台原生外观图层（服务端合成）→ 绑定 QQ 头像 → 前端占位图；非 Hytale 平台
     * 退化为 QQ 头像卡片，因此该指令三端均可用。
     *
     * ## 旧模板兼容：先探测，再回落
     *
     * 已发版的 PlumBot-Web 模板 manifest 里没有 `avatar`，会明确拒绝。两道防线：
     * 1. **先探测**（[RenderPortal.supportsRoute]）：明确没声明 `avatar` 时直接走旧
     *    [RenderRoute.HYTALE_AVATAR]，不发起 `avatar` 渲染。这一步必需：默认
     *    `fallback_to_awt=true` 时 `avatar` 失败会被 AWT 用纯文字图接住（AWT 忽略 viewRoute），
     *    调用方拿到 Image 而非 Failed，回落逻辑不会触发，用户只看到一张没头像的文字图。
     * 2. **失败后重试一次**（不循环）：探测结果未知时，若判词确为"路由未实现"，改旧路由重试。
     *
     * 只对"路由未实现"重试：manifest 缺失、协议号不符同样报 PW_TEMPLATE_INVALID，
     * 换旧路由也一样画不出来，白白多跑一次渲染。
     */
    fun sendAvatarCard(groupId: Long, playerName: String, fallbackText: String) {
        val plain = RenderCards.plain(fallbackText)
        val portal = openRenderPortal(groupId, RenderRoute.AVATAR, plain, avatarPicEnabled()) ?: return
        renderScope.launch {
            var route = if (portal.supportsRoute(RenderRoute.AVATAR) == false) {
                noticeAvatarRouteFallback()
                RenderRoute.HYTALE_AVATAR
            } else {
                RenderRoute.AVATAR
            }
            val primary = renderOnce(portal, route, groupId) {
                avatarCard(portal, playerName, plain, route)
            }
            val result = if (
                route == RenderRoute.AVATAR &&
                primary is RenderResult.Failed &&
                avatarRouteMissing(primary.reason)
            ) {
                noticeAvatarRouteFallback()
                // 复用 RENDER_FALLBACK（出图链路被迫降级，但降级后仍成功出图），
                // 配合 noticeAvatarRouteFallback 的日志可把"旧模板"与"真故障"区分开。
                HealthCounters.increment(HealthCounters.Key.RENDER_FALLBACK)
                route = RenderRoute.HYTALE_AVATAR
                renderOnce(portal, route, groupId) {
                    avatarCard(portal, playerName, plain, route)
                }
            } else {
                primary
            }
            deliverRendered(groupId, route, result, plain)
        }
    }

    /**
     * 头像卡载荷。[route] 决定是否下发 `data.platform`：旧 [RenderRoute.HYTALE_AVATAR] 模板
     * 协议里没有该字段，多写一个键会触发"载荷多出协议未声明"告警，所以只有新路由才带。
     */
    private suspend fun avatarCard(
        portal: RenderPortal,
        playerName: String,
        fallbackText: String,
        route: RenderRoute
    ): RenderCard {
        val qq = DatabaseProvider.getBindByName(playerName)

        // ①–③ 皮肤漏斗（皮肤插件 → GameProfile → 正版源），服务端裁头 + 缓存
        val skin = runCatching { portal.resolveSkinAvatar(playerName) }.getOrNull()

        // ④ 平台原生外观图层（Hytale 部件化皮肤）→ 服务端合成
        val layers = runCatching {
            context.playerService.resolveAvatarLayers(playerName)
        }.getOrDefault(emptyList())

        val composed = if (skin != null || layers.isEmpty()) {
            null
        } else {
            runCatching {
                portal.composeAvatar(
                    AvatarComposeRequest(
                        cacheKey = "platform:$playerName",
                        layers = layers.map { layer ->
                            AvatarLayer(
                                png = layer.png,
                                blend = if (layer.blend.equals("multiply", ignoreCase = true)) {
                                    AvatarBlend.MULTIPLY
                                } else {
                                    AvatarBlend.NORMAL
                                },
                                color = layer.color
                            )
                        }
                    )
                )
            }.getOrNull()
        }

        // ⑤ 绑定 QQ 头像
        val qqAvatar = if (skin != null || composed != null) {
            null
        } else {
            qq?.let { q ->
                runCatching {
                    portal.fetchAvatarAsDataUrl("qq:$q", "https://q1.qlogo.cn/g?b=qq&nk=$q&s=640")
                }.getOrNull()
            }
        }

        val avatarUrl = skin ?: composed ?: qqAvatar
        val source = when {
            skin != null -> "skin"
            composed != null -> "platform"
            qqAvatar != null -> "qq"
            else -> "none"
        }
        return RenderCards.hytaleAvatar(
            text = fallbackText,
            playerName = playerName,
            avatarUrl = avatarUrl,
            fallbackQq = qq,
            source = source,
            platform = if (route == RenderRoute.AVATAR) platformBrand() else null
        )
    }

    fun sendBindMessage(groupId: Long, message: String) {
        val pic = true
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, message, pic)
    }

    fun sendBindTemplate(groupId: Long, template: String, vararg replacements: Pair<String, String>) {
        sendBindMessage(groupId, render(template, *replacements))
    }

    fun sendListTemplate(groupId: Long, template: String, vararg replacements: Pair<String, String>) {
        val pic = true
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, render(template, *replacements), pic)
    }

    fun sendWrongUsage(groupId: Long) {
        sendBindMessage(groupId, Messages.wrongUsage)
    }

    fun sendRawMessage(groupId: Long, message: String, pic: Boolean = false) {
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, message, pic)
    }

    fun groupMemberNameCard(groupId: Long, userId: Long): Pair<String, String> {
        bot.requireCapability(BotCapability.GROUP_MEMBER_QUERY)
        return bot.getGroupUserName(groupId, userId) to bot.getGroupUserCard(groupId, userId)
    }

    fun userReplacements(groupId: Long, userId: Long): Array<Pair<String, String>> {
        val (name, card) = groupMemberNameCard(groupId, userId)
        return arrayOf(
            "%user_id%" to userId.toString(),
            "%user_name%" to name,
            "%user_nick%" to card
        )
    }

    fun originReplacements(groupId: Long, userId: Long): Array<Pair<String, String>> {
        val (_, card) = groupMemberNameCard(groupId, userId)
        return arrayOf(
            "%origin_id%" to userId.toString(),
            "%origin_name%" to card
        )
    }

    fun checkPlayerExists(player: String): Boolean = DatabaseProvider.getBindByName(player) != null

    /** 绑定额度上限（`feature.bind.maxNum`）；供事务化写入在同一事务内使用。 */
    fun bindingLimit(): Int = config.getInteger("feature", "bind", "maxNum")

    fun checkUserBindingFull(userId: String): Boolean =
        // 走数据库端 COUNT：把该 QQ 的全部绑定行取回再 .size 会让开销随规模线性增长。
        DatabaseProvider.getDatabase()?.countByUser(userId)
            ?.let { it >= config.getInteger("feature", "bind", "maxNum") } ?: false

    /**
     * 失败原因是否为"模板没有实现 avatar 路由"（而非 manifest 缺失/协议号不符）。
     * 判词来自驱动层 manifest 校验：`模板未实现 route '<route>'（已实现：…）`。
     */
    internal fun avatarRouteMissing(reason: String): Boolean =
        reason.contains(ROUTE_MISSING_MARKER) && reason.contains("'" + RenderRoute.AVATAR.route + "'")

    private companion object {
        /** 模板 manifest 未声明该路由时的判词前缀。 */
        const val ROUTE_MISSING_MARKER = "模板未实现 route"
    }
}
