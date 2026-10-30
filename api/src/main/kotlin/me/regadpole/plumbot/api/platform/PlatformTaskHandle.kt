// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.PublicApi

/**
 * 平台任务执行句柄，用于主动取消排队中或周期运行的调度任务。
 */
@PublicApi
fun interface PlatformTaskHandle {

    /**
     * 取消当前任务；true 代表平台确认取消（已执行完或此前已取消的在 Bukkit 侧为 false）。
     */
    fun cancel(): Boolean
}
