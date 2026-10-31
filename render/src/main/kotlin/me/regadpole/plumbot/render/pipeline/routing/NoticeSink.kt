// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.routing

import java.util.concurrent.ConcurrentHashMap
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.pipeline.RenderMetrics
import me.regadpole.plumbot.render.support.RenderLimits

/**
 * 面向服主的**提示去重表**：同一件事只在首次发生时提示一次，避免每条消息刷屏。
 *
 * 五张表按"提示语义"分开，键也各不相同（引擎名 / 路由 / 引擎+路由），
 * 混用一张表会让"同一个错拼只告警一次"与"每条路由各提示一次"互相吞掉。
 *
 * 这里只做**提示**，不改变任何渲染决策；降级计数仍然逐次累加进 [RenderMetrics]。
 */
internal class NoticeSink(private val context: PlatformContext) {

    /** 已提示过的未知引擎取值（去重键是引擎名，不是 route）。 */
    private val engineNotices = ConcurrentHashMap.newKeySet<String>()

    /** 降级提示的去重时间表：键为 "引擎:路由"，值为上次 ERROR 的时间戳。 */
    private val degradationNotices = ConcurrentHashMap<String, Long>()

    /** 已提示过的"全局引擎覆盖路由首选"（键为 "路由:全局取值"）。 */
    private val engineShadowNotices = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过的"路由出图被配置关闭"（键为 route）。 */
    private val gateClosedNotices = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过的"路由引擎为 none"（键为 route）。 */
    private val engineOffNotices = ConcurrentHashMap.newKeySet<String>()

    /**
     * 未知引擎值 → **一次性 WARN** 并说明已回落 AWT。
     *
     * 没有取值白名单时，任何拼写错误（`playwrite` / `palywright`）都会**静默**落到 AWT：
     * 服主以为开了高清渲染，实际一直是本地轻量图，且没有任何线索可查。
     * 去重键是**引擎取值**而非 route，同一个错拼只在首次出现时提示一次。
     */
    fun warnUnknownEngine(route: RenderRoute, engine: String) {
        if (!engineNotices.add(engine)) return
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 未知渲染引擎 '$engine'（首次出现在路由 ${route.route}）—— " +
                "已回落 ${RenderLimits.ENGINE_AWT}。可选值：${RenderLimits.ENGINE_NONE} / " +
                "${RenderLimits.ENGINE_AWT} / ${RenderLimits.ENGINE_PLAYWRIGHT} / ${RenderLimits.ENGINE_AUTO}"
        )
    }

    /**
     * 全局引擎覆盖了路由首选引擎时，**一次性 INFO** 说清原因。
     *
     * 模板里 `engine: "awt"` 是显式值，升级后不会自动变成 auto，头像卡会一直是无头像
     * 的 AWT 图；没有这条提示就只能靠读文档猜。只在"全局显式"且与首选不一致时提示，
     * 逐路由覆盖视为用户有意为之。
     */
    fun noticeGlobalEngineOverride(route: RenderRoute, specific: String?, global: String?, engine: String) {
        val preferred = route.preferredEngine ?: return
        // 逐路由显式配置（含显式 auto）都视为用户有意为之，不再提示。
        if (specific != null) return
        if (global == null || global.equals(RenderLimits.ENGINE_AUTO, ignoreCase = true)) return
        if (engine.equals(preferred, ignoreCase = true)) return
        if (!engineShadowNotices.add("${route.route}:$global")) return
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] render.engine=$global 覆盖了路由 ${route.route} 的首选引擎 $preferred —— " +
                "把 render.engine 设为 auto 可按路由自动选择，或用 render.views.${route.route}.engine 单独指定"
        )
    }

    /**
     * 门控关闭时**一次性 INFO**：这是"本该出图却只发文字"最主要的静默原因。
     * 门户直接返回 Unavailable 且容量闸不为 Unavailable 计数，若无提示，
     * `/plumbot render stats` 的"配置关闭"恒为 0，服主没有任何线索。
     */
    fun noticeGateClosed(route: RenderRoute) {
        if (!gateClosedNotices.add(route.route)) return
        val gate = route.featureGate
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 路由 ${route.route} 的出图已关闭" +
                (if (gate != null) "（render.outputs.$gate=false）" else "") +
                " —— 该功能将始终发送纯文本"
        )
    }

    /** `engine=none` 是主动关闭出图，与门控关闭分开提示，避免排查时混为一谈。 */
    fun noticeEngineOff(route: RenderRoute) {
        if (!engineOffNotices.add(route.route)) return
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 路由 ${route.route} 的引擎为 ${RenderLimits.ENGINE_NONE}（render.engine 或 " +
                "render.views.${route.route}.engine）—— 该功能将始终发送纯文本"
        )
    }

    /**
     * 记录一次"主引擎失败 → AWT 降级"。
     *
     * 为什么是 ERROR 而不是 WARN：降级意味着服主看到的图与所配引擎不符，
     * 属于必须可发现的配置/依赖问题。同一引擎+路由在 [RenderLimits.DEGRADATION_LOG_INTERVAL_MS]
     * 内只打一次，避免每条消息刷屏；计数仍逐次累加。
     *
     * @param loading Playwright 运行库正在后台装载
     * @param cooling Playwright 运行库上次装载失败、正在冷却
     */
    fun reportDegradation(
        route: RenderRoute,
        engine: String,
        reason: String,
        fallbackSucceeded: Boolean,
        loading: Boolean,
        cooling: Boolean
    ) {
        RenderMetrics.increment(RenderMetrics.FALLBACK)
        if (fallbackSucceeded) RenderMetrics.increment(RenderMetrics.FALLBACK_AWT)
        RenderMetrics.noteFailure(route.route, "$engine 渲染失败：$reason")
        val key = "$engine:${route.route}"
        val now = System.currentTimeMillis()
        val last = degradationNotices[key] ?: 0L
        if (now - last < RenderLimits.DEGRADATION_LOG_INTERVAL_MS) return
        degradationNotices[key] = now
        val outcome = if (fallbackSucceeded) "已用 AWT 兜底出图" else "AWT 兜底也未成功，本次降级为纯文本"
        // 装载进行中/冷却中不是"配置或依赖出错"，用 INFO 说明现状即可；
        // 真正的故障（引擎在但初始化失败）仍保持 ERROR 以便被发现。
        val level = if (loading || cooling) LogLevel.INFO else LogLevel.ERROR
        val hint = when {
            loading -> "Playwright 运行库正在后台装载，装载完成后自动改用高清引擎（无需再次操作）。"
            cooling -> "Playwright 运行库上次装载失败，正处于冷却中；执行 /plumbot doctor render 查看原因。"
            else -> "执行 /plumbot doctor render 查看依赖与环境诊断。"
        }
        context.log(
            level,
            "[PlumBot-Render] 路由 ${route.route} 的 $engine 渲染失败：$reason；$outcome。$hint"
        )
    }
}
