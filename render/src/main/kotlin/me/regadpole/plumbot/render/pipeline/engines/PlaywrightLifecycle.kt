// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.engines

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderLibraryLoader
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.engine.playwright.PlaywrightImageRenderer
import me.regadpole.plumbot.render.install.PlaywrightInstallGate
import me.regadpole.plumbot.render.pipeline.RenderTeardown
import me.regadpole.plumbot.render.pipeline.routing.RoutePolicy
import me.regadpole.plumbot.render.support.RenderLimits

/**
 * Playwright 运行库的**装载门**：调度、阻塞装载、状态查询与回收编排。
 *
 * 核心纪律：调度与装载分离。调度（[schedulePlaywrightLoad]）只把手上的活交给平台
 * 异步线程并立刻返回；阻塞装载（[ensurePlaywrightLibrary]）只允许异步泳道调用，
 * 主线程路径（出图、命令回复）永远只是"顺带催一下"，绝不在此同步下载。
 */
internal class PlaywrightLifecycle(
    private val context: PlatformContext,
    private val libraryLoader: RenderLibraryLoader?,
    private val policy: RoutePolicy
) {

    /** 装载门：一次只跑一个装载任务，成功/失败/冷却状态全部在这里收敛。 */
    val gate = PlaywrightInstallGate(
        loader = libraryLoader,
        scheduleAsync = { task -> context.scheduler.runAsync(task) },
        log = { level, message -> context.log(level, message) },
        cooldownMillis = RenderLimits.PLAYWRIGHT_LOAD_COOLDOWN_MS
    )

    /**
     * 调度一次异步装载：同一时间只有一个任务；已成功 / 冷却中 / 无 loader 时直接返回。
     * 调度失败只记录 WARN，**绝不**回退到调用线程同步下载。
     */
    fun schedulePlaywrightLoad() {
        // 门的内部判断：已就绪 / 装载中 / 冷却中 / 无 loader 一律直接返回，且**只调度不阻塞**。
        gate.startOnce("warm-up")
    }

    /** 配置要求 playwright 时调度一次异步装载；无 loader / 已成功 / 冷却中均直接返回。 */
    fun schedulePlaywrightLoadIfConfigured() {
        if (!policy.playwrightConfiguredForGateways()) return
        schedulePlaywrightLoad()
    }

    /**
     * 真实装载（可能触发 Libby 下载，耗时/阻塞）。**只允许平台异步线程调用**：由
     * [schedulePlaywrightLoad] 调度，或由 `/plumbot render doctor|install` 在异步泳道调用；
     * 禁止在 [warmUpEngines] 或任何主线程路径直接调用 —— 该约束由 [PlaywrightInstallGate]
     * 与三端装载器自身的主线程守卫共同保证。
     *
     * @return null 表示已就绪；否则为**可展示给服主的失败原因**（doctor 直接打印）。
     */
    fun ensurePlaywrightLibrary(): String? {
        if (gate.isReady()) return null
        // 平台可能早已把 Playwright 放上类路径（预装 / 别的插件注入），此时不必再下载。
        if (isPlaywrightPresent()) {
            gate.markReady()
            return null
        }
        if (libraryLoader == null) return "未注入 RenderLibraryLoader（平台未注册依赖加载器）"
        return gate.awaitLoad()
    }

    /** Playwright 是否已在类路径上（不触发任何下载）。 */
    fun isPlaywrightPresent(): Boolean =
        runCatching { Class.forName(RenderLimits.PLAYWRIGHT_CLASS) }.isSuccess

    fun isReady(): Boolean = gate.isReady()

    fun isLoading(): Boolean = gate.isLoading()

    fun isCooling(): Boolean = gate.isCooling()

    /**
     * 把一批引擎的关闭动作交给 [RenderTeardown]（守护线程串行执行）；空列表直接跳过。
     *
     * 关闭 Playwright 会同步等待 node 驱动进程退出（profile 实测单 tick 最高 1817ms），
     * 因此**绝不在调用线程**执行。
     */
    fun closeOffThread(engines: List<ImageRenderer>, playwright: PlaywrightImageRenderer?) {
        if (engines.isEmpty() && playwright == null) return
        RenderTeardown.submit(
            onFailure = { t ->
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Render] 回收渲染引擎失败：" + (t.message ?: t.javaClass.simpleName)
                )
            }
        ) {
            engines.forEach { runCatching { it.shutdown() } }
            runCatching { playwright?.shutdown() }
        }
    }
}
