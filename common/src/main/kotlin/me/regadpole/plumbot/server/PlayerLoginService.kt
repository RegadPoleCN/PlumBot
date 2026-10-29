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

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.bot.command.RenderCards
import me.regadpole.plumbot.bot.command.whitelist.BindFeature
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.database.BindingLookupService
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import java.util.concurrent.atomic.AtomicLong

/**
 * 登录白名单校验（fail-closed）。
 *
 * 三层门，自外向内：`feature.bind.enable=false` → 绑定功能整体关闭（与群内绑定指令、
 * 退群解绑共用**同一把门**）；`feature.bind.whitelist=false` → 运维主动放行，也是故障时的
 * 应急通道；两道门都开着才查绑定表，且**查不清就拒绝**——宁可把已绑定玩家挡在门外（有一行
 * WARN 与计数键可查，改开关即可放行），也不让未绑定玩家在数据库故障时混进服。
 *
 * 查询交给 `BindingLookupService`（缓存 + 单飞 + 有界等待），数据库卡住时这里最多阻塞
 * `feature.bind.login_lookup_timeout_ms`（默认 500ms），不会挂死平台登录线程。
 */
class PlayerLoginService(
    private val context: PlatformContext,
    private val sender: ServerMessageSender = ServerMessageSender(context)
) {

    private val config get() = context.config

    /** 最近一次「校验不可用」告警的时间戳；同一场故障只留一条 WARN，避免刷屏。 */
    private val lastUnavailableWarnAt = AtomicLong(Long.MIN_VALUE / 2)

    fun check(playerName: String): PreLoginResult {
        if (!BindFeature.enabled(config)) return PreLoginResult(allowed = true)
        if (!config.getBoolean("feature", "bind", "whitelist")) return PreLoginResult(allowed = true)

        val result = BindingLookupService.resolve(playerName, cacheTtlSeconds(), lookupTimeoutMs())
        return when (result.outcome) {
            BindingLookupService.Outcome.BOUND -> PreLoginResult(allowed = true)

            BindingLookupService.Outcome.NOT_BOUND -> {
                notifyKick(playerName)
                PreLoginResult(
                    allowed = false,
                    kickMessage = kickGuide(),
                    denial = PreLoginDenial.NOT_BOUND
                )
            }

            BindingLookupService.Outcome.UNAVAILABLE -> {
                reportUnavailable(playerName, result.reason)
                PreLoginResult(
                    allowed = false,
                    kickMessage = unavailableKick(),
                    denial = PreLoginDenial.LOOKUP_UNAVAILABLE
                )
            }
        }
    }

    /**
     * 读一个「可能缺失」的整数配置：`getLong` 缺失时回 0，而 0 秒 TTL / 0 毫秒超时都是非法值。
     * 判据与 `ConsoleEchoSetting` 一致：`rawTypeOf` 为 missing 才用默认值（不通过 `node.boolean`
     * 之类的属性读，免得把默认值写回配置树）。
     */
    private fun longOrDefault(default: Long, vararg path: String): Long =
        if (config.rawTypeOf(*path) == "missing") default else config.getLong(*path)

    private fun cacheTtlSeconds(): Long = longOrDefault(
        BindingLookupService.DEFAULT_TTL_SECONDS,
        "feature", "bind", "login_cache_ttl_seconds"
    ).coerceIn(MIN_CACHE_TTL_SECONDS, MAX_CACHE_TTL_SECONDS)

    private fun lookupTimeoutMs(): Long = longOrDefault(
        BindingLookupService.DEFAULT_TIMEOUT_MS,
        "feature", "bind", "login_lookup_timeout_ms"
    ).coerceIn(BindingLookupService.MIN_TIMEOUT_MS, BindingLookupService.MAX_TIMEOUT_MS)

    /**
     * fail-closed 的一次拒绝：计数 + 限频 WARN。
     *
     * 限频是必要的：数据库真挂时每一秒都可能有人登录，逐条 WARN 会把控制台淹掉，
     * 反而看不到第一条。
     */
    private fun reportUnavailable(playerName: String, reason: String?) {
        HealthCounters.increment(HealthCounters.Key.LOGIN_LOOKUP_UNAVAILABLE)
        val now = System.currentTimeMillis()
        val last = lastUnavailableWarnAt.get()
        if (now - last < UNAVAILABLE_WARN_INTERVAL_MS) return
        if (!lastUnavailableWarnAt.compareAndSet(last, now)) return
        val reasonText = reason ?: "未知原因"
        val window = UNAVAILABLE_WARN_INTERVAL_MS / 1000
        context.log(
            LogLevel.WARN,
            "[PlumBot-Whitelist] 白名单校验暂时不可用，拒绝 ${playerName} 进入：${reasonText}；" +
                "已计入 LOGIN_LOOKUP_UNAVAILABLE（同类告警每 ${window}s 只留一条）；" +
                "如需临时放行，把 feature.bind.whitelist 改成 false。"
        )
    }

    private fun notifyKick(playerName: String) {
        val reason = Messages.kickPlatform.replace("%player_name%", playerName)
        val guide = kickGuide()
        val text = if (guide.isBlank()) reason else reason + "\n" + guide
        sender.broadcastRendered(
            route = RenderRoute.KICK,
            card = RenderCards.kick(text, playerName, reason, guide),
            pic = true
        )
    }

    /** 被踢玩家看到的加群提示；图片卡与纯文本提示共用这一份。 */
    private fun kickGuide(): String = getLegacyFromComponent(
        getComponentFromMiniMsg(
            Messages.kickServer.replace("%groups%", config.getLongList("groups").toString())
        )
    )

    /** 校验通道故障时的提示：不提加群、不提绑定，免得玩家以为是自己没申请。 */
    private fun unavailableKick(): String =
        getLegacyFromComponent(getComponentFromMiniMsg(Messages.kickUnavailable))

    private companion object {
        /** 缓存 TTL 的合理区间（秒）：0 等于不开缓存，过大则改库后长时间不生效。 */
        const val MIN_CACHE_TTL_SECONDS = 1L
        const val MAX_CACHE_TTL_SECONDS = 3_600L

        /** 「校验不可用」告警的限频窗口。 */
        const val UNAVAILABLE_WARN_INTERVAL_MS = 60_000L
    }
}
