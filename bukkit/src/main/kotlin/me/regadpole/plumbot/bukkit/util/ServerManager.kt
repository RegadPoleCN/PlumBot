package me.regadpole.plumbot.bukkit.util

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.bot.KookBot
import me.regadpole.plumbot.bot.QQBot
import me.regadpole.plumbot.bukkit.listener.ConsoleSender
import me.regadpole.plumbot.tool.TextToImg
import org.bukkit.Bukkit
import taboolib.common.platform.function.submitAsync

object ServerManager {

    @JvmField
    var msgList: MutableList<String> = java.util.LinkedList()

    fun sendCmd(cmd: String, disp: Boolean): String {
        val returnStr = java.util.concurrent.atomic.AtomicReference("无返回值")

        val commandSender: org.bukkit.command.CommandSender = ConsoleSender()

        submitAsync(now = true) {
            msgList.clear()
            Bukkit.dispatchCommand(commandSender, cmd)
        }

        submitAsync(delay = 20L) {
            synchronized(returnStr) {
                (returnStr as Any).notify()
                val stringBuilder = java.lang.StringBuilder()
                if (msgList.size == 0) {
                    msgList.add("无返回值")
                }
                for (msg in msgList) {
                    if (msgList[msgList.size - 1].equals(msg, ignoreCase = true)) {
                        stringBuilder.append(msg)
                    } else {
                        stringBuilder.append(msg).append("\n")
                    }
                }
                if (!disp) {
                    msgList.clear()
                    returnStr.set("无返回值")
                }
                if (stringBuilder.toString().length <= 5000) {
                    //                PlumBot.getBot().sendMsg(isGroup, stringBuilder.toString(), id);
                    when (PlumBot.getBot()) {
                        is QQBot -> returnStr.set(TextToImg.toImgCQCode(stringBuilder.toString()))
                        is KookBot -> returnStr.set(stringBuilder.toString())
                        else -> {}
                    }
                } else {
                    returnStr.set("返回值过长")
                }
                msgList.clear()
            }
        }

        synchronized(returnStr) {
            try {
                (returnStr as Any).wait()
            } catch (e: InterruptedException) {
                throw java.lang.RuntimeException(e)
            }
            return returnStr.get()
        }
    }
}
