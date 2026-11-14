// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.adapter.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.alazeprt.aonebot.action.PostAction
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import java.util.concurrent.atomic.AtomicLong

class SendGroupMessageSegments(
    private val groupId: Long,
    private val messageSegments: JsonArray,
    private val autoEscape: Boolean = false
) : PostAction() {

    override fun getData(): String {
        val root = JsonObject()
        root.addProperty("action", "send_group_msg")

        val params = JsonObject()
        params.addProperty("group_id", groupId)
        params.add("message", messageSegments)
        params.addProperty("auto_escape", autoEscape)
        root.add("params", params)

        val echo = OneBotEchoGenerator.next()
        root.addProperty("echo", echo)

        return WebsocketBotClient.gson.toJson(root)
    }
}

class SendPrivateMessageSegments(
    private val userId: Long,
    private val messageSegments: JsonArray,
    private val autoEscape: Boolean = false
) : PostAction() {

    override fun getData(): String {
        val root = JsonObject()
        root.addProperty("action", "send_msg")

        val params = JsonObject()
        params.addProperty("message_type", "private")
        params.addProperty("user_id", userId)
        params.add("message", messageSegments)
        params.addProperty("auto_escape", autoEscape)
        root.add("params", params)

        val echo = OneBotEchoGenerator.next()
        root.addProperty("echo", echo)

        return WebsocketBotClient.gson.toJson(root)
    }
}

/**
 * echo 标识生成器：旧的毫秒取模 10000 在 10 秒周期内必然重号，并发生成时按 echo
 * 关联回复会错配；改为进程内单调递增计数器 + 当前毫秒，保证唯一且便于日志定位。
 */
internal object OneBotEchoGenerator {
    private val counter = AtomicLong(0)

    fun next(): String = "aob_" + System.currentTimeMillis() + "_" + counter.incrementAndGet()
}
