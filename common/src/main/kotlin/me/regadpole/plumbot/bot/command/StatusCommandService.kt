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

import me.regadpole.plumbot.api.bot.BotState
import me.regadpole.plumbot.bot.BotProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import java.util.Locale
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.internal.HealthCounters.Key as HealthKey
import me.regadpole.plumbot.internal.ServerMetricsText

class StatusCommandService(private val service: BotCommandService) {

    /**
     * 渲染与出图专用的协程作用域。
     *
     * 取代旧的 `scheduler.runAsync { runBlocking { … } }`：渲染是 suspend 能力，
     * 用 `runBlocking` 会把平台调度线程整段占住（最长可达渲染超时）。
     */
    private val renderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun shutdown() {
        renderScope.cancel()
    }

    fun showStatus(message: String, groupId: Long, userId: Long) {
        if (!service.context.hasCapability(PlatformCapability.SERVER_TPS_METRICS)) {
            service.sendRawMessage(groupId, Messages.statusUnavailable)
            return
        }

        val tpsArray = service.context.getRecentTps()
        val mspt = service.context.getMspt()

        // R31：指标读不到时**明说读不到**（统一占位符）。
        // 旧实现把缺失的 TPS 显示成字面量 "20.0" —— 正是"完美 TPS"，会让代理端服主
        // 看到一张"一切正常"的卡片，而实际上这个指标根本没读到。
        val tps1m = ServerMetricsText.tps(tpsArray, 0)
        val tps5m = ServerMetricsText.tps(tpsArray, 1)
        val tps15m = ServerMetricsText.tps(tpsArray, 2)

        val msptStr = ServerMetricsText.mspt(mspt)

        val runtime = Runtime.getRuntime()
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem

        val onlineCount = service.context.playerService.listPlayers().size

        val portal = RenderProvider.getOrNull()
        val usePic = portal != null && portal.isImageCapable
        if (usePic && portal != null) {
            val payload = mapOf(
                "serverName" to (service.context.config.getString("server", "name")?.ifBlank { "Minecraft Server" } ?: "Minecraft Server"),
                "tps" to listOf(tps1m, tps5m, tps15m),
                "mspt" to msptStr,
                "usedMem" to usedMem,
                "maxMem" to maxMem,
                "online" to onlineCount
            )
            renderScope.launch {
                val result = try {
                    portal.render(
                        RenderRequest(
                            route = RenderRoute.STATUS.route,
                            payload = payload,
                            targetKey = "group:$groupId"
                        )
                    )
                } catch (t: Throwable) {
                    // 门户契约是"永不抛异常"，此处再兜一层，确保任何情况下都能出文本
                    service.context.log(LogLevel.WARN, "[PlumBot-Render] 状态图渲染异常: ${t.message}")
                    RenderResult.Failed(t.message ?: "exception")
                }
                val imageBytes = (result as? RenderResult.Image)?.png
                if (imageBytes != null) {
                    val sent = runCatching { service.bot.sendGroupImage(groupId, imageBytes) }
                        .onFailure { service.context.log(LogLevel.WARN, "[PlumBot-Render] 图片发送失败: ${it.message}") }
                        .isSuccess
                    if (sent) return@launch
                }
                // 降级回退发送纯文本（渲染失败/发送失败都会走到这里）
                val rendered = renderTextMessage(tps1m, tps5m, tps15m, msptStr, usedMem, maxMem, onlineCount)
                service.sendRawMessage(groupId, rendered, false)
            }
            return
        }

        val rendered = renderTextMessage(tps1m, tps5m, tps15m, msptStr, usedMem, maxMem, onlineCount)
        service.sendRawMessage(groupId, rendered, false)
    }

    private fun renderTextMessage(
        tps1m: String, tps5m: String, tps15m: String, msptStr: String,
        usedMem: Long, maxMem: Long, onlineCount: Int
    ): String = service.render(
        Messages.serverStatus,
        "%tps_1m%" to tps1m,
        "%tps_5m%" to tps5m,
        "%tps_15m%" to tps15m,
        "%mspt%" to msptStr,
        "%used_mem%" to usedMem.toString(),
        "%max_mem%" to maxMem.toString(),
        "%online%" to onlineCount.toString(),
        // 健康计数：这些"静默失败"只在日志里翻得到，直接摆到状态里
        "%health%" to healthLine()
    )

    /**
     * 运行时健康摘要（渲染降级/关闭 / 队列拒绝 / 过滤命中 / 词库重载失败 / Bot 启停 / DB 失败）。
     *
     * 数值全为 0 时仍显示一行 —— "全零"本身就是有效信息（说明没有静默降级）。
     */
    private fun healthLine(): String {
        // 渲染侧指标由门户自持（`:render` 不依赖 `:common`），这里一并汇总
        val queueRejected = RenderProvider.getOrNull()?.metrics()?.get("render.queueRejected") ?: 0L
        return "渲染降级 ${HealthCounters.get(HealthKey.RENDER_FALLBACK)}（配置关闭 ${HealthCounters.get(HealthKey.RENDER_DISABLED)}）· " +
            "队列拒绝 $queueRejected · " +
            "过滤命中 ${HealthCounters.get(HealthKey.FILTER_HITS)} · 词库失败 ${HealthCounters.get(HealthKey.FILTER_RELOAD_FAILURES)} · " +
            "Bot 启停 ${HealthCounters.get(HealthKey.BOT_STARTED)}/${HealthCounters.get(HealthKey.BOT_UNLOADED)} · " +
            // R02：把连接状态放进摘要 —— 布尔式的"已连接"看不出"正在重连"，而重连期间消息是发不出去的
            "状态 ${BotProvider.getBot()?.state ?: BotState.INIT} · " +
            "重连 ${HealthCounters.get(HealthKey.BOT_RECONNECTS)} 次 · " +
            "数据库失败 ${HealthCounters.get(HealthKey.DB_INIT_FAILURES)}"
    }

    private fun formatTps(tps: Double): String = ServerMetricsText.tps(tps)
}
