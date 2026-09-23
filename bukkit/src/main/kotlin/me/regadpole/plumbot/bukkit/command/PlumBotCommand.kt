package me.regadpole.plumbot.bukkit.command

import me.regadpole.plumbot.bukkit.PlumBotBukkit
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class PlumBotCommand(private val plugin: PlumBotBukkit) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("plumbot.admin")) {
            sender.sendMessage("§c你没有权限执行此命令。")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                try {
                    plugin.reloadPlugin()
                    sender.sendMessage("§a[PlumBot] 配置文件与字体资源已成功重载！")
                } catch (e: Exception) {
                    sender.sendMessage("§c[PlumBot] 重载失败: ${e.message}")
                }
            }
            "status" -> {
                val bot = BotProvider.getBot()
                val botType = bot?.metadata?.displayName ?: "未连接"
                val database = DatabaseProvider.getDatabase()
                val dbStatus = if (database != null) "已连接" else "未连接"
                sender.sendMessage("§b===== [PlumBot 运行状态] =====")
                sender.sendMessage("§7Bot 类型: §a$botType")
                sender.sendMessage("§7数据库状态: §a$dbStatus")
                sender.sendMessage("§7在线玩家数: §a${plugin.listPlayers().size}")
            }
            "bind" -> {
                handleBindCommand(sender, args.drop(1))
            }
            else -> {
                sender.sendMessage("§c[PlumBot] 未知子命令，请输入 /plumbot 查看帮助。")
            }
        }
        return true
    }

    private fun handleBindCommand(sender: CommandSender, subArgs: List<String>) {
        if (subArgs.isEmpty()) {
            sender.sendMessage("§e用法: /plumbot bind <add|remove|query> ...")
            return
        }

        when (subArgs[0].lowercase()) {
            "query" -> {
                if (subArgs.size < 2) {
                    sender.sendMessage("§e用法: /plumbot bind query <游戏名|qq:QQ号>")
                    return
                }
                val target = subArgs[1]
                if (target.startsWith("qq:")) {
                    val qq = target.removePrefix("qq:")
                    val binds = DatabaseProvider.getBindByUser(qq)
                    sender.sendMessage("§a[PlumBot] QQ $qq 绑定的玩家: ${binds.keys.ifEmpty { listOf("无") }}")
                } else {
                    val qq = DatabaseProvider.getBindByName(target)
                    if (qq != null) {
                        sender.sendMessage("§a[PlumBot] 玩家 $target 绑定的 QQ: $qq")
                    } else {
                        sender.sendMessage("§c[PlumBot] 玩家 $target 尚未绑定 QQ。")
                    }
                }
            }
            "remove" -> {
                if (subArgs.size < 2) {
                    sender.sendMessage("§e用法: /plumbot bind remove <游戏名>")
                    return
                }
                val playerName = subArgs[1]
                val existing = DatabaseProvider.getBindByName(playerName)
                if (existing == null) {
                    sender.sendMessage("§c[PlumBot] 玩家 $playerName 未被绑定。")
                    return
                }
                DatabaseProvider.getDatabase()?.removeBind(playerName)
                plugin.kickPlayer(playerName)
                sender.sendMessage("§a[PlumBot] 已成功解绑并移出白名单: $playerName")
            }
            "add" -> {
                if (subArgs.size < 3) {
                    sender.sendMessage("§e用法: /plumbot bind add <QQ号> <游戏名>")
                    return
                }
                val qqStr = subArgs[1]
                val playerName = subArgs[2]
                val qq = qqStr.toLongOrNull()
                if (qq == null) {
                    sender.sendMessage("§c[PlumBot] QQ号必须为数字。")
                    return
                }
                if (DatabaseProvider.getBindByName(playerName) != null) {
                    sender.sendMessage("§c[PlumBot] 玩家 $playerName 已被其他账号绑定。")
                    return
                }
                DatabaseProvider.getDatabase()?.addBind(qq, playerName)
                sender.sendMessage("§a[PlumBot] 已成功为 QQ $qq 绑定白名单账号: $playerName")
            }
            else -> {
                sender.sendMessage("§c[PlumBot] 未知的绑定子命令: ${subArgs[0]}")
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§b===== [PlumBot 指令帮助] =====")
        sender.sendMessage("§e/plumbot reload §7- 热重载插件配置与字体")
        sender.sendMessage("§e/plumbot status §7- 查看当前 Bot 与数据库状态")
        sender.sendMessage("§e/plumbot bind query <游戏名|qq:QQ> §7- 查询白名单绑定")
        sender.sendMessage("§e/plumbot bind remove <游戏名> §7- 解绑玩家白名单")
        sender.sendMessage("§e/plumbot bind add <QQ> <游戏名> §7- 管理员添加白名单")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("plumbot.admin")) return emptyList()

        if (args.size == 1) {
            return listOf("reload", "status", "bind").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("bind", ignoreCase = true)) {
            return listOf("add", "remove", "query").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }
}
