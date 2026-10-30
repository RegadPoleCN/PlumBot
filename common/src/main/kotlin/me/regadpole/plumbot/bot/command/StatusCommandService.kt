// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.bot.BotState
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.render.RenderHealth
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.internal.HealthCounters.Key as HealthKey
import me.regadpole.plumbot.internal.HealthSummary
import me.regadpole.plumbot.internal.ServerMetricsText

/**
 * 服务器状态指令：读取 TPS / MSPT / 内存 / 在线人数，拼成状态卡交给渲染门户。
 *
 * 指标读不到时显式显示占位符，不用「完美 TPS」伪装正常。
 */
class StatusCommandService(private val service: BotCommandService) {

    fun showStatus(message: String, groupId: Long, userId: Long) {
        if (!service.context.hasCapability(PlatformCapability.SERVER_TPS_METRICS)) {
            service.sendRawMessage(groupId, Messages.statusUnavailable)
            return
        }

        val tpsArray = service.context.getRecentTps()
        val mspt = service.context.getMspt()

        // 指标读不到时**明说读不到**：旧实现把缺失的 TPS 显示成字面量 "20.0"（"完美 TPS"），
        // 会让服主看到一张"一切正常"的卡片，而指标根本没读到。
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
        val serverName = service.context.config.getString("server", "name")
            ?.ifBlank { "Minecraft Server" } ?: "Minecraft Server"

        // 状态卡与纯文本降级共用同一份正文与 data：健康行不再只存在于文字路径。
        val card = RenderCards.status(
            serverName = serverName,
            tps = listOf(tps1m, tps5m, tps15m),
            mspt = msptStr,
            usedMem = usedMem,
            maxMem = maxMem,
            online = onlineCount,
            health = healthLine()
        )
        service.sendCard(groupId, RenderRoute.STATUS, card)
    }

    /**
     * 运行时健康摘要（渲染分类 / 配置关闭 / 引擎预热 / 发图 / 过滤 / Bot / 数据库）。
     *
     * 渲染结果**由门户自持**（[RenderHealth]），业务层只补门户看不到的三种情况：配置关闭、
     * 引擎预热失败、图片发送失败。数值全为 0 时仍显示一行——"全零"说明没有静默降级；排版见
     * [HealthSummary]（可单测）。
     */
    private fun healthLine(): String = HealthSummary.line(
        render = RenderProvider.getOrNull()?.renderHealth() ?: RenderHealth.EMPTY,
        disabled = HealthCounters.get(HealthKey.RENDER_DISABLED),
        warmUpFailures = HealthCounters.get(HealthKey.RENDER_FALLBACK),
        sendFailed = HealthCounters.get(HealthKey.RENDER_SEND_FAILED),
        filterHits = HealthCounters.get(HealthKey.FILTER_HITS),
        filterReloadFailures = HealthCounters.get(HealthKey.FILTER_RELOAD_FAILURES),
        botStarted = HealthCounters.get(HealthKey.BOT_STARTED),
        botUnloaded = HealthCounters.get(HealthKey.BOT_UNLOADED),
        // 连接状态要能体现"正在重连"——重连期间消息发不出去，布尔式的"已连接"看不出来
        botState = (BotProvider.getBot()?.state ?: BotState.INIT).toString(),
        reconnects = HealthCounters.get(HealthKey.BOT_RECONNECTS),
        dbInitFailures = HealthCounters.get(HealthKey.DB_INIT_FAILURES)
    )
}
