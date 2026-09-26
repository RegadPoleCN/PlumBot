package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.PublicApi

/**
 * 平台任务执行句柄。
 * 用于主动取消正在排队或周期运行的调度任务。
 */
@PublicApi
fun interface PlatformTaskHandle {

    /**
     * 取消当前任务。
     * @return true 代表成功取消
     */
    fun cancel(): Boolean
}
