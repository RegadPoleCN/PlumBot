// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.platform.PlatformContext

/**
 * 门户持有者：保证 `:render` 对外**只有一个实例、一套生命周期**。
 *
 * 平台插件在启用时调用 [bootstrap]（注入平台上下文与可选的依赖装载器），禁用 / 重载时
 * 调用 [shutdown]。多个实例会各建一套 Playwright 页面池与 Chromium，必须避免。
 */
@PublicApi
object RenderProvider {

    @Volatile
    private var portal: RenderPortal? = null

    @Volatile
    private var libraryLoader: RenderLibraryLoader? = null

    /** 平台层在启用早期注册依赖装载器；可在 [bootstrap] 之前调用，重复注册以最后一次为准。 */
    fun registerLibraryLoader(loader: RenderLibraryLoader) {
        libraryLoader = loader
    }

    /** 装配门户（幂等）；重复调用会先关闭旧实例，避免资源泄漏。 */
    @Synchronized
    fun bootstrap(context: PlatformContext, loader: RenderLibraryLoader? = null): RenderPortal {
        loader?.let { registerLibraryLoader(it) }
        portal?.let { existing ->
            runCatching { existing.shutdown() }
        }
        val created = ImageRenderManager(context, libraryLoader)
        portal = created
        return created
    }

    fun getOrNull(): RenderPortal? = portal

    /** 取门户实例，未初始化时抛错（与 [getOrNull] 成对，调用方不必到处写 `!!`）。 */
    fun get(): RenderPortal =
        portal ?: throw IllegalStateException("渲染门户尚未初始化：请先由平台层调用 RenderProvider.bootstrap(...)")

    /** 重载门户；未初始化时**静默无操作**，插件禁用阶段调用不会抛错。 */
    fun reload() {
        portal?.let { runCatching { it.reload() } }
    }

    /** 关闭并清空门户（fire-and-forget；重载/热替换用）。 */
    @Synchronized
    fun shutdown() {
        portal?.let { runCatching { it.shutdown() } }
        portal = null
    }

    /**
     * 关闭并**等待**回收完成（**停服路径专用**，见 [RenderPortal.shutdownAndAwait]）。
     *
     * 插件 `disable()` 必须等到 Playwright 的 node 驱动真正退出，否则类加载器先被关闭，
     * 回收线程会抛 zip file error。
     *
     * @return 是否在 `timeoutMs` 内排空（未初始化时视为已排空）。
     */
    @Synchronized
    fun shutdownAndAwait(timeoutMs: Long = DEFAULT_TEARDOWN_TIMEOUT_MS): Boolean {
        val current = portal ?: return true
        portal = null
        return runCatching { current.shutdownAndAwait(timeoutMs) }.getOrDefault(false)
    }
}
