// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.assets

import java.io.File
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.DEFAULT_RENDER_USER_AGENT
import me.regadpole.plumbot.render.RenderTextStyle
import me.regadpole.plumbot.render.avatar.AvatarComposer
import me.regadpole.plumbot.render.engine.TextToImg
import me.regadpole.plumbot.render.support.RenderLimits
import me.regadpole.plumbot.render.theme.RenderFontInjector
import me.regadpole.plumbot.render.theme.ThemeManager

/**
 * 主题与字体：页面字体 CSS 的注入、主题管理器的重建、AWT 文本样式的换算。
 *
 * 这些都是**配置派生 + 有状态**的对象，只能在异步线程重建（读 TTF 最坏 16MB、
 * 扫模板目录还可能触发下载），因此门户只登记配置并调度，不在此同步执行。
 */
internal class ThemeService(
    private val context: PlatformContext,
    private val runtimeWebDir: File,
    private val lock: Any,
    private val userAgentOf: () -> String?,
    private val publishTheme: (ThemeManager) -> Unit,
    private val publishFontCss: (String?) -> Unit,
    private val invalidateAvatars: () -> Unit,
    private val onConfigReloaded: () -> Unit
) {

    /**
     * 当前主题管理器。
     *
     * Playwright 驱动在惰性构造时取用，热重建时通过 [publishTheme] 推给已存在的驱动，
     * 避免为了换主题而关闭 Chromium。
     */
    @Volatile
    var themeManager: ThemeManager = buildThemeManager()
        private set

    /** 待注入浏览器的字体 CSS（驱动惰性构造时取用）。 */
    @Volatile
    var fontCss: String? = null
        private set

    /** 最近一次 `configure()` 注入的 TTF；异步重建时只在工作线程读取。 */
    @Volatile
    private var configuredFontFile: File? = null

    /** `configure()` 记录的字体文件（供异步重建在锁外读取）。 */
    fun configuredFontFile(): File? = configuredFontFile

    /** 记录 `configure()` 注入的 TTF；真正读取与注入由 [applyRenderFont] 在异步线程完成。 */
    fun setConfiguredFont(file: File?) {
        configuredFontFile = file
    }

    /**
     * 用**同一个 TTF** 同时供两条引擎使用：AWT 由 [TextToImg.ttfFile] 直接加载，
     * 浏览器由注入 `@font-face` 加载。这样同一条消息无论走哪个引擎字形都一致，
     * 并解决模板侧中文 / Emoji 的豆腐块。
     */
    fun applyRenderFont(fontFile: File?) {
        val maxBytes = context.config.getInteger("render", "playwright", "font_max_bytes")
            .let { if (it > 0) it else RenderLimits.DEFAULT_FONT_MAX_BYTES }

        // 读 TTF / 生成 base64 CSS 在锁外完成（最坏 16MB，绝不能在主线程做）；
        // 只在发布结果时持锁，保证惰性构造时看到的 fontCss 与推送结果一致。
        val outcome = RenderFontInjector.buildCss(fontFile, maxBytes)
        synchronized(lock) {
            when (outcome) {
                is RenderFontInjector.Outcome.Css -> {
                    fontCss = outcome.text
                    // 驱动可能尚未惰性构造，等它被创建时会取用 fontCss
                    publishFontCss(outcome.text)
                }

                is RenderFontInjector.Outcome.Skipped -> {
                    fontCss = null
                    publishFontCss(null)
                    context.log(LogLevel.INFO, "[PlumBot-Render] 未注入页面字体：${outcome.reason}")
                }
            }
        }
    }

    /** 清单/配置 → 引擎样式；非法颜色回落到默认值而不是抛异常。 */
    fun toEngineStyle(style: RenderTextStyle): TextToImg.Style {
        val fallback = TextToImg.Style.DEFAULT
        val base = AvatarComposer.parseColor(style.baseColor)
        val text = AvatarComposer.parseColor(style.textColor)
        if (base == null) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 非法底板色 '${style.baseColor}'，已回落默认值")
        }
        if (text == null) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 非法文字色 '${style.textColor}'，已回落默认值")
        }
        return TextToImg.Style(
            plateColor = base ?: fallback.plateColor,
            textColor = text ?: fallback.textColor,
            autoOutline = style.autoOutline,
            contrastThreshold = style.contrastThreshold,
            maxLineWidth = style.maxLineWidth.takeIf { it >= 0 } ?: fallback.maxLineWidth
        )
    }

    /** 重建主题管理器并推给已存在的 Playwright 驱动；头像管线一并失效。 */
    fun refreshThemeManager() {
        // 构建会读配置并扫 dev/运行时目录，放在锁外；发布时持锁，避免覆盖并发构造的新驱动。
        val rebuilt = buildThemeManager()
        synchronized(lock) {
            themeManager = rebuilt
            publishTheme(rebuilt)
        }
        // 这三个都是"配置派生 + 有状态"，必须一起失效并在下次使用时重建：
        // 缓存目录/有效期、出口白名单/体积上限/UA、以及漏斗的熔断表。
        invalidateAvatars()
    }

    fun buildThemeManager(): ThemeManager {
        val devDir = context.config.getString("render", "playwright", "dev_web_dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
        return ThemeManager(context, runtimeWebDir, devDir, userAgentOf() ?: DEFAULT_RENDER_USER_AGENT)
    }

    /**
     * 重载：同步摘掉头像管线，字体/主题重建调度到异步线程；**引擎只在指纹变化时才
     * 重建，且推迟到下一次真正需要引擎时**（懒重建）。
     *
     * 引擎与头像管线都是**首次使用时按当时的配置**创建的，配置改动后必须丢弃重建，
     * 否则 reload 只换了主题：引擎选择、页面池参数、缓存目录、头像尺寸仍是旧值。
     * 无条件重建则每次 reload 都要关闭 Chromium（同步等待 node 驱动退出、进行中的
     * 出图降级为文本），还留下一行"底层 Chromium 意外退出"的假故障日志。
     */
    fun reload() {
        // 先在调用线程摘掉头像管线：丢引用是零 I/O 操作，但它承载着 AvatarResolver 的
        // 熔断表与内存缓存，留给异步任务会让 reload 之后的解析仍命中旧缓存/旧熔断
        // （表现为"改了配置却没生效"），调度失败时重载语义也会失效。
        invalidateAvatars()
        onConfigReloaded()
    }
}
