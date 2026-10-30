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

package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PublicApi

/**
 * 第三方 BotAdapter 的运行时注册表，取用入口
 * [me.regadpole.plumbot.api.PlumBotAPI.extensionRegistry]。
 * 与内置适配器共享同一 type 命名空间（大小写不敏感）：重复 type、与内置 type 冲突
 * 或底层注册失败均抛 [me.regadpole.plumbot.api.exception.AdapterRegistrationException]，
 * 成功返回 true。宿主批量注销只移除注册项，**不会**停止已运行的 Bot。
 */
@PublicApi
interface BotExtensionRegistry {
    fun registerExternalFactory(factory: BotFactory, plugin: Plugin): Boolean
    fun unregisterExternalFactory(type: String, plugin: Plugin): Boolean
    fun unregisterAllFor(plugin: Plugin): Int
    fun snapshot(): List<Pair<String, Plugin>>
}
