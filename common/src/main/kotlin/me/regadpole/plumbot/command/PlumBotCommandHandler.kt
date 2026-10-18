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

package me.regadpole.plumbot.command

import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * `/plumbot` 命令的平台侧依赖：只暴露平台真正不同的能力，其余语义统一在
 * [PlumBotCommandHandler] 里。三个平台（Bukkit / Velocity / Hytale）各自实现一次。
 */
interface CommandHost {
    /** 状态标题里展示的名字，如 `PlumBot`、`PlumBot Velocity`、`PlumBot Hytale`。 */
    val displayName: String

    /** 平台自己的热重载入口（配置/字体/敏感词库）。失败直接抛异常，由处理器转成错误消息。 */
    fun reloadPlugin()

    fun onlinePlayerCount(): Int

    fun kickPlayer(playerName: String)
}

/** 平台侧消息落地：Bukkit 序列化为 `§` 文本，Velocity 直接发 Component，Hytale 转纯文本。 */
fun interface CommandOutput {
    fun send(message: Component)
}

/**
 * `/plumbot` 的唯一实现（三端共用同一套逻辑与 Adventure 组件）。
 *
 * 统一的原因：此前 Bukkit（`§` 字符串）、Velocity（MiniMessage）、Hytale（`Message.raw`）
 * 各写一份 help/status/bind 逻辑，文案与边界条件已经开始漂移；同一份逻辑只在这里维护，
 * 平台层只剩「权限判定 + 把 Component 交给平台」。
 */
class PlumBotCommandHandler(private val host: CommandHost) {

    fun execute(output: CommandOutput, args: List<String>) {
        if (args.isEmpty()) {
            sendHelp(output)
            return
        }

        when (args[0].lowercase()) {
            "reload" -> reload(output)
            "status" -> status(output)
            "bind" -> bind(output, args.drop(1))
            else -> output.send(error("[PlumBot] 未知子命令，请输入 /plumbot 查看帮助。"))
        }
    }

    /** 供各平台 Tab 补全使用；不在命令层做权限判断（平台适配器负责）。 */
    fun suggest(args: List<String>): List<String> = when {
        args.isEmpty() -> SUBCOMMANDS
        args.size == 1 -> SUBCOMMANDS.filter { it.startsWith(args[0], ignoreCase = true) }
        args.size == 2 && args[0].equals("bind", ignoreCase = true) ->
            BIND_SUBCOMMANDS.filter { it.startsWith(args[1], ignoreCase = true) }
        else -> emptyList()
    }

    private fun reload(output: CommandOutput) {
        try {
            host.reloadPlugin()
            output.send(success("[PlumBot] 配置文件、字体资源与敏感词库已成功重载！"))
        } catch (e: Exception) {
            output.send(error("[PlumBot] 重载失败: ${e.message}"))
        }
    }

    private fun status(output: CommandOutput) {
        val bot = BotProvider.getBot()
        val botType = bot?.metadata?.displayName ?: "未连接"
        val dbStatus = if (DatabaseProvider.getDatabase() != null) "已连接" else "未连接"

        output.send(Component.text("===== [${host.displayName} 运行状态] =====", NamedTextColor.AQUA))
        output.send(labelValue("Bot 类型: ", botType))
        output.send(labelValue("数据库状态: ", dbStatus))
        output.send(labelValue("在线玩家数: ", host.onlinePlayerCount().toString()))
    }

    private fun bind(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.isEmpty()) {
            output.send(usage("/plumbot bind <add|remove|query> ..."))
            return
        }
        if (DatabaseProvider.getDatabase() == null) {
            output.send(error("[PlumBot] 数据库尚未就绪，无法执行绑定操作。"))
            return
        }

        when (subArgs[0].lowercase()) {
            "query" -> bindQuery(output, subArgs)
            "remove" -> bindRemove(output, subArgs)
            "add" -> bindAdd(output, subArgs)
            else -> output.send(error("[PlumBot] 未知的绑定子命令: ${subArgs[0]}"))
        }
    }

    private fun bindQuery(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.size < 2) {
            output.send(usage("/plumbot bind query <游戏名|qq:QQ号>"))
            return
        }
        val target = subArgs[1]
        if (target.startsWith("qq:")) {
            val qq = target.removePrefix("qq:")
            val binds = DatabaseProvider.getBindByUser(qq)
            output.send(success("[PlumBot] QQ $qq 绑定的玩家: ${binds.keys.ifEmpty { listOf("无") }}"))
            return
        }
        val qq = DatabaseProvider.getBindByName(target)
        if (qq != null) {
            output.send(success("[PlumBot] 玩家 $target 绑定的 QQ: $qq"))
        } else {
            output.send(error("[PlumBot] 玩家 $target 尚未绑定 QQ。"))
        }
    }

    private fun bindRemove(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.size < 2) {
            output.send(usage("/plumbot bind remove <游戏名>"))
            return
        }
        val playerName = subArgs[1]
        if (DatabaseProvider.getBindByName(playerName) == null) {
            output.send(error("[PlumBot] 玩家 $playerName 未被绑定。"))
            return
        }
        DatabaseProvider.getDatabase()?.removeBind(playerName)
        host.kickPlayer(playerName)
        output.send(success("[PlumBot] 已成功解绑并移出白名单: $playerName"))
    }

    private fun bindAdd(output: CommandOutput, subArgs: List<String>) {
        if (subArgs.size < 3) {
            output.send(usage("/plumbot bind add <QQ号> <游戏名>"))
            return
        }
        val qq = subArgs[1].toLongOrNull()
        val playerName = subArgs[2]
        if (qq == null) {
            output.send(error("[PlumBot] 无效的 QQ 号: ${subArgs[1]}"))
            return
        }
        val database = DatabaseProvider.getDatabase()
        if (database == null) {
            output.send(error("[PlumBot] 数据库尚未就绪，无法执行绑定。"))
            return
        }
        val existing = DatabaseProvider.getBindByName(playerName)
        if (existing != null) {
            output.send(error("[PlumBot] 玩家 $playerName 已被 QQ $existing 绑定！"))
            return
        }
        database.addBind(qq, playerName)
        output.send(success("[PlumBot] 成功将玩家 $playerName 绑定至 QQ $qq！"))
    }

    private fun sendHelp(output: CommandOutput) {
        output.send(Component.text("===== [PlumBot 指令帮助] =====", NamedTextColor.AQUA))
        output.send(helpLine("/plumbot reload", "热重载插件配置、字体资源与敏感词库"))
        output.send(helpLine("/plumbot status", "查看当前 Bot 与数据库状态"))
        output.send(helpLine("/plumbot bind query <游戏名|qq:QQ号>", "查询白名单绑定"))
        output.send(helpLine("/plumbot bind add <QQ号> <游戏名>", "管理员强制绑定"))
        output.send(helpLine("/plumbot bind remove <游戏名>", "管理员解绑并移出白名单"))
    }

    private fun helpLine(command: String, description: String): Component =
        Component.text(command, NamedTextColor.YELLOW)
            .append(Component.text(" - $description", NamedTextColor.GRAY))

    private fun labelValue(label: String, value: String): Component =
        Component.text(label, NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.GREEN))

    private fun success(text: String): Component = Component.text(text, NamedTextColor.GREEN)

    private fun error(text: String): Component = Component.text(text, NamedTextColor.RED)

    private fun usage(text: String): Component = Component.text("用法: $text", NamedTextColor.YELLOW)

    private companion object {
        val SUBCOMMANDS = listOf("reload", "status", "bind")
        val BIND_SUBCOMMANDS = listOf("query", "add", "remove")
    }
}
