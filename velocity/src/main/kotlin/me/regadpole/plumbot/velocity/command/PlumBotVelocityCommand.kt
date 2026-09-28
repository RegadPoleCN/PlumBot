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

package me.regadpole.plumbot.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.utils.asMiniMessage
import me.regadpole.plumbot.velocity.PlumBotVelocity

class PlumBotVelocityCommand(private val plugin: PlumBotVelocity) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val args = invocation.arguments()

        if (!hasPermission(invocation)) {
            source.sendMessage("<red>你没有权限执行此命令。".asMiniMessage())
            return
        }

        if (args.isEmpty()) {
            sendHelp(invocation)
            return
        }

        when (args[0].lowercase()) {
            "reload" -> {
                try {
                    plugin.reloadPlugin()
                    source.sendMessage("<green>[PlumBot] 配置文件、字体资源与敏感词库已成功重载！".asMiniMessage())
                } catch (e: Exception) {
                    source.sendMessage("<red>[PlumBot] 重载失败: ${e.message}".asMiniMessage())
                }
            }
            "status" -> {
                val bot = BotProvider.getBot()
                val botType = bot?.metadata?.displayName ?: "未连接"
                val database = DatabaseProvider.getDatabase()
                val dbStatus = if (database != null) "已连接" else "未连接"
                source.sendMessage("<aqua>===== [PlumBot Velocity 运行状态] =====</aqua>".asMiniMessage())
                source.sendMessage("<gray>Bot 类型: <green>$botType</green></gray>".asMiniMessage())
                source.sendMessage("<gray>数据库状态: <green>$dbStatus</green></gray>".asMiniMessage())
                source.sendMessage("<gray>代理在线玩家数: <green>${plugin.listPlayers().size}</green></gray>".asMiniMessage())
            }
            "bind" -> {
                handleBindCommand(invocation, args.drop(1))
            }
            else -> {
                source.sendMessage("<red>[PlumBot] 未知子命令，请输入 /plumbot 查看帮助。".asMiniMessage())
            }
        }
    }

    private fun handleBindCommand(invocation: SimpleCommand.Invocation, subArgs: List<String>) {
        val source = invocation.source()
        if (subArgs.isEmpty()) {
            source.sendMessage("<yellow>用法: /plumbot bind <add|remove|query> ...</yellow>".asMiniMessage())
            return
        }

        when (subArgs[0].lowercase()) {
            "query" -> {
                if (subArgs.size < 2) {
                    source.sendMessage("<yellow>用法: /plumbot bind query <游戏名|qq:QQ号></yellow>".asMiniMessage())
                    return
                }
                val target = subArgs[1]
                if (target.startsWith("qq:")) {
                    val qq = target.removePrefix("qq:")
                    val binds = DatabaseProvider.getBindByUser(qq)
                    source.sendMessage("<green>[PlumBot] QQ $qq 绑定的玩家: ${binds.keys.ifEmpty { listOf("无") }}</green>".asMiniMessage())
                } else {
                    val qq = DatabaseProvider.getBindByName(target)
                    if (qq != null) {
                        source.sendMessage("<green>[PlumBot] 玩家 $target 绑定的 QQ: $qq</green>".asMiniMessage())
                    } else {
                        source.sendMessage("<red>[PlumBot] 玩家 $target 尚未绑定 QQ。</red>".asMiniMessage())
                    }
                }
            }
            "remove" -> {
                if (subArgs.size < 2) {
                    source.sendMessage("<yellow>用法: /plumbot bind remove <游戏名></yellow>".asMiniMessage())
                    return
                }
                val playerName = subArgs[1]
                val existing = DatabaseProvider.getBindByName(playerName)
                if (existing == null) {
                    source.sendMessage("<red>[PlumBot] 玩家 $playerName 未被绑定。</red>".asMiniMessage())
                    return
                }
                DatabaseProvider.getDatabase()?.removeBind(playerName)
                plugin.kickPlayer(playerName)
                source.sendMessage("<green>[PlumBot] 已成功解绑并移出白名单: $playerName</green>".asMiniMessage())
            }
            "add" -> {
                if (subArgs.size < 3) {
                    source.sendMessage("<yellow>用法: /plumbot bind add <QQ号> <游戏名></yellow>".asMiniMessage())
                    return
                }
                val qqStr = subArgs[1]
                val playerName = subArgs[2]
                val qq = qqStr.toLongOrNull()
                if (qq == null) {
                    source.sendMessage("<red>[PlumBot] 无效的 QQ 号: $qqStr</red>".asMiniMessage())
                    return
                }
                val db = DatabaseProvider.getDatabase()
                if (db == null) {
                    source.sendMessage("<red>[PlumBot] 数据库尚未就绪，无法执行绑定。</red>".asMiniMessage())
                    return
                }
                val existing = DatabaseProvider.getBindByName(playerName)
                if (existing != null) {
                    source.sendMessage("<red>[PlumBot] 玩家 $playerName 已被 QQ $existing 绑定！</red>".asMiniMessage())
                    return
                }
                db.addBind(qq, playerName)
                source.sendMessage("<green>[PlumBot] 成功将玩家 $playerName 绑定至 QQ $qq！</green>".asMiniMessage())
            }
            else -> {
                source.sendMessage("<red>[PlumBot] 未知操作，支持: query, remove, add</red>".asMiniMessage())
            }
        }
    }

    private fun sendHelp(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        source.sendMessage("<aqua>===== [PlumBot 管理命令] =====</aqua>".asMiniMessage())
        source.sendMessage("<yellow>/plumbot reload</yellow> <gray>- 重载配置文件与敏感词库</gray>".asMiniMessage())
        source.sendMessage("<yellow>/plumbot status</yellow> <gray>- 查看运行状态与连接信息</gray>".asMiniMessage())
        source.sendMessage("<yellow>/plumbot bind query <游戏名|qq:QQ号></yellow> <gray>- 查询白名单绑定</gray>".asMiniMessage())
        source.sendMessage("<yellow>/plumbot bind add <QQ号> <游戏名></yellow> <gray>- 管理员强制绑定</gray>".asMiniMessage())
        source.sendMessage("<yellow>/plumbot bind remove <游戏名></yellow> <gray>- 管理员解绑白名单</gray>".asMiniMessage())
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val args = invocation.arguments()
        if (args.isEmpty()) return listOf("reload", "status", "bind")
        if (args.size == 1) {
            return listOf("reload", "status", "bind").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("bind", ignoreCase = true)) {
            return listOf("query", "add", "remove").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("plumbot.admin")
    }
}
