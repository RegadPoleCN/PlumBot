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

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
interface PlatformConfig {
    fun getString(vararg path: String?): String?
    fun getLong(vararg path: String?): Long
    fun getInteger(vararg path: String?): Int
    fun getBoolean(vararg path: String?): Boolean
    fun getLongList(vararg path: String?): List<Long>
    fun getStringList(vararg path: String?): List<String?>
    fun getSubConfig(vararg path: String?): PlatformConfig
}
