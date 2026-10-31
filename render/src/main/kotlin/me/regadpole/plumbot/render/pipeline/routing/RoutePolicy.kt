// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.routing

import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.support.RenderLimits

/**
 * 路由解析策略：**只读配置、只返回判断结果**，不持有引擎、不做 I/O。
 *
 * 引擎解析顺序：`render.views.<route>.engine` → `render.engine` → 路由首选 → 内置默认 `awt`。
 * **显式配置优先，路由首选只是缺省值**：内置模板的 `render.engine` 恒存在，若按
 * `specific ?: global ?: fallback` 解析，路由首选（如头像卡的 playwright）永远走不到。
 * 因此 `auto`（或缺失 / 空串）视为"未显式配置"，此时才用 [RenderRoute.preferredEngine]。
 */
internal class RoutePolicy(
    private val context: PlatformContext,
    private val notices: NoticeSink
) {

    /**
     * 引擎解析：未显式配置时跟随路由首选，未知取值一次 WARN 后回落 AWT。
     *
     * 门控见 [picEnabled]，提示见 [NoticeSink.warnUnknownEngine] /
     * [NoticeSink.noticeGlobalEngineOverride]。
     */
    fun engineFor(route: RenderRoute): String {
        val specific = context.config
            .getString("render", "views", route.route, "engine")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val global = context.config
            .getString("render", "engine")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        // auto = "没选"，不是引擎名：逐路由 auto 表示"忽略全局、跟随该路由首选"。
        val specificExplicit = specific?.takeUnless { it.equals(RenderLimits.ENGINE_AUTO, ignoreCase = true) }
        val globalExplicit = global?.takeUnless { it.equals(RenderLimits.ENGINE_AUTO, ignoreCase = true) }
        val explicit = specificExplicit ?: if (specific != null) null else globalExplicit
        val engine = (explicit ?: route.preferredEngine ?: RenderLimits.ENGINE_AWT).lowercase()
        warnIfUnknownEngine(route, engine)
        notices.noticeGlobalEngineOverride(route, specific, global, engine)
        return engine
    }

    /**
     * 出图门控：`render.outputs.<功能>`，图片开关的唯一真源。
     *
     * 每个路由都有门控（`TEXT` 对应 `render.outputs.text`），`featureGate` 为 null 的只有
     * 测试 / 未来扩展；`AVATAR` 与 `HYTALE_AVATAR` 共用 `render.outputs.avatar`。
     */
    fun picEnabled(route: RenderRoute): Boolean {
        val gate = route.featureGate ?: return true
        return context.config.getBoolean("render", "outputs", gate)
    }

    /** 渲染超时：逐路由覆盖 → 全局 playwright 超时 → 内置默认值。 */
    fun timeoutFor(route: RenderRoute): Long {
        val specific = context.config.getInteger("render", "views", route.route, "timeout_ms")
        if (specific > 0) return specific.toLong()
        val global = context.config.getInteger("render", "playwright", "render_timeout_ms")
        return if (global > 0) global.toLong() else RenderLimits.DEFAULT_TIMEOUT_MS
    }

    /**
     * 只反映"配置是否启用"：门控关闭或 `engine=none` 视为**主动关闭**；
     * 队列溢出、引擎不可用等属于"启用后失败"，由渲染结果的 Unavailable/Failed 表达。
     */
    fun isImageEnabled(route: String): Boolean {
        val parsed = RenderRoute.byName(route) ?: return false
        return picEnabled(parsed) && engineFor(parsed) != RenderLimits.ENGINE_NONE
    }

    /**
     * 模板能力探测：交给**当前生效驱动**回答（见 [ImageRenderer.supportsRoute]）。
     *
     * 引擎不可用 / 配置为 none → 驱动为 null → `null`（未知），调用方按原顺序尝试；
     * AWT 不看路由 → 默认 `null`；只有 Playwright 会依据 manifest 的 `routes` 给出
     * 明确的 `true`/`false`。
     *
     * @param rendererFor 由门户注入的"按引擎名取驱动"查询，避免策略层持有引擎表。
     */
    fun supportsRoute(route: RenderRoute, rendererFor: (String) -> ImageRenderer?): Boolean? =
        rendererFor(engineFor(route))?.supportsRoute(route.route)

    /**
     * 已启用出图的路由里是否出现了 playwright 引擎（只读配置，不做任何 I/O）。
     *
     * 必须扫描**所有已启用出图**的路由，而不是只看 `STATUS`：逐路由覆盖同样需要预热。
     */
    fun playwrightConfiguredForGateways(): Boolean = RenderRoute.entries.any { route ->
        picEnabled(route) &&
            runCatching { engineFor(route) }.getOrDefault(RenderLimits.ENGINE_AWT) == RenderLimits.ENGINE_PLAYWRIGHT
    }

    private fun warnIfUnknownEngine(route: RenderRoute, engine: String) {
        if (engine in RenderLimits.KNOWN_ENGINES) return
        notices.warnUnknownEngine(route, engine)
    }
}
