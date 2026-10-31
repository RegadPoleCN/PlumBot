// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.flow

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.engine.RenderOptions
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.pipeline.PayloadGovernor
import me.regadpole.plumbot.render.pipeline.RenderMetrics
import me.regadpole.plumbot.render.pipeline.engines.EngineRegistry
import me.regadpole.plumbot.render.pipeline.engines.PlaywrightLifecycle
import me.regadpole.plumbot.render.pipeline.routing.NoticeSink
import me.regadpole.plumbot.render.pipeline.routing.RoutePolicy
import me.regadpole.plumbot.render.support.RenderLimits
import me.regadpole.plumbot.render.support.RenderProtocolRules
import me.regadpole.plumbot.render.support.SafeHttp

/**
 * 渲染主流程：门控 → 协议自检 → 容量/串行 → 载荷治理 → 主引擎 → AWT 兜底 → 结果归类。
 *
 * 这里只有**顺序与降级语义**，具体策略（引擎解析、提示去重、容量、注册表）都在协作者里。
 */
internal class RenderPipeline(
    private val context: PlatformContext,
    private val policy: RoutePolicy,
    private val registry: EngineRegistry,
    private val lifecycle: PlaywrightLifecycle,
    private val notices: NoticeSink,
    private val capacity: CapacityGate
) {

    suspend fun render(request: RenderRequest): RenderResult {
        val route = RenderRoute.byName(request.route)
            ?: return RenderResult.Failed("未知路由: ${request.route}")

        if (!policy.picEnabled(route)) {
            notices.noticeGateClosed(route)
            return RenderResult.Unavailable
        }

        val engine = policy.engineFor(route)
        if (engine == RenderLimits.ENGINE_NONE) {
            notices.noticeEngineOff(route)
            return RenderResult.Unavailable
        }

        // 协议字段自检：模板与插件各自独立演进，字段漂移只会表现为「图片里某块空白」，
        // 因此在真正出图前做一次键集比对，把漂移变成可诊断的 WARN（同一差异只报一次）。
        RenderProtocolRules.diff(route.route, request.payload)?.let { drift ->
            RenderProtocolRules.report(route.route, drift) { message ->
                context.log(LogLevel.WARN, message)
            }
        }

        return capacity.dispatch(route, engine, request) { doRender(route, engine, request) }
    }

    private suspend fun doRender(
        route: RenderRoute,
        engine: String,
        request: RenderRequest
    ): RenderResult {
        val options = RenderOptions(timeoutMs = request.timeoutMs ?: policy.timeoutFor(route))
        // null = 卡片正文超过 card_text_max：截断会让图片与文字分叉，宁可不出图。
        val payload = governPayload(request.payload)
            ?: return unavailable(route, "卡片正文超过 card_text_max，已放弃出图")

        // 配了 playwright 但运行库还没装上时在这里补一次异步装载。
        // 没有这一步，命令回复等"从不调用 warmUpEngines()"的路径永远拿不到 Playwright。
        if (engine == RenderLimits.ENGINE_PLAYWRIGHT && !lifecycle.isReady() && !lifecycle.isPlaywrightPresent()) {
            lifecycle.schedulePlaywrightLoad()
        }

        val primary = registry.rendererFor(engine)?.takeIf { it.isSupported }
        var primaryFailure: String? = null

        if (primary != null) {
            val bytes = try {
                primary.render(route.route, payload, options)
            } catch (t: Throwable) {
                primaryFailure = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                null
            }
            if (bytes != null && isRenderableImage(bytes)) return RenderResult.Image(bytes)
            if (primaryFailure == null) {
                primaryFailure = if (bytes == null) "引擎未返回图片数据" else "引擎返回的数据不是有效 PNG"
            }
        } else if (engine != RenderLimits.ENGINE_AWT) {
            RenderMetrics.increment(RenderMetrics.ENGINE_UNAVAILABLE)
            primaryFailure = when {
                engine != RenderLimits.ENGINE_PLAYWRIGHT -> "引擎 $engine 不可用（未装载或初始化失败）"
                !lifecycle.isPlaywrightPresent() -> "Playwright 运行库尚未装载完成（正在后台装载）"
                else -> "Playwright 引擎未能初始化"
            }
        }

        // 主引擎即 AWT 时不再重复渲染一次
        if (engine == RenderLimits.ENGINE_AWT) {
            return RenderResult.Failed(
                "AWT 引擎未能产出有效图片（路由 ${route.route}）：${primaryFailure ?: "未知原因"}"
            )
        }

        // fallback_to_awt=false：主引擎失败即降级纯文本，不允许悄悄换成 AWT 图。
        if (!fallbackToAwt()) {
            notices.reportDegradation(
                route = route,
                engine = engine,
                reason = primaryFailure ?: "未知原因",
                fallbackSucceeded = false,
                loading = lifecycle.isLoading(),
                cooling = lifecycle.isCooling()
            )
            return RenderResult.Failed(
                "路由 ${route.route} 的 $engine 渲染失败：${primaryFailure ?: "未知原因"}；" +
                    "render.playwright.fallback_to_awt=false，本次降级为纯文本。"
            )
        }

        // 兜底同样要走"按需重建"的查找（reload 后引擎表被清空，直接查表会拿不到）
        val fallback = registry.rendererFor(RenderLimits.ENGINE_AWT)?.takeIf { it.isSupported }
        if (fallback == null) {
            notices.reportDegradation(
                route = route,
                engine = engine,
                reason = primaryFailure ?: "未知原因",
                fallbackSucceeded = false,
                loading = lifecycle.isLoading(),
                cooling = lifecycle.isCooling()
            )
            return unavailable(route, "AWT 兜底引擎不可用，本次没有出图")
        }
        val fallbackBytes = try {
            fallback.render(route.route, payload, options)
        } catch (t: Throwable) {
            context.log(LogLevel.WARN, "[PlumBot-Render] AWT 兜底失败: ${t.message}")
            null
        }
        return if (fallbackBytes != null && isRenderableImage(fallbackBytes)) {
            notices.reportDegradation(
                route = route,
                engine = engine,
                reason = primaryFailure ?: "未知原因",
                fallbackSucceeded = true,
                loading = lifecycle.isLoading(),
                cooling = lifecycle.isCooling()
            )
            RenderResult.Image(fallbackBytes)
        } else {
            notices.reportDegradation(
                route = route,
                engine = engine,
                reason = primaryFailure ?: "未知原因",
                fallbackSucceeded = false,
                loading = lifecycle.isLoading(),
                cooling = lifecycle.isCooling()
            )
            RenderResult.Failed("AWT 兜底未能产出有效图片（路由 ${route.route}）")
        }
    }

    /**
     * 记一次"被前置条件挡住、未真正尝试渲染"（载荷超限、兜底引擎缺失）。
     *
     * 口径见 RenderHealth：这类失败计入 failed 并留下原因，但不计入 degraded
     * （主引擎根本没被调用）。
     */
    private fun unavailable(route: RenderRoute, reason: String): RenderResult {
        RenderMetrics.increment(RenderMetrics.DEGRADED_FAILED)
        RenderMetrics.noteFailure(route.route, reason)
        return RenderResult.Unavailable
    }

    /**
     * 是否允许主引擎失败后改用 AWT 兜底。
     *
     * 缺省必须是 true（兼容旧配置与测试替身）；显式 false 时宁可降级纯文本，
     * 也不让服主以为 Playwright 正在工作、实际看到的却是 AWT 图。
     */
    private fun fallbackToAwt(): Boolean {
        val raw = runCatching {
            context.config.getString("render", "playwright", "fallback_to_awt")
        }.getOrNull() ?: return true
        return raw.toBooleanStrictOrNull() ?: true
    }

    /**
     * 三层长度治理，取代旧实现的「500 字符静默截断」。
     *
     * `data:` URL（服务端预取并合成的头像图层）**不参与文本截断**，否则会被截成坏图。
     *
     * @return 治理后的载荷；卡片正文超过 `render.limits.card_text_max` 时返回 `null`，
     *   表示"放弃出图、直接发纯文本"（调用方仍有完整正文，用户不会看到半截内容）。
     */
    private fun governPayload(payload: Any): Any? {
        val config = context.config
        val governor = PayloadGovernor(
            displayTextMax = RenderLimits.limit(config, "display_text_max", RenderLimits.DEFAULT_DISPLAY_TEXT_MAX),
            perLayerMaxBytes = RenderLimits.limit(config, "per_layer_max", RenderLimits.DEFAULT_PER_LAYER_MAX),
            binaryTotalMaxBytes = RenderLimits.limit(config, "binary_total_max", RenderLimits.DEFAULT_BINARY_TOTAL_MAX),
            layersMax = RenderLimits.limit(config, "layers_max", RenderLimits.DEFAULT_LAYERS_MAX),
            textTotalWarn = RenderLimits.limit(config, "total_text_warn", RenderLimits.DEFAULT_TOTAL_TEXT_WARN),
            cardTextMax = RenderLimits.limit(config, "card_text_max", RenderLimits.DEFAULT_CARD_TEXT_MAX),
            warn = { message -> context.log(LogLevel.WARN, message) }
        )
        val governed = governor.govern(payload)
        governor.report()
        if (governor.cardTextOverflow) return null
        return governed ?: payload
    }

    /**
     * 按**实际内容**判定能否出图（不看 HTTP/MIME 声明）。
     *
     * 浏览器截图失败时可能拿到 `null`、0 字节或以 `<html>` 开头的错误页；
     * 把这类结果当图片发给平台，客户端只会显示破图占位。
     */
    private fun isRenderableImage(bytes: ByteArray): Boolean {
        val mime = SafeHttp.mimeOf(bytes)
        return mime == "image/png" || mime == "image/jpeg"
    }
}
