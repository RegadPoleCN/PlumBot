package me.regadpole.plumbot.utils

import com.hypixel.hytale.server.core.Message

fun getMessageFromString(msg: String): Message {
    return Message.parse(msg)
}