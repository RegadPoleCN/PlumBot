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

package me.regadpole.plumbot.hytale.platform

import kotlinx.coroutines.*
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import kotlin.time.Duration

class HytalePlatformScheduler(
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("PlumBot-Hytale-Scheduler"))
) : PlatformScheduler {

    override fun runSync(task: () -> Unit): PlatformTaskHandle = runAsync(task)

    override fun runAsync(task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch { task() }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch {
            delay(delay)
            task()
        }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle = runLater(delay, task)

    override fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch {
            delay(initialDelay)
            while (isActive) {
                task()
                delay(period)
            }
        }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle =
        runRepeating(initialDelay, period, task)

    fun shutdown() {
        scope.cancel()
    }
}
