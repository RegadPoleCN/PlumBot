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

package me.regadpole.plumbot.hytale.command

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.hytale.PlumBotHytale
import java.util.concurrent.CompletableFuture

class HytalePlumBotCommand(private val plugin: PlumBotHytale) : 
    AbstractCommand("plumbot", "PlumBot 管理命令") {

    init {
        requirePermission("plumbot.admin")
        addAliases("pb")
        setAllowsExtraArguments(true)
    }

    override fun execute(context: CommandContext): CompletableFuture<Void> {
        val input = context.inputString.trim()
        val parts = input.split("\\s+".toRegex()).drop(1)

        if (parts.isEmpty()) {
            sendHelp(context)
            return CompletableFuture.completedFuture(null)
        }

        when (parts[0].lowercase()) {
            "reload" -> {
                try {
                    plugin.reloadPlugin()
                    context.sendMessage(Message.raw("[PlumBot] 配置与敏感词库已成功重载！"))
                } catch (e: Exception) {
                    context.sendMessage(Message.raw("[PlumBot] 重载失败: ${e.message}"))
                }
            }
            "status" -> {
                val bot = BotProvider.getBot()
                val botType = bot?.metadata?.displayName ?: "未连接"
                val database = DatabaseProvider.getDatabase()
                val dbStatus = if (database != null) "已连接" else "未连接"
                context.sendMessage(Message.raw("===== [PlumBot Hytale 运行状态] ====="))
                context.sendMessage(Message.raw("Bot 类型: $botType"))
                context.sendMessage(Message.raw("数据库状态: $dbStatus"))
                context.sendMessage(Message.raw("在线玩家数: ${plugin.listPlayers().size}"))
            }
            "bind" -> {
                handleBind(context, parts.drop(1))
            }
            else -> {
                context.sendMessage(Message.raw("[PlumBot] 未知子命令，请输入 /plumbot 查看帮助。"))
            }
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun handleBind(context: CommandContext, args: List<String>) {
        if (args.isEmpty()) {
            context.sendMessage(Message.raw("用法: /plumbot bind <query|add|remove> ..."))
            return
        }
        when (args[0].lowercase()) {
            "query" -> {
                if (args.size < 2) {
                    context.sendMessage(Message.raw("用法: /plumbot bind query <游戏名|qq:QQ号>"))
                    return
                }
                val target = args[1]
                if (target.startsWith("qq:")) {
                    val qq = target.removePrefix("qq:")
                    val binds = DatabaseProvider.getBindByUser(qq)
                    context.sendMessage(Message.raw("[PlumBot] QQ $qq 绑定的玩家: ${binds.keys.ifEmpty { listOf("无") }}"))
                } else {
                    val qq = DatabaseProvider.getBindByName(target)
                    if (qq != null) {
                        context.sendMessage(Message.raw("[PlumBot] 玩家 $target 绑定的 QQ: $qq"))
                    } else {
                        context.sendMessage(Message.raw("[PlumBot] 玩家 $target 尚未绑定 QQ。"))
                    }
                }
            }
            "remove" -> {
                if (args.size < 2) {
                    context.sendMessage(Message.raw("用法: /plumbot bind remove <游戏名>"))
                    return
                }
                val playerName = args[1]
                val existing = DatabaseProvider.getBindByName(playerName)
                if (existing == null) {
                    context.sendMessage(Message.raw("[PlumBot] 玩家 $playerName 未被绑定。"))
                    return
                }
                DatabaseProvider.getDatabase()?.removeBind(playerName)
                plugin.kickPlayer(playerName)
                context.sendMessage(Message.raw("[PlumBot] 已成功解绑并移出白名单: $playerName"))
            }
            "add" -> {
                if (args.size < 3) {
                    context.sendMessage(Message.raw("用法: /plumbot bind add <QQ号> <游戏名>"))
                    return
                }
                val qq = args[1].toLongOrNull()
                val playerName = args[2]
                if (qq == null) {
                    context.sendMessage(Message.raw("[PlumBot] 无效的 QQ 号: ${args[1]}"))
                    return
                }
                val db = DatabaseProvider.getDatabase()
                if (db == null) {
                    context.sendMessage(Message.raw("[PlumBot] 数据库尚未就绪，无法执行绑定。"))
                    return
                }
                val existing = DatabaseProvider.getBindByName(playerName)
                if (existing != null) {
                    context.sendMessage(Message.raw("[PlumBot] 玩家 $playerName 已被 QQ $existing 绑定！"))
                    return
                }
                db.addBind(qq, playerName)
                context.sendMessage(Message.raw("[PlumBot] 成功将玩家 $playerName 绑定至 QQ $qq！"))
            }
            else -> context.sendMessage(Message.raw("未知操作，支持: query, add, remove"))
        }
    }

    private fun sendHelp(context: CommandContext) {
        context.sendMessage(Message.raw("===== [PlumBot Hytale 管理帮助] ====="))
        context.sendMessage(Message.raw("/plumbot reload - 重载配置文件与敏感词库"))
        context.sendMessage(Message.raw("/plumbot status - 查看运行状态与连接信息"))
        context.sendMessage(Message.raw("/plumbot bind query <游戏名|qq:QQ号> - 查询白名单绑定"))
        context.sendMessage(Message.raw("/plumbot bind add <QQ号> <游戏名> - 管理员强制绑定"))
        context.sendMessage(Message.raw("/plumbot bind remove <游戏名> - 管理员解绑白名单"))
    }
}
