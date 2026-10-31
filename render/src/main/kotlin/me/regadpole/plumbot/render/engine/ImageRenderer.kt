// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine

/**
 * 渲染引擎契约：任何驱动（Playwright / AWT）都只暴露这三件事 —— 类型、可用性、出图。
 *
 * 同时安置 [RenderOptions]（单次渲染选项）。Playwright 侧的出图格式在
 * `me.regadpole.plumbot.render.engine.playwright.ScreenshotFormat`，本文件只定义
 * 引擎必须暴露的最小契约（引擎 SPI 与实现分属 `engine` / `engine.playwright` 两层）。
 */
internal interface ImageRenderer {
    val type: String
    val isSupported: Boolean

    suspend fun render(viewRoute: String, payload: Any, options: RenderOptions = RenderOptions.DEFAULT): ByteArray?

    /**
     * 驱动是否确信模板实现了 [route]（`null` = 未知 / 该驱动不按路由渲染）。
     *
     * AWT 把任何路由都当纯文本画，保持默认 `null`；Playwright 用模板 manifest 的
     * `routes` 声明回答，供调用方在首次渲染前选路由。
     */
    fun supportsRoute(route: String): Boolean? = null

    fun shutdown() {}
}

internal data class RenderOptions(
    /**
     * 单次渲染超时（原生超时，非协程超时），由 `views.<route>.timeout_ms` 或
     * `render.playwright.render_timeout_ms` 决定。
     *
     * 这里**没有** `scaleFactor`：设备像素比是**浏览器上下文级**属性，上下文在启动时只
     * 创建一次，因此它只能由 `render.playwright.scale_factor` 全局配置 —— 放进 per-render
     * options 只会制造假契约。
     */
    val timeoutMs: Long = 5000L
) {
    companion object {
        val DEFAULT = RenderOptions()
    }
}

