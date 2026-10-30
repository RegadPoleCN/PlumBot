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

/**
 * 整次渲染的**时间预算**：入口处算出**绝对截止时间**，每个阶段只花**剩余**额度。
 *
 * `options.timeoutMs` 是整次渲染的预算，若逐阶段各计满一次（等页面池 → 导航 → 握手 →
 * READY → 截图），实际总耗时可达配置值的数倍，调用方设置的"超时"形同虚设，出图慢时还会
 * 一直占着渲染线程。独立成类是为了可单测（Playwright 驱动本身无法在本地运行）。
 */
internal class RenderBudget(
    timeoutMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val deadline: Long = clock() + timeoutMs.coerceAtLeast(1L)

    /** 剩余额度（毫秒），**下限 1ms** —— 阶段拿到 0 会被 Playwright 当作"立即超时"并抛出难懂的异常。 */
    fun remainingMs(): Long = (deadline - clock()).coerceAtLeast(1L)

    /** 剩余额度（**毫秒**，类型取 Double 是为了直接喂给 Playwright 的超时参数，不是秒）。 */
    fun remaining(): Double = remainingMs().toDouble()

    /** 预算是否已耗尽（用于提前放弃而不是继续发起新阶段）。 */
    fun exhausted(): Boolean = clock() >= deadline
}
