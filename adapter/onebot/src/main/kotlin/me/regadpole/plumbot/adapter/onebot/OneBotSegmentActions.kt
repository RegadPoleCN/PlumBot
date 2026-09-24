package me.regadpole.plumbot.adapter.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import top.alazeprt.aonebot.action.PostAction
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient

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

        val echo = "aob_${System.currentTimeMillis() % 10000}"
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

        val echo = "aob_${System.currentTimeMillis() % 10000}"
        root.addProperty("echo", echo)

        return WebsocketBotClient.gson.toJson(root)
    }
}
