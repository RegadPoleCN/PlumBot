// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.engines

import java.util.concurrent.atomic.AtomicLong
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.engine.AwtImageRenderer
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.engine.playwright.PlaywrightImageRenderer
import me.regadpole.plumbot.render.pipeline.routing.RoutePolicy
import me.regadpole.plumbot.render.support.RenderLimits
import me.regadpole.plumbot.render.theme.ThemeManager

/**
 * 引擎注册表与派生状态：装配、惰性构造、**按配置指纹的懒重建**、以及配置派生状态的
 * 异步重建调度。
 *
 * 为什么是"懒"重建：关闭 Chromium 要同步等 node 驱动退出，进行中的出图会降级为文本，
 * 还会留下一行"底层 Chromium 意外退出"的假故障日志。因此 `reload()` 只置标记，
 * 真正重建推迟到下一次 [rendererFor]。
 */
internal class EngineRegistry(
    private val context: PlatformContext,
    private val lock: Any,
    private val lifecycle: PlaywrightLifecycle,
    private val policy: RoutePolicy,
    private val themeOf: () -> ThemeManager,
    private val fontCssOf: () -> String?,
    private val applyFont: () -> Unit,
    private val refreshTheme: () -> Unit,
    private val invalidateAvatars: () -> Unit
) {

    /** 已装配引擎（key = `type.lowercase()`）。 */
    private val renderers = mutableMapOf<String, ImageRenderer>()

    /** Playwright 驱动（惰性构造；与 [renderers] 中的同名项是同一对象）。 */
    private var playwrightRenderer: PlaywrightImageRenderer? = null

    /** 派生状态重建的代数：快速连续的 configure/reload 只有最新一代生效。 */
    private val derivedStateGeneration = AtomicLong(0L)

    /** `configure()` 登记的配置指纹（"下次要用什么配置"）。 */
    private var pendingEngineFingerprint = ""

    /** 当前引擎表实际对应的配置指纹（"现在是什么配置"）。 */
    private var appliedEngineFingerprint = ""

    /** 已请求、尚未执行的引擎重建（懒重建：在下次真正需要引擎时执行）。 */
    @Volatile
    private var engineResetPending = false

    /** 记录"当前引擎表对应的配置指纹"，供 [noteConfigReload] 判断是否需要重建。 */
    fun markEnginesCurrent() {
        appliedEngineFingerprint = pendingEngineFingerprint
    }

    /** `configure()` 登记新指纹；启动期首次登记直接认领（见 [updateConfiguredFingerprint]）。 */
    fun updateConfiguredFingerprint(fingerprint: String) {
        pendingEngineFingerprint = fingerprint
        // 启动期第一次 configure：init() 装配的 AWT 引擎不固化配置，直接认领指纹即可。
        // 否则 applied 一直是空串，启动后第一次 /plumbot reload 会被误判成"配置已变化"
        // 而无谓地重建一次引擎。
        if (appliedEngineFingerprint.isEmpty()) appliedEngineFingerprint = pendingEngineFingerprint
    }

    fun register(renderer: ImageRenderer) {
        renderers[renderer.type.lowercase()] = renderer
    }

    val isImageCapable: Boolean
        get() = rendererFor(RenderLimits.ENGINE_AWT)?.isSupported == true ||
            lifecycle.isPlaywrightPresent() ||
            lifecycle.isReady()

    /**
     * **只调度** Playwright 按需装载，绝不在调用线程（主线程）上同步下载；配置了
     * playwright 时即使尚未装载完成也返回 true，让首帧进入渲染路径（最坏情况由 AWT
     * 兜底），避免启动期把图片直接判死。
     *
     * 必须扫描**所有已启用出图**的路由，而不是只看 [RenderRoute.STATUS]：逐路由覆盖
     * 同样需要预热。
     */
    fun warmUpEngines(): Boolean {
        val engines = RenderRoute.entries
            .filter { policy.picEnabled(it) }
            .map { runCatching { policy.engineFor(it) }.getOrDefault(RenderLimits.ENGINE_AWT) }
            .toSet()
        val playwrightConfigured = RenderLimits.ENGINE_PLAYWRIGHT in engines
        if (playwrightConfigured) lifecycle.schedulePlaywrightLoad()
        return isImageCapable || playwrightConfigured
    }

    fun rendererFor(engine: String): ImageRenderer? {
        // 懒重建：配置改过就先把旧引擎摘下交给回收线程，再按新配置构造。
        applyPendingEngineReset()
        if (engine == RenderLimits.ENGINE_PLAYWRIGHT) {
            playwrightRenderer?.let { return it }
            return synchronized(lock) {
                playwrightRenderer ?: PlaywrightImageRenderer(context, themeOf(), lifecycle.gate).also {
                    it.updateFontCss(fontCssOf())
                    playwrightRenderer = it
                    register(it)
                    markEnginesCurrent()
                }
            }
        }

        renderers[engine]?.let { return it }
        if (engine != RenderLimits.ENGINE_AWT) return null

        // AWT 引擎按需（重新）装配。
        // `reload()` 会在配置变化时关闭并清空引擎表，若这里只查表不重建，
        // reload 之后**再也出不了图**（渲染一律 Failed）。
        return synchronized(lock) {
            renderers[engine] ?: AwtImageRenderer(context).also {
                register(it)
                markEnginesCurrent()
            }
        }
    }

    /** 热更新主题：推给已存在的 Playwright 驱动，避免为了换主题而关闭 Chromium。 */
    fun forwardTheme(theme: ThemeManager) {
        synchronized(lock) { playwrightRenderer?.updateThemeManager(theme) }
    }

    /** 热更新页面字体 CSS：驱动可能尚未惰性构造，等它被创建时会取用 [fontCssOf]。 */
    fun forwardFontCss(css: String?) {
        synchronized(lock) { playwrightRenderer?.updateFontCss(css) }
    }

    /**
     * `reload()` 的引擎部分：**只在指纹变化时**置待重建标记（懒重建）。
     */
    fun noteConfigReload() {
        if (pendingEngineFingerprint == appliedEngineFingerprint) {
            context.log(
                LogLevel.INFO,
                "[PlumBot-Render] 渲染配置未变化，保留现有引擎（跳过 Chromium 关闭与重建）。"
            )
        } else {
            engineResetPending = true
            context.log(
                LogLevel.INFO,
                "[PlumBot-Render] 渲染配置已变化：引擎将在下一次出图前按新配置重建（进行中的出图不受影响）。"
            )
        }
    }

    /**
     * 将配置派生状态（字体 CSS、主题管理器）的重建调度到平台异步线程。
     *
     * generation 合并快速连续的 configure/reload，只有最新任务生效；引擎重建**不走
     * 这里**，由 [noteConfigReload] 置标记、[applyPendingEngineReset] 在需要时执行。
     */
    fun scheduleDerivedStateRebuild(reason: String) {
        val generation = derivedStateGeneration.incrementAndGet()
        try {
            context.scheduler.runAsync {
                if (generation != derivedStateGeneration.get()) return@runAsync
                try {
                    applyFont()
                    if (generation != derivedStateGeneration.get()) return@runAsync
                    refreshTheme()
                } catch (t: Throwable) {
                    context.log(
                        LogLevel.WARN,
                        "[PlumBot-Render] 异步重建渲染配置失败（$reason）：${t.message}"
                    )
                }
            }
        } catch (t: Throwable) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 无法调度渲染配置异步重建（$reason）：${t.message}"
            )
        }
    }

    /**
     * 执行被推迟的引擎重建（懒重建），只在**真正需要引擎时**（[rendererFor]）执行。
     *
     * 锁内只摘引用，关闭动作入队到 RenderTeardown：关闭 Playwright 会同步等待 node
     * 驱动进程退出，持锁执行会连带阻塞惰性构造与 `shutdown()`。
     */
    fun applyPendingEngineReset() {
        if (!engineResetPending) return
        val closing: Pair<List<ImageRenderer>, PlaywrightImageRenderer?> = synchronized(lock) {
            if (!engineResetPending) return
            engineResetPending = false
            val snapshot = renderers.values.toList() to playwrightRenderer
            renderers.clear()
            playwrightRenderer = null
            appliedEngineFingerprint = pendingEngineFingerprint
            snapshot
        }
        lifecycle.closeOffThread(closing.first, closing.second)
        // 熔断表随解析器一起重建：配置重载后应给上一次故障的端点一次新机会
        invalidateAvatars()
        if (closing.first.isEmpty() && closing.second == null) return
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 引擎已按新配置重建：后续出图由新引擎完成，旧引擎在后台回收。"
        )
    }

    /**
     * 关闭引擎表：**立即返回**，不在调用线程上关闭引擎（见 [PlaywrightLifecycle.closeOffThread]）。
     */
    fun shutdown() {
        // 取消尚未执行的配置重建任务；已进入任务体的那一代也会在锁内看到代数变化。
        derivedStateGeneration.incrementAndGet()
        engineResetPending = false
        val closing: Pair<List<ImageRenderer>, PlaywrightImageRenderer?> = synchronized(lock) {
            val snapshot = renderers.values.toList() to playwrightRenderer
            renderers.clear()
            playwrightRenderer = null
            // 引擎表已空：之后按当前配置惰性重建，不需要再标记"待重建"。
            appliedEngineFingerprint = pendingEngineFingerprint
            snapshot
        }
        invalidateAvatars()
        lifecycle.closeOffThread(closing.first, closing.second)
    }
}
