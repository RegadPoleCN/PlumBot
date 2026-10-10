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

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.platform.PlatformContext

/**
 * 门户持有者：保证 `:render` 对外**只有一个实例、一套生命周期**。
 *
 * 平台插件在启用时调用 [bootstrap]（注入平台上下文与可选的依赖装载器），
 * 在禁用/重载时调用 [shutdown] / 通过 [getOrNull] 取门户执行 `reload()`。
 *
 * 取代旧形态：`StatusCommandService` 自行 `new ImageRenderManager(...)` —— 每个实例都会
 * 新建一套 Playwright 页面池与 Chromium，且 `shutdown()`/`reload()` 全项目无人调用。
 */
@PublicApi
object RenderProvider {

    @Volatile
    private var portal: RenderPortal? = null

    @Volatile
    private var libraryLoader: RenderLibraryLoader? = null

    /**
     * 平台层在启用早期注册依赖装载器（实现 [RenderLibraryLoader]）。
     * 可在 [bootstrap] 之前调用；重复注册以最后一次为准。
     */
    fun registerLibraryLoader(loader: RenderLibraryLoader) {
        libraryLoader = loader
    }

    /**
     * 装配门户（幂等）。重复调用会先关闭旧实例，避免资源泄漏。
     */
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

    /**
     * 取门户实例，未初始化时抛错。
     *
     * 与 [getOrNull] 成对存在：调用方不必到处写 `!!`，且失败信息明确
     * （此前文档照抄 `getOrNull()` 之外的写法会直接编译失败 —— 见 N09）。
     */
    fun get(): RenderPortal =
        portal ?: throw IllegalStateException("渲染门户尚未初始化：请先由平台层调用 RenderProvider.bootstrap(...)")

    /**
     * 重载门户（等价于 `getOrNull()?.reload()`）。
     *
     * 未初始化时**静默无操作**：插件禁用阶段或渲染被关闭时调用不会抛错。
     */
    fun reload() {
        portal?.let { runCatching { it.reload() } }
    }

    /** 关闭并清空门户。 */
    @Synchronized
    fun shutdown() {
        portal?.let { runCatching { it.shutdown() } }
        portal = null
    }
}
