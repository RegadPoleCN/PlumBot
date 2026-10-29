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

package me.regadpole.plumbot.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.internal.ShutdownRegistry
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.bot.command.RenderCards
import me.regadpole.plumbot.render.RenderCard
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import java.util.concurrent.ConcurrentHashMap

/**
 * 向所有配置互通的群广播消息。
 *
 * 出图统一经**渲染门户**，可按 route 指定结构化载荷（`message` / `death` / `advancement`
 * 各自一张卡片），而不是一律落回通用文本卡片。
 */
class ServerMessageSender(private val context: PlatformContext) {

    /** 广播出图协程作用域（渲染是挂起能力，不能阻塞调用线程）。 */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 已提示过"广播路由出图被关闭"（键为 route）。 */
    private val disabledNotices = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过"本次广播渲染未产出图片"（键为 route，避免每个群每次事件刷屏）。 */
    private val degradedNotices = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过"没有任何可用出图能力"（全局一次）。 */
    @Volatile
    private var warnedNoRenderCapability = false

    init {
        // 兜底登记：本类由多个服务各自实例化、拥有者分散在平台模块里，没有别处会调用 shutdown()。
        ShutdownRegistry.register(this) { shutdown() }
    }

    fun shutdown() {
        ShutdownRegistry.unregister(this)
        scope.cancel()
    }

    /** 纯文本广播（沿用旧语义）。 */
    fun broadcast(message: String, isPic: Boolean = false) {
        val bot = BotProvider.getBot() ?: return
        context.config.getLongList("groups").forEach { groupId ->
            bot.sendMsg(true, groupId, message, isPic)
        }
    }

    /** 以指定 route 广播结构化载荷：渲染**只做一次**、字节复用，某个群失败时只有该群降级为纯文本。 */
    fun broadcastRendered(
        route: RenderRoute,
        card: RenderCard,
        pic: Boolean = true
    ) {
        val groups = context.config.getLongList("groups")
        if (groups.isEmpty()) return

        // 降级文本只由卡片正文投影，不再由调用方另拼一份。
        val fallbackText = RenderCards.plain(card)
        val portal = if (pic) RenderProvider.getOrNull() else null
        if (portal == null) {
            HealthCounters.increment(HealthCounters.Key.RENDER_DISABLED)
            noticeNoRenderCapability()
            broadcastPlain(groups, fallbackText)
            return
        }

        // 与命令回复路径（BotCommandService.sendRendered）保持同一套门控序列：
        // ① 路由开关 / 引擎是否为 none ② 是否具备出图能力。缺了这两步，广播事件本该出图
        // 却静默发文字，stats 里也不记"配置关闭"与"能力缺失"。
        if (!portal.isImageEnabled(route.route)) {
            HealthCounters.increment(HealthCounters.Key.RENDER_DISABLED)
            noticeImageDisabled(route)
            broadcastPlain(groups, fallbackText)
            return
        }
        if (!portal.warmUpEngines()) {
            HealthCounters.increment(HealthCounters.Key.RENDER_FALLBACK)
            noticeNoRenderCapability()
            broadcastPlain(groups, fallbackText)
            return
        }

        scope.launch {
            val result = try {
                portal.render(
                    RenderRequest(route = route.route, payload = card, targetKey = "broadcast")
                )
            } catch (t: Throwable) {
                // 门户契约是"永不抛异常"，此处再兜一层
                context.log(LogLevel.WARN, "[PlumBot-Render] 广播渲染异常（${route.route}）: ${t.message}")
                RenderResult.Failed(t.message ?: "exception")
            }

            val png = (result as? RenderResult.Image)?.png
            if (png == null) {
                // 渲染失败也要计数与告警，否则与"配置关闭"在日志里不可区分。
                HealthCounters.increment(HealthCounters.Key.RENDER_FALLBACK)
                val reason = (result as? RenderResult.Failed)?.reason
                noticeDegraded(route, reason)
                broadcastPlain(groups, fallbackText)
                return@launch
            }

            val bot = BotProvider.getBot()
            if (bot == null) return@launch
            groups.forEach { groupId ->
                // sendGroupImage 返回 Boolean（true=已交给协议层）：只接异常会把适配器的 false
                // 当成发送成功，结果是这条群消息彻底消失，连降级文本都没有。
                val sent = runCatching { bot.sendGroupImage(groupId, png) }.getOrDefault(false)
                if (!sent) {
                    HealthCounters.increment(HealthCounters.Key.RENDER_SEND_FAILED)
                    context.log(
                        LogLevel.WARN,
                        "[PlumBot-Render] 群 $groupId 图片发送失败（${route.route}），该群降级为纯文本"
                    )
                    bot.sendMsg(true, groupId, fallbackText, false)
                }
            }
        }
    }

    /** 广播路由出图关闭时**一次性 INFO**（每 route 一次）。 */
    private fun noticeImageDisabled(route: RenderRoute) {
        if (!disabledNotices.add(route.route)) return
        val gate = route.featureGate
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 广播路由 ${route.route} 的图片输出已关闭" +
                (if (gate != null) "（render.outputs.$gate=false 或引擎为 none）" else "") +
                "，事件消息将发送纯文本"
        )
    }

    /** 渲染未产出图片时**一次性 WARN**（每 route 一次），带上门户给出的失败原因。 */
    private fun noticeDegraded(route: RenderRoute, reason: String?) {
        if (!degradedNotices.add(route.route)) return
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 广播路由 ${route.route} 未产出图片" +
                (reason?.let { "：$it" } ?: "") +
                "，本次改为发送纯文本"
        )
    }

    /** 平台没有任何出图能力时**一次性 WARN**（全局一次）。 */
    private fun noticeNoRenderCapability() {
        if (warnedNoRenderCapability) return
        warnedNoRenderCapability = true
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 当前平台没有可用的渲染引擎或未注册渲染门户，广播图片将全部降级为纯文本"
        )
    }

    private fun broadcastPlain(groups: List<Long>, fallbackText: String) {
        val bot = BotProvider.getBot() ?: return
        groups.forEach { groupId -> bot.sendMsg(true, groupId, fallbackText, false) }
    }
}
