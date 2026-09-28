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

package me.regadpole.plumbot.velocity.platform

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class VelocityPlatformScheduler(
    private val plugin: Any,
    private val server: ProxyServer
) : PlatformScheduler {

    override fun runSync(task: () -> Unit): PlatformTaskHandle {
        val scheduled = server.scheduler.buildTask(plugin, Runnable { task() }).schedule()
        return wrapTask(scheduled)
    }

    override fun runAsync(task: () -> Unit): PlatformTaskHandle {
        return runSync(task)
    }

    override fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val scheduled = server.scheduler.buildTask(plugin, Runnable { task() })
            .delay(delay.toJavaDuration())
            .schedule()
        return wrapTask(scheduled)
    }

    override fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        return runLater(delay, task)
    }

    override fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val scheduled = server.scheduler.buildTask(plugin, Runnable { task() })
            .delay(initialDelay.toJavaDuration())
            .repeat(period.toJavaDuration())
            .schedule()
        return wrapTask(scheduled)
    }

    override fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        return runRepeating(initialDelay, period, task)
    }

    private fun wrapTask(task: ScheduledTask?): PlatformTaskHandle {
        return PlatformTaskHandle {
            if (task == null) false
            else {
                task.cancel()
                true
            }
        }
    }
}
