package me.regadpole.plumbot.command

import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.internal.PlumBotCommandSource
import me.regadpole.plumbot.utils.getMessageFromString
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

class PlumBotCommand(private val plugin: PlumBot) : AbstractCommand("plumbot", "commands for PlumBot") {
    init {
        setAllowsExtraArguments(true)
        addAliases("pb")
    }
    private val commands = arrayOf("reload", "addBind", "queryBind", "deleteBind")

    override fun execute(ctx: CommandContext): CompletableFuture<Void>? {
        val input: String = ctx.inputString
        val args = Arrays.stream(input.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()).collect(
            Collectors.toList()
        )
        if (args.isNotEmpty()) {
            val first = args[0]
            if (first == "plumbot" || first == "/plumbot" || first == "pb" || first == "/pb") {
                args.removeAt(0)
            }
        }
        val source = object: PlumBotCommandSource {
            override fun sendMessage(message: String) {
                ctx.sender().sendMessage(getMessageFromString(message))
            }

            override fun hasPermission(permission: String): Boolean {
                return ctx.sender().hasPermission(permission)
            }

        }
        CommandHandler.handleCommand(args.toTypedArray(), source, plugin)
        return null
    }

}