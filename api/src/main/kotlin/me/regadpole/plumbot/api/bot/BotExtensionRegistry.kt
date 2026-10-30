// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
