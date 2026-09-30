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

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandSender
import com.hypixel.hytale.server.core.permissions.PermissionHolder
import com.hypixel.hytale.server.core.permissions.PermissionQuery
import me.regadpole.plumbot.utils.OutputCapturingBuffer
import net.kyori.adventure.audience.Audience
import java.util.UUID

class CapturingHytaleCommandSender(
    private val capturer: OutputCapturingBuffer = OutputCapturingBuffer()
) : CommandSender, Audience by capturer {

    override fun getUsername(): String = "CONSOLE"
    override fun getUuid(): UUID = UUID(0L, 0L)

    override fun sendMessage(message: Message) {
        val raw = message.rawText ?: message.ansiMessage
        capturer.appendString(raw)
    }

    override fun hasPermission(permission: String): Boolean = true
    override fun hasPermission(permission: String, defaultVal: Boolean): Boolean = true
    override fun hasPermission(query: PermissionQuery): Boolean = true
    override fun hasPermission(query: PermissionQuery, defaultVal: Boolean): Boolean = true

    fun getOutput(): String = capturer.getResult()
}
