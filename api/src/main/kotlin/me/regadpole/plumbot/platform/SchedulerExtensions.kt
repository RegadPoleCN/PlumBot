package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.PublicApi
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
