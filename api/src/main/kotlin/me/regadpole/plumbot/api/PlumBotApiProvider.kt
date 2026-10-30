// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api

/**
 * 跨平台 API 单例持有者：核心启动时 [register] 注入，外部插件用 [PlumBotAPI.get] 获取。
 * 未注册或已 [unregister] 时 [get] 抛 [IllegalStateException]。
 */
@PublicApi
object PlumBotApiProvider {

    @Volatile
    private var instance: PlumBotAPI? = null

    @JvmStatic
    fun get(): PlumBotAPI =
        instance ?: error("PlumBotAPI 尚未初始化！请确保 PlumBot 已正确加载且处于已启用状态。")

    fun getInstanceOrNull(): PlumBotAPI? = instance

    fun register(api: PlumBotAPI) {
        instance = api
    }

    fun unregister() {
        instance = null
    }
}
