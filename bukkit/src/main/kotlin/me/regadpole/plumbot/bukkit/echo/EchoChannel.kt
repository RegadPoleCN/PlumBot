// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.bukkit.echo.capture.EchoSession

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
