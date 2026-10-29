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

import me.regadpole.plumbot.api.platform.LogLevel
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Playwright 运行库的**装载门**：预热、渲染与 `/plumbot render doctor|install` 都必须
 * 经由这里，以满足三条不变式（首次装载 driver-bundle 是一次约 190 MB 的真实下载，
 * 任何调用方都不能把这次下载变成阻塞动作或重复动作）。
 *
 * 1. [startOnce] **绝不阻塞调用线程**：它只把装载排进平台异步泳道后立即返回，
 *    真正可能耗时的 [RenderLibraryLoader.ensurePlaywright] 只在异步线程上执行；
 * 2. **单飞**：同一时刻最多一个装载在跑（[loadLock] 同时保护调度路径与 doctor 的阻塞路径）；
 * 3. **成功后不再触发，失败进入冷却**：冷却期内重复请求直接拒绝，不再摸网络。
 *
 * 状态机：`IDLE → RUNNING → READY | COOLING`，`COOLING` 到期后可重新 `IDLE`。
 * 渲染路径（[PlaywrightImageRenderer]）只调用 [startOnce]，永远立即返回；
 * 阻塞型入口 [awaitLoad] 只有诊断/安装命令的异步泳道才允许调用。
 *
 * @param loader 平台注入的装载器；null 表示这个平台没有依赖加载器（直接拒绝，不假装成功）
 * @param scheduleAsync 平台异步泳道（`PlatformScheduler.runAsync`）；抛出即视为"无法调度"
 * @param log 日志出口（由门户注入 `PlatformContext::log`）
 * @param clock 时钟（测试可注入，避免真的等 60 秒）
 * @param cooldownMillis 失败冷却时长
 */
internal class PlaywrightInstallGate(
    private val loader: RenderLibraryLoader?,
    private val scheduleAsync: (() -> Unit) -> Unit,
    private val log: (LogLevel, String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MS
) {

    internal enum class State {
        /** 从未装载过，也没有在装载。 */
        IDLE,

        /** 装载任务已排进异步泳道或正在执行。 */
        RUNNING,

        /** 装载成功：后续请求一律放行，不再摸网络。 */
        READY,

        /** 装载失败：冷却期内拒绝新的装载请求。 */
        COOLING
    }

    private val state = AtomicReference(State.IDLE)
    private val coolingUntil = AtomicLong(0L)

    @Volatile
    private var lastFailure: String? = null

    /**
     * 真实装载的互斥锁。
     *
     * 调度路径与 [awaitLoad] 都可能进来，两者必须串行：Libby 的 `loadLibrary` 本身不是
     * 为并发调用设计的，两个线程同时下载同一批 jar 会产生半成品目录。
     */
    private val loadLock = Any()

    fun currentState(): State = state.get()

    fun isReady(): Boolean = state.get() == State.READY

    /** 装载进行中：渲染失败时据此把 ERROR 降为 INFO，避免把"正在装载"报成故障。 */
    fun isLoading(): Boolean = state.get() == State.RUNNING

    /** 冷却中且仍未到期。 */
    fun isCooling(): Boolean = state.get() == State.COOLING && coolingRemainingMillis() > 0L

    /** 最近一次失败原因；成功后清空。 */
    fun failureReason(): String? = lastFailure

    fun coolingRemainingMillis(): Long {
        val until = coolingUntil.get()
        if (until <= 0L) return 0L
        return (until - clock()).coerceAtLeast(0L)
    }

    /** 运行库已在类路径上（例如平台预装了 Playwright）时直接放行。 */
    fun markReady() {
        state.set(State.READY)
        coolingUntil.set(0L)
        lastFailure = null
    }

    /**
     * **非阻塞**入口：能调度就调度，调度不了就返回 false。渲染与预热只允许走这里。
     *
     * @param reason 触发来源（写进日志，便于区分"预热"与"渲染"）
     * @return true 表示本次真的调度了一个异步装载任务
     */
    fun startOnce(reason: String): Boolean {
        if (loader == null) return false
        if (!tryBegin()) return false
        return try {
            scheduleAsync { runScheduledLoad(reason) }
            true
        } catch (t: Throwable) {
            state.set(State.IDLE)
            log(
                LogLevel.WARN,
                "[PlumBot-Render] 无法调度 Playwright 按需装载（$reason）：" +
                    (t.message ?: t.javaClass.simpleName)
            )
            false
        }
    }

    /**
     * **阻塞**入口：真正执行一次装载并返回可展示的失败原因。**只允许平台异步泳道**调用
     * （`/plumbot render doctor|install`）。
     *
     * @return null 表示已就绪，否则为可直接打印给服主的原因（冷却中也走这里）
     */
    fun awaitLoad(): String? {
        if (state.get() == State.READY) return null
        if (isCooling()) {
            return lastFailure ?: "装载失败后处于 ${cooldownMillis / 1000}s 冷却中，请稍后重试"
        }
        val failure = performLoad()
        if (failure == null) {
            markReady()
            return null
        }
        recordFailure(failure)
        return failure
    }

    /** 抢占 RUNNING：已成功/已装载中/冷却中一律失败，避免重复下载。 */
    private fun tryBegin(): Boolean {
        while (true) {
            when (state.get()) {
                State.READY, State.RUNNING -> return false
                State.COOLING -> {
                    if (coolingRemainingMillis() > 0L) return false
                    if (state.compareAndSet(State.COOLING, State.RUNNING)) {
                        coolingUntil.set(0L)
                        return true
                    }
                }

                State.IDLE -> if (state.compareAndSet(State.IDLE, State.RUNNING)) return true
            }
        }
    }

    private fun runScheduledLoad(reason: String) {
        val failure = performLoad()
        if (failure == null) {
            markReady()
            return
        }
        recordFailure(failure)
        log(
            LogLevel.WARN,
            "[PlumBot-Render] Playwright 按需装载失败（$reason）：$failure；" +
                "已进入 ${cooldownMillis / 1000}s 冷却，本引擎暂时不可用。"
        )
    }

    /** 真正调用装载器；返回 null 表示成功。异常也折成原因返回，绝不向上抛。 */
    private fun performLoad(): String? = synchronized(loadLock) {
        val activeLoader = loader ?: return "未注入 RenderLibraryLoader（平台未注册依赖加载器）"
        try {
            if (activeLoader.ensurePlaywright()) {
                null
            } else {
                activeLoader.lastFailure()?.takeIf { it.isNotBlank() } ?: "Libby 未返回失败原因"
            }
        } catch (t: Throwable) {
            "按需装载异常：${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun recordFailure(reason: String) {
        lastFailure = reason
        coolingUntil.set(clock() + cooldownMillis)
        state.set(State.COOLING)
        // 异常路径单独一条 WARN：doctor 与日志按"按需装载异常"前缀检索该分支。
        if (reason.startsWith("按需装载异常")) {
            log(LogLevel.WARN, "[PlumBot-Render] $reason")
        }
    }

    companion object {
        /** 与门户的 `PLAYWRIGHT_LOAD_COOLDOWN_MS` 保持一致（60s）；单独可测时作为默认值。 */
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}
