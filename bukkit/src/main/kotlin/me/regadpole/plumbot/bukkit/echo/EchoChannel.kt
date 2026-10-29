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

package me.regadpole.plumbot.bukkit.echo

/** 已准备好、尚未派发的一次回显通道。 */
internal interface PreparedEcho {
    val channel: String
}

/** 本地（主线程）通道：Paper 反馈、插件指令代理、真实控制台、NMS、代理 sender。 */
internal class PreparedLocalEcho(
    override val channel: String,
    private val dispatcher: (String) -> Boolean,
    private val finishAction: (EchoSession) -> Unit = {},
    val detail: String? = null
) : PreparedEcho {

    fun dispatch(command: String): Boolean = dispatcher(command)

    fun finish(session: EchoSession) = finishAction(session)
}

/** RCON 网络通道：不接触主线程，在专用线程完成。 */
internal class PreparedRconEcho(
    override val channel: String,
    private val client: RconEchoClient
) : PreparedEcho {

    fun execute(command: String): RconOutcome = client.execute(command)
}
