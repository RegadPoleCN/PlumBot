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
