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

package me.regadpole.plumbot.api.platform

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 将整型数值转换为基于 20 TPS 规范的 Minecraft 游戏刻时长（1 tick = 50ms）。
 * 示例：`20.ticks` 相当于 `1.seconds`
 */
val Int.ticks: Duration
    get() = (this.toLong() * 50L).milliseconds

val Long.ticks: Duration
    get() = (this * 50L).milliseconds

/**
 * 将 Duration 转换为对应的 Minecraft 游戏刻数量（至少为 1 tick）。
 */
fun Duration.toTicks(): Long =
    (this.inWholeMilliseconds / 50L).coerceAtLeast(1L)
