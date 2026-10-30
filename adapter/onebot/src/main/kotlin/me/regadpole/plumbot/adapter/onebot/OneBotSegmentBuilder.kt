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

package me.regadpole.plumbot.adapter.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Base64

/**
 * 构建 OneBot 协议标准的 JSON 消息段数组（Segment Array），
 * 替代 `[CQ:xxx]` 字符串拼接，从根上避开 CQ 码转义问题。
 */
object OneBotSegmentBuilder {

    fun text(text: String): JsonObject = JsonObject().apply {
        addProperty("type", "text")
        add("data", JsonObject().apply {
            addProperty("text", text)
        })
    }

    fun at(userId: Long): JsonObject = JsonObject().apply {
        addProperty("type", "at")
        add("data", JsonObject().apply {
            addProperty("qq", userId.toString())
        })
    }

    fun atAll(): JsonObject = JsonObject().apply {
        addProperty("type", "at")
        add("data", JsonObject().apply {
            addProperty("qq", "all")
        })
    }

    /** 图片段：Base64 内联，免落盘 */
    fun image(bytes: ByteArray): JsonObject = JsonObject().apply {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        addProperty("type", "image")
        add("data", JsonObject().apply {
            addProperty("file", "base64://$base64")
        })
    }

    fun buildArray(vararg segments: JsonObject): JsonArray = JsonArray().apply {
        segments.forEach { add(it) }
    }
}
