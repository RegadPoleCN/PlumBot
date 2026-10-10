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
import me.regadpole.plumbot.internal.ShutdownRegistry
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute

/**
 * 负责向所有配置互通的群广播消息的发送组件。
 *
 * 出图路径统一经**渲染门户**，并可按 route 指定结构化载荷
 * （例如 `message` / `death` / `advancement` 各自一张定制卡片），而不是一律落回通用文本卡片。
 */
class ServerMessageSender(private val context: PlatformContext) {

    /** 广播出图协程作用域（渲染是挂起能力，不能阻塞调用线程）。 */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // 兜底登记：本类由多个服务各自实例化，拥有者分散在平台模块里，
        // 历史上没有任何一处调用 shutdown()，作用域因此随每次 reload 泄漏。
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

    /**
     * 以指定 route 广播结构化载荷。
     *
     * 渲染**只做一次**，字节复用于所有互通群；某个群发送失败时只有该群降级为纯文本。
     */
    fun broadcastRendered(
        route: RenderRoute,
        payload: Any,
        fallbackText: String,
        pic: Boolean = true
    ) {
        val groups = context.config.getLongList("groups")
        if (groups.isEmpty()) return

        val portal = if (pic) RenderProvider.getOrNull() else null
        if (portal == null) {
            broadcastPlain(groups, fallbackText)
            return
        }

        scope.launch {
            val result = try {
                portal.render(
                    RenderRequest(route = route.route, payload = payload, targetKey = "broadcast")
                )
            } catch (t: Throwable) {
                // 门户契约是"永不抛异常"，此处再兜一层
                context.log(LogLevel.WARN, "[PlumBot-Render] 广播渲染异常（${route.route}）: ${t.message}")
                RenderResult.Failed(t.message ?: "exception")
            }

            val png = (result as? RenderResult.Image)?.png
            if (png == null) {
                broadcastPlain(groups, fallbackText)
                return@launch
            }

            val bot = BotProvider.getBot()
            if (bot == null) return@launch
            groups.forEach { groupId ->
                runCatching { bot.sendGroupImage(groupId, png) }
                    .onFailure {
                        context.log(
                            LogLevel.WARN,
                            "[PlumBot-Render] 群 $groupId 图片发送失败: ${it.message}，该群降级为纯文本"
                        )
                        bot.sendMsg(true, groupId, fallbackText, false)
                    }
            }
        }
    }

    private fun broadcastPlain(groups: List<Long>, fallbackText: String) {
        val bot = BotProvider.getBot() ?: return
        groups.forEach { groupId -> bot.sendMsg(true, groupId, fallbackText, false) }
    }
}
