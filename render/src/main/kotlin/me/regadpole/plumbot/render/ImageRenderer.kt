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

package me.regadpole.plumbot.render

internal interface ImageRenderer {
    val type: String
    val isSupported: Boolean

    suspend fun render(viewRoute: String, payload: Any, options: RenderOptions = RenderOptions.DEFAULT): ByteArray?

    fun shutdown() {}
}

internal data class RenderOptions(
    /**
     * 单次渲染超时（原生超时，非协程超时）。由 `views.<route>.timeout_ms` 或
     * `img.playwright.render_timeout_ms` 决定。
     *
     * 注意：这里**没有** `scaleFactor`。设备像素比是**浏览器上下文级**属性，
     * 而上下文在启动时只创建一次，因此它由 `feature.img.playwright.scale_factor`
     * 全局配置，无法逐次渲染变化 —— 把它放进 per-render options 只会制造假契约。
     */
    val timeoutMs: Long = 5000L
) {
    companion object {
        val DEFAULT = RenderOptions()
    }
}
