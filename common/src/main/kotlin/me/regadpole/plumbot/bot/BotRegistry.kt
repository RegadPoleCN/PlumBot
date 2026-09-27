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

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.platform.PlatformType

@PublicApi
class BotRegistry {
    private val factories = linkedMapOf<String, BotFactory>()

    fun register(factory: BotFactory) {
        factories[normalize(factory.metadata.type)] = factory
    }

    fun unregister(type: String) {
        factories.remove(normalize(type))
    }

    fun clear() {
        factories.clear()
    }

    fun find(type: String?): BotFactory? {
        if (type.isNullOrBlank()) return factories.values.firstOrNull()
        return factories[normalize(type)]
    }

    fun compatibleFactories(platformType: PlatformType): List<BotFactory> {
        return factories.values.filter { it.metadata.supports(platformType) }
    }

    fun registeredTypes(): Set<String> {
        return factories.keys.toSet()
    }

    fun factories(): List<BotFactory> {
        return factories.values.toList()
    }

    private fun normalize(type: String): String {
        return type.lowercase()
    }
}
