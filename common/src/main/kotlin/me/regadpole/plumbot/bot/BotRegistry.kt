// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.platform.PlatformType

@PublicApi
class BotRegistry {
    private val factories = linkedMapOf<String, BotFactory>()

    fun register(factory: BotFactory) {
        synchronized(factories) { factories[normalize(factory.metadata.type)] = factory }
    }

    /**
     * 注销单个 factory。
     *
     * @return 确实删除了 factory 时返回 `true`；键不存在时返回 `false`（读-判-删在锁内一步完成，
     * 调用方无需再先查后删，避免两步之间状态被并发修改）。
     */
    fun unregister(type: String): Boolean {
        return synchronized(factories) { factories.remove(normalize(type)) != null }
    }

    fun clear() {
        synchronized(factories) { factories.clear() }
    }

    fun find(type: String?): BotFactory? {
        return synchronized(factories) {
            if (type.isNullOrBlank()) factories.values.firstOrNull() else factories[normalize(type)]
        }
    }

    fun compatibleFactories(platformType: PlatformType): List<BotFactory> {
        return synchronized(factories) { factories.values.filter { it.metadata.supports(platformType) } }
    }

    fun registeredTypes(): Set<String> {
        return synchronized(factories) { factories.keys.toSet() }
    }

    fun factories(): List<BotFactory> {
        return synchronized(factories) { factories.values.toList() }
    }

    private fun normalize(type: String): String {
        return type.lowercase()
    }
}
