// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
