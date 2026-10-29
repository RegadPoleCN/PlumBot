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

import me.regadpole.plumbot.api.platform.PlatformConfig
import java.io.File

/**
 * 引擎指纹：**决定"现有引擎实例能否继续服务"的那组配置**的快照。
 *
 * 页面池、浏览器通道、缩放、输出格式、模板主题、字体注入都在首次使用时按当时的配置固化，
 * 配置真变了就必须重建。指纹只收录**构造期读过**的配置项；每次 reload 一律重建会让改一行
 * 无关配置（甚至只是重新保存同一个文件）都重启 Chromium —— 表现为紧跟一行「底层 Chromium
 * 意外退出」的假故障，并让进行中的出图降级。
 *
 * 纯粹影响"是否出图"的开关（`render.outputs.*`）与载荷治理阈值**不入指纹**：它们每次实时
 * 读取，改了立刻生效，不需要重启引擎。
 */
internal object RenderEngineFingerprint {

    /** 与引擎实例绑定、改动后必须重建的 `render.playwright.*` 配置项（构造期一次性读取）。 */
    private val ENGINE_KEYS = listOf(
        "theme",
        "template_protocol",
        "template_url",
        "template_sha256",
        "dev_web_dir",
        "scale_factor",
        "image_format",
        "jpeg_quality",
        "browsers_path",
        "custom_chrome_path",
        "browser_channel",
        "disable_sandbox",
        "fallback_to_awt",
        "render_timeout_ms",
        "font_max_bytes",
        "download_host"
    )

    fun of(config: PlatformConfig, fontFile: File?): String {
        val builder = StringBuilder(512)
        builder.append("engine=").append(describe(config, arrayOf("render", "engine"))).append('\n')
        RenderRoute.entries.forEach { route ->
            builder.append("view.").append(route.route).append('=')
                .append(describe(config, arrayOf("render", "views", route.route, "engine")))
                .append('\n')
        }
        ENGINE_KEYS.forEach { key ->
            builder.append(key).append('=')
                .append(describe(config, arrayOf("render", "playwright", key)))
                .append('\n')
        }
        builder.append("font=").append(fontDescriptor(fontFile)).append('\n')
        return builder.toString()
    }

    /** 字体内容（替换/改尺寸）同样要求重新注入页面，因此把路径、大小与 mtime 一并纳入。 */
    private fun fontDescriptor(fontFile: File?): String {
        if (fontFile == null) return "none"
        return fontFile.absolutePath + "|" + fontFile.isFile + "|" + fontFile.length() + "|" + fontFile.lastModified()
    }

    /**
     * 取一项配置的**稳定文本表示**。
     *
     * **只允许不变异的读取**（[PlatformConfig.contains] / [PlatformConfig.getString]）：
     * configurate 的 `getBoolean` / `getInteger` / `getDouble` 是**带默认值拷贝**的读法，
     * 类型对不上就把默认值（`false` / `0`）**写回节点**。把三种读法混进指纹，每算一次指纹
     * 就会把 34 个键逐个改写成 `false`：`render.engine` 变 `Boolean false` → 三端同时报
     * 「未知渲染引擎 'false'」并回落 AWT，doctor 读到 `browsers_path=false` 而误判
     * `PW_CONFIG_INVALID`。（回归用例：`RenderEngineFingerprintTest` / `ConfigReadSemanticsTest`。）
     *
     * `getString` 已能把布尔 / 整数 / 浮点节点字符串化（`true` / `10000` / `1.5`），缺失键
     * 写成固定占位 `-`，因此同一份配置永远得到同一个串（不做哈希：出问题时要能读）。
     */
    private fun describe(config: PlatformConfig, path: Array<String>): String {
        if (!config.contains(*path)) return "-"
        return runCatching { config.getString(*path) }.getOrNull() ?: "-"
    }
}
