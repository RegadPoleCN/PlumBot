// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import com.microsoft.playwright.Page
import java.util.Locale
import kotlinx.coroutines.delay
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.PlaywrightErrorCodes

/**
 * `#render-target` 的裁剪矩形（CSS 像素，相对页面左上角）与现场诊断。
 *
 * 为什么需要 `y` 带 `scrollY`：`page.screenshot(clip)` 会让 Playwright 以
 * `captureBeyondViewport` 方式截取，clip 坐标是**文档坐标**而非视口坐标。
 */
internal data class RenderTargetBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val fontsReady: Boolean,
    val diagnostics: String
) {
    fun sameBox(other: RenderTargetBox): Boolean =
        x == other.x && y == other.y && width == other.width && height == other.height

    fun diagnose(): String = diagnostics
}

/**
 * 渲染目标稳定化：把 `#render-target` 从"还在回流"等到"连续两帧几何一致 + 字体就绪"，
 * 再交出可交给 `page.screenshot(clip)` 的矩形。
 *
 * 不直接用 `Locator.screenshot`：元素截图会先 scrollIntoView，再等两帧几何完全一致，
 * READY 之后任何持续回流（异步字体、图片解码、滚动条出现）都会把这段等待拖到超时
 * （线上实测 8959ms 后报 "Timeout ... waiting for element to be stable"，整次渲染降级 AWT）。
 */
internal class TargetSettler(
    private val context: PlatformContext,
    private val failureMapper: FailureMapper
) {

    /**
     * 等 `#render-target` 布局稳定，并返回可交给 `page.screenshot(clip)` 的矩形。
     *
     * 稳定性判据：连续两次采样（间隔 [SETTLE_INTERVAL_MS]）几何完全一致，且文档字体
     * 已是 `loaded` 状态。总等待上限 [SETTLE_TIMEOUT_MS]，超时也返回当前几何 ——
     * 让随后的截图超时给出更准确的错误，而不是在这里盲等。
     */
    suspend fun settle(page: Page, viewRoute: String): RenderTargetBox {
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        var previous: RenderTargetBox? = null
        while (true) {
            val current = sample(page, viewRoute)
            if (current.width <= MIN_TARGET_SIZE || current.height <= MIN_TARGET_SIZE) {
                // 尺寸为 0：模板还没把内容挂上 DOM，继续等（0 尺寸不算"稳定"）
                previous = null
            } else if (current.fontsReady && previous != null && previous.sameBox(current)) {
                return current
            } else {
                previous = current
            }
            if (System.currentTimeMillis() >= deadline) {
                context.log(
                    LogLevel.DEBUG,
                    "[PlumBot-Render] #render-target 在 " + SETTLE_TIMEOUT_MS + "ms 内未判定为稳定，按当前几何截图：" + current.diagnose()
                )
                return current
            }
            delay(SETTLE_INTERVAL_MS)
        }
    }

    /** 采样一次 `#render-target` 的几何与页面状态（供 [settle] 与失败诊断）。 */
    private fun sample(page: Page, viewRoute: String): RenderTargetBox {
        val raw: Any? = page.evaluate(
            """
            () => {
                const el = document.querySelector('#render-target');
                if (!el) {
                    return { missing: true };
                }
                const r = el.getBoundingClientRect();
                const cs = getComputedStyle(el);
                return {
                    missing: false,
                    x: r.left + window.scrollX,
                    y: r.top + window.scrollY,
                    width: r.width,
                    height: r.height,
                    scrollY: window.scrollY,
                    viewport: window.innerWidth + 'x' + window.innerHeight,
                    docHeight: document.documentElement.scrollHeight,
                    fontsReady: !document.fonts || document.fonts.status === 'loaded',
                    animations: document.getAnimations ? document.getAnimations().length : 0,
                    display: cs.display,
                    visibility: cs.visibility,
                    opacity: cs.opacity,
                    tab: document.visibilityState
                };
            }
            """.trimIndent()
        )
        val map = raw as? Map<*, *> ?: return RenderTargetBox(
            0.0, 0.0, 0.0, 0.0, false,
            "无法读取 #render-target 几何（evaluate 返回 " + (raw?.javaClass?.simpleName ?: "null") + "）"
        )
        if (map["missing"] == true) {
            throw failureMapper.failure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                "settle",
                viewRoute,
                "模板没有渲染出 #render-target 元素（window.__PLUMBOT_RENDER_READY__ 已为 true，但 DOM 中不存在该 id）",
                "模板必须在数据就绪后渲染 id=render-target 的根节点；协议需为 1。",
                null
            )
        }
        fun num(key: String): Double = (map[key] as? Number)?.toDouble() ?: 0.0
        return RenderTargetBox(
            x = num("x"),
            y = num("y"),
            width = num("width"),
            height = num("height"),
            fontsReady = map["fontsReady"] == true,
            diagnostics = "box=" + fmtMetric(num("width")) + "x" + fmtMetric(num("height")) +
                "@" + fmtMetric(num("x")) + "," + fmtMetric(num("y")) +
                " scrollY=" + fmtMetric(num("scrollY")) +
                " viewport=" + map["viewport"] +
                " docHeight=" + fmtMetric(num("docHeight")) +
                " fonts=" + (if (map["fontsReady"] == true) "loaded" else "loading") +
                " animations=" + num("animations").toInt() +
                " display=" + map["display"] +
                " visibility=" + map["visibility"] +
                " opacity=" + map["opacity"] +
                " tab=" + map["tab"]
        )
    }

    private fun fmtMetric(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    private companion object {
        /**
         * `#render-target` 稳定性等待的上限与采样间隔。
         *
         * 取值理由：模板自身的 READY 信号通常已等过两帧（约 33ms），这里再补 1.5s 足够覆盖
         * 异步字体/图片回流；50ms 的采样间隔足以识别"还在动"。两者都不与整体渲染预算竞争
         * —— 超时后照常按当前几何截图，由截图超时给出真实错误。
         */
        const val SETTLE_TIMEOUT_MS = 1_500L
        const val SETTLE_INTERVAL_MS = 50L
        const val MIN_TARGET_SIZE = 1.0
    }
}
