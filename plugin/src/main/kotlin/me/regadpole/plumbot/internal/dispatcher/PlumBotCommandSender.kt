package me.regadpole.plumbot.internal.dispatcher

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandSender
import java.util.*

class PlumBotCommandSender internal constructor(private val num: Int): CommandSender {
    private val uuid = UUID(0L, 0L)

    override fun sendMessage(message: Message) {
        message.rawText?.let { CommandDispatcher.result.put(num, it) }
    }

    override fun hasPermission(p0: String): Boolean {
        return true
    }

    override fun hasPermission(p0: String, p1: Boolean): Boolean {
        return true
    }

    override fun getDisplayName(): String {
        return "PlumBot"
    }

    override fun getUuid(): UUID {
        return uuid
    }
}