package me.regadpole.plumbot.adapter.onebot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object OneBotMessageParser {

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

    fun parse(
        elements: Iterable<JsonElement>,
        groupId: Long,
        cardResolver: (Long) -> String
    ): String {
        val builder = StringBuilder()
        for (element in elements) {
            val segment = element.asJsonObject ?: continue
            val type = segment.get("type")?.asString?.lowercase() ?: continue
            val data = segment.get("data")?.asJsonObject ?: JsonObject()

            when (type) {
                "text" -> {
                    data.get("text")?.asString?.let { builder.append(it) }
                }
                "at" -> {
                    val qqStr = data.get("qq")?.asString ?: ""
                    if (qqStr.equals("all", ignoreCase = true)) {
                        builder.append("@全体成员")
                    } else {
                        val qq = qqStr.toLongOrNull()
                        if (qq != null) {
                            val card = cardResolver(qq)
                            builder.append(if (card.isNotBlank()) "@$card" else "@$qq")
                        } else {
                            builder.append("@$qqStr")
                        }
                    }
                }
                "image" -> {
                    builder.append("[图片]")
                }
                "face" -> {
                    val id = data.get("id")?.asInt
                    val name = if (id != null) FACE_NAMES[id] ?: "$id" else "未知"
                    builder.append("[表情: $name]")
                }
                "mface" -> {
                    val summary = data.get("text")?.asString
                        ?: data.get("summary")?.asString
                        ?: "表情"
                    builder.append("[动画表情: $summary]")
                }
                "reply" -> {
                    builder.append("[回复消息]")
                }
                "forward", "node" -> {
                    val summary = data.get("summary")?.asString ?: "聊天记录"
                    builder.append("[转发: $summary]")
                }
                "file" -> {
                    val name = data.get("name")?.asString ?: "未知文件"
                    val size = data.get("size")?.asLong
                    val sizeStr = if (size != null) " (" + formatFileSize(size) + ")" else ""
                    builder.append("[文件: $name$sizeStr]")
                }
                "record" -> {
                    val duration = data.get("duration")?.asInt
                    val durationStr = if (duration != null) ": ${duration}秒" else ""
                    builder.append("[语音$durationStr]")
                }
                "video" -> {
                    val file = data.get("file")?.asString ?: data.get("name")?.asString ?: ""
                    val fileStr = if (file.isNotBlank()) ": $file" else ""
                    builder.append("[视频$fileStr]")
                }
                "share" -> {
                    val title = data.get("title")?.asString ?: "链接分享"
                    builder.append("[分享: $title]")
                }
                "json" -> {
                    val rawJson = data.get("data")?.asString
                    builder.append(parseJsonCard(rawJson))
                }
                "xml" -> {
                    val rawXml = data.get("data")?.asString ?: ""
                    val promptRegex = Regex("""prompt="([^"]+)"""")
                    val prompt = promptRegex.find(rawXml)?.groupValues?.get(1) ?: "卡片消息"
                    builder.append("[卡片: $prompt]")
                }
                "poke" -> {
                    builder.append("[戳一戳]")
                }
                "dice" -> {
                    val value = data.get("value")?.asInt ?: data.get("result")?.asInt
                    builder.append(if (value != null) "[掷骰子: ${value}点]" else "[掷骰子]")
                }
                "rps" -> {
                    val value = data.get("value")?.asInt ?: data.get("result")?.asInt
                    val rpsName = when (value) {
                        1 -> "石头"
                        2 -> "剪刀"
                        3 -> "布"
                        else -> ""
                    }
                    builder.append(if (rpsName.isNotEmpty()) "[猜拳: $rpsName]" else "[猜拳]")
                }
                "location" -> {
                    val title = data.get("title")?.asString ?: "位置分享"
                    builder.append("[位置: $title]")
                }
                else -> {
                    builder.append("[消息]")
                }
            }
        }
        return builder.toString()
    }

    private fun parseJsonCard(rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return "[小程序/卡片消息]"
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
