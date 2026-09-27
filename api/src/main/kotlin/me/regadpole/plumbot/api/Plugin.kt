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
 * Platform-neutral view of a host plugin.
 *
 * Concrete platform implementations:
 *  - [me.regadpole.plumbot.bukkit.platform.BukkitPlugin] (Bukkit API).
 *
 * Adding a new platform means adding an adapter wrapping the platform's
 * native plugin object. Third-party plugins keep using this contract.
 */
@PublicApi
interface Plugin {
    val name: String
    val version: String
    val isEnabled: Boolean
}
