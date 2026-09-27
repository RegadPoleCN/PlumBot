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

package me.regadpole.plumbot.adapter.miraimc

import com.google.gson.JsonParser

object MiraiCodeParser {

    private val MIRAI_TAG_REGEX = Regex("""\[mirai:([a-zA-Z0-9]+)(?::([^\]]+))?\]""")

    private val FACE_NAMES: Map<Int, String> = mapOf(
        0 to "惊讶", 1 to "撇嘴", 2 to "色", 3 to "发呆", 4 to "得意", 5 to "流泪", 6 to "害羞",
        7 to "闭嘴", 8 to "睡", 9 to "大哭", 10 to "尴尬", 11 to "发怒", 12 to "调皮", 13 to "呲牙",
        14 to "微笑", 15 to "难过", 16 to "酷", 17 to "抓狂", 18 to "吐", 19 to "偷笑", 20 to "可爱",
        21 to "白眼", 22 to "傲慢", 23 to "饥饿", 24 to "困", 25 to "惊恐", 26 to "流汗", 27 to "憨笑",
        28 to "悠闲", 29 to "奋斗", 30 to "咒骂", 31 to "疑问", 32 to "嘘", 33 to "晕", 34 to "折磨",
        35 to "衰", 36 to "骷髅", 37 to "敲打", 38 to "再见", 39 to "擦汗", 40 to "抠鼻", 41 to "鼓掌",
        42 to "糗大了", 43 to "坏笑", 44 to "左哼哼", 45 to "右哼哼", 46 to "哈欠", 47 to "鄙视", 48 to "委屈",
        49 to "快哭了", 50 to "阴险", 51 to "亲亲", 52 to "吓", 53 to "可怜", 54 to "菜刀", 55 to "西瓜",
        56 to "啤酒", 57 to "篮球", 58 to "乒乓", 59 to "咖啡", 60 to "饭", 61 to "猪头", 62 to "玫瑰",
        63 to "凋谢", 64 to "嘴唇", 65 to "爱心", 66 to "心碎", 67 to "蛋糕", 68 to "闪电", 69 to "炸弹"
    )

    fun parse(rawMessage: String, cardResolver: (Long) -> String): String {
        val result = MIRAI_TAG_REGEX.replace(rawMessage) { match ->
            val type = match.groupValues[1].lowercase()
            val param = match.groupValues.getOrNull(2) ?: ""

            when (type) {
                "at" -> {
                    if (param.equals("all", ignoreCase = true)) {
                        "@全体成员"
                    } else {
                        val targetQq = param.toLongOrNull()
                        val card = targetQq?.let { cardResolver(it) }
                        if (!card.isNullOrBlank()) "@$card" else "@$param"
                    }
                }
                "image" -> "[图片]"
                "face" -> {
                    val id = param.toIntOrNull()
                    val name = if (id != null) FACE_NAMES[id] ?: "$id" else param
                    "[表情: $name]"
                }
                "marketface" -> {
                    val name = param.substringBefore(",").substringBefore("=")
                    "[动画表情${if (name.isNotBlank()) ": $name" else ""}]"
                }
                "voice" -> {
                    val lengthPart = param.split(",").firstOrNull { it.startsWith("length=") }
                    val sec = lengthPart?.removePrefix("length=")?.toIntOrNull()
                    if (sec != null) "[语音: ${sec}秒]" else "[语音]"
                }
                "quote" -> "[回复消息]"
                "forward" -> {
                    val titlePart = param.split(",").firstOrNull { it.startsWith("title=") }
                    val title = titlePart?.removePrefix("title=") ?: "聊天记录"
                    "[转发: $title]"
                }
                "file" -> {
                    val parts = param.split(",")
                    val name = parts.getOrNull(1) ?: "未知文件"
                    val size = parts.getOrNull(2)?.toLongOrNull()
                    val sizeStr = if (size != null) " (" + formatFileSize(size) + ")" else ""
                    "[文件: $name$sizeStr]"
                }
                "video" -> {
                    val name = param.split(",").firstOrNull { !it.contains("=") && !it.startsWith("{") } ?: ""
                    if (name.isNotBlank()) "[视频: $name]" else "[视频]"
                }
                "music" -> {
                    val parts = param.split(",")
                    val title = parts.getOrNull(1) ?: "音乐"
                    val singer = parts.getOrNull(2)
                    if (!singer.isNullOrBlank()) "[音乐: $title - $singer]" else "[音乐: $title]"
                }
                "app" -> parseJsonCard(param)
                "service" -> {
                    val prompt = param.substringAfter("prompt=\"", "").substringBefore("\"", "")
                    if (prompt.isNotBlank()) "[卡片: $prompt]" else "[卡片消息]"
                }
                "poke" -> "[戳一戳]"
                "dice" -> {
                    val value = param.toIntOrNull()
                    if (value != null) "[掷骰子: ${value}点]" else "[掷骰子]"
                }
                "rps" -> {
                    val value = param.toIntOrNull()
                    val rpsName = when (value) {
                        1 -> "石头"
                        2 -> "剪刀"
                        3 -> "布"
                        else -> ""
                    }
                    if (rpsName.isNotEmpty()) "[猜拳: $rpsName]" else "[猜拳]"
                }
                else -> "[消息]"
            }
        }

        return result
            .replace("\\[", "[")
            .replace("\\]", "]")
            .replace("\\\\", "\\")
    }

    private fun parseJsonCard(rawJson: String): String {
        return try {
            val obj = JsonParser.parseString(rawJson).asJsonObject
            val prompt = obj.get("prompt")?.asString
            if (!prompt.isNullOrBlank()) {
                val cleanedPrompt = prompt.removePrefix("[").removeSuffix("]")
                "[卡片: $cleanedPrompt]"
            } else {
                "[卡片消息]"
            }
        } catch (_: Exception) {
            "[卡片消息]"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f%sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
