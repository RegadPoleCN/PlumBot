package me.regadpole.plumbot.command

/**
 * 命令发送者接口
 */
interface PlumBotCommandSource {
    fun sendMessage(message: String)
    fun hasPermission(permission: String): Boolean
}