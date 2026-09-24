package me.regadpole.plumbot.adapter.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Base64

/**
 * 专为 OneBot 协议构建标准的 JSON 消息段数组（Segment Array）。
 * 彻底消除 [CQ:xxx] 字符拼接与转义问题。
 */
object OneBotSegmentBuilder {

    /** 纯文本消息段 */
    fun text(text: String): JsonObject = JsonObject().apply {
        addProperty("type", "text")
        add("data", JsonObject().apply {
            addProperty("text", text)
        })
    }

    /** @群成员 消息段 */
    fun at(userId: Long): JsonObject = JsonObject().apply {
        addProperty("type", "at")
        add("data", JsonObject().apply {
            addProperty("qq", userId.toString())
        })
    }

    /** @全体成员 消息段 */
    fun atAll(): JsonObject = JsonObject().apply {
        addProperty("type", "at")
        add("data", JsonObject().apply {
            addProperty("qq", "all")
        })
    }

    /** 图片消息段（Base64 编码，安全且免落盘） */
    fun image(bytes: ByteArray): JsonObject = JsonObject().apply {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        addProperty("type", "image")
        add("data", JsonObject().apply {
            addProperty("file", "base64://$base64")
        })
    }

    /** 快速打包为段数组 */
    fun buildArray(vararg segments: JsonObject): JsonArray = JsonArray().apply {
        segments.forEach { add(it) }
    }
}
