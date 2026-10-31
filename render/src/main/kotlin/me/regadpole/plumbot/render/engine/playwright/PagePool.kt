// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext

/**
 * Playwright 页面池 + 渲染协程泳道。
 *
 * 两条纪律：
 * - **池容量固定为 [PAGE_POOL_SIZE]**（=1）：只有前台标签页能可靠截图，且 Playwright Java
 *   的 API 不是线程安全的；单页把出图串行化，第二个请求在池上排队而不是互抢前台；
 * - **任何补页失败只告警**：池绝不能被永久缩容，否则后续渲染会永远卡在等页上。
 *
 * 页面池只负责"页面的生命"，浏览器/上下文/驱动的生命周期由 [EngineLifecycle] 负责；
 * 因此补页需要调用方传入当前 [BrowserContext]（空表示引擎已关停，直接放弃）。
 */
internal class PagePool(private val context: PlatformContext) {

    /** 池中的一个页面；[useCount] 用于定期换页，避免长时间复用同一渲染上下文积累状态。 */
    class PooledPage(val page: Page, var useCount: Int = 0)

    /**
     * 渲染协程泳道。并发 2 是给 AWT 兜底与排队留的余量，Playwright 侧复用单页，
     * 真正出图仍是串行的。
     */
    val dispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(RENDER_THREADS) { r ->
        Thread(r, "PlumBot-Render-Worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val pages = Channel<PooledPage>(PAGE_POOL_SIZE)

    /** 引擎启动后按 [PAGE_POOL_SIZE] 预填页面；多余的页面直接关掉以免泄漏。 */
    fun seed(browserContext: BrowserContext) {
        repeat(PAGE_POOL_SIZE) {
            val page = browserContext.newPage()
            if (pages.trySend(PooledPage(page, 0)).isFailure) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 页面池已满，关闭多余页面以避免泄漏。")
                runCatching { page.close() }
            }
        }
    }

    /**
     * 从池中取页；等待 [timeoutMs] 后仍无页返回 `null`。
     *
     * 超时由调用方映射成 `PW_RENDER_TIMEOUT`（池是排队点，超时语义属于渲染预算，
     * 不属于页面池本身）。
     */
    suspend fun acquire(timeoutMs: Long): PooledPage? =
        withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) { pages.receive() }

    /**
     * 归还页面：清掉页面级存储，复用次数达到 [MAX_REUSE_COUNT] 就换一张新页。
     *
     * 用 [NonCancellable] 执行：协程可能已因超时/取消而结束，回收动作不能被一起取消，
     * 否则页面会泄漏或池永久缩容。
     */
    suspend fun release(pooled: PooledPage, browserContext: BrowserContext?) {
        withContext(NonCancellable) {
            runCatching { pooled.page.evaluate("() => { localStorage.clear(); sessionStorage.clear(); }") }
            if (pooled.useCount >= MAX_REUSE_COUNT) {
                runCatching { pooled.page.close() }
                replenish(browserContext, 1)
            } else {
                pooled.useCount++
                if (pages.trySend(pooled).isFailure) {
                    runCatching { pooled.page.close() }
                }
            }
        }
    }

    /** 丢弃坏页并补一张新的；同样在 [NonCancellable] 内执行。 */
    suspend fun discard(pooled: PooledPage, browserContext: BrowserContext?) {
        withContext(NonCancellable) {
            runCatching { pooled.page.close() }
            replenish(browserContext, 1)
        }
    }

    /** 补充页面池。任何失败只告警，绝不让池被永久缩容。 */
    fun replenish(browserContext: BrowserContext?, count: Int = 1) {
        val ctx = browserContext ?: return
        repeat(count) {
            runCatching { ctx.newPage() }
                .onSuccess { page ->
                    if (pages.trySend(PooledPage(page, 0)).isFailure) {
                        runCatching { page.close() }
                    }
                }
                .onFailure {
                    context.log(LogLevel.WARN, "[PlumBot-Render] 重建页面失败：" + it.message)
                }
        }
    }

    /**
     * 排空页面池，把池内所有页面**交还给调用方**。
     *
     * 关闭动作本身带超时且需要守护线程，属于 [EngineLifecycle] 的回收编排；
     * 这里只负责把引用从池里摘出来（先摘引用、再关闭，并发渲染拿到空池会直接降级）。
     */
    fun releaseAll(): List<Page> {
        val drained = mutableListOf<Page>()
        var pooled = pages.tryReceive().getOrNull()
        while (pooled != null) {
            drained += pooled.page
            pooled = pages.tryReceive().getOrNull()
        }
        return drained
    }

    private companion object {
        /**
         * 页面池大小。**必须保持 1**：
         *
         * 1. Chromium 只给前台标签页出合成帧，后台页 `page.screenshot` 必然等到超时；
         * 2. Playwright Java 的 API 非线程安全，多页并发会抛 `Object doesn't exist`；
         * 3. 单页把出图串行化，第二个请求在页面池上排队而不是互抢前台 ——
         *    并发 2 是给 AWT 兜底与排队留的余量，Playwright 侧始终复用这一页。
         *
         * 需要更高吞吐时调大排队深度（[me.regadpole.plumbot.render.pipeline.ImageRenderManager]
         * 的 QUEUE_DEPTH），而不是加页面。
         */
        const val PAGE_POOL_SIZE = 1

        /** 渲染泳道并发度：Playwright 侧串行，余量留给 AWT 兜底与排队。 */
        const val RENDER_THREADS = 2

        /** 单页复用上限：超过就换新页，避免长会话积累页面级状态。 */
        const val MAX_REUSE_COUNT = 200
    }
}
