package me.regadpole.plumbot

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ProxyServer
import me.regadpole.plumbot.utils.WhitelistHelper
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object PlumBotCommand {
    private val commands = arrayOf("reload", "addBind", "queryBind", "deleteBind")

    fun createBrigadierCommand(server: ProxyServer, plugin: PlumBot): BrigadierCommand {
        val plumbotNode =
            BrigadierCommand.literalArgumentBuilder("plumbot")
                 // Using the "then" method, you can add sub-arguments to the command.
                // For example, this subcommand will be executed when using the command "/test <some argument>"
                // A RequiredArgumentBuilder is a type of argument in which you can enter some undefined data
                // of some kind. For example, this example uses a StringArgumentType.word() that requires
                // a single word to be entered, but you can also use different ArgumentTypes provided by Brigadier
                // that return data of type Boolean, Integer, Float, other String types, etc
                .then(BrigadierCommand.requiredArgumentBuilder(
                    "mode",
                    StringArgumentType.greedyString()
                ).requires { source -> source.hasPermission("plumbot.command") }
                    .suggests { _: CommandContext<CommandSource?>?, builder: SuggestionsBuilder ->
                        commands.forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }.executes { context ->
                        val argumentProvided = context.getArgument("mode", String::class.java)
                    val arg = argumentProvided.split(" ")
                    when(arg[0]) {
                        "reload" -> {
                            plugin.bot!!.shutdown()
                            plugin.loadConfig()
                            plugin.loadDatabase()
                            plugin.loadBot()
                            context.source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(Component.text("Reload completed!", NamedTextColor.GREEN)))
                        }
                        "addBind" -> {
                            val source = context.source
                            if (arg.size != 3) source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(getComponentFromMiniMsg(plugin.messages.noCommandFound)))
                            else if (WhitelistHelper.getInstance(plugin).checkPlayerExists(arg[2])) {
                                val msg = plugin.messages.existsBind
                                    .replace("%player_name%", arg[2])
                                source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(Component.text(msg, NamedTextColor.RED)))
                            } else {
                                plugin.database!!.addBind(arg[1].toLong(), arg[2])
                                val message = getComponentFromMiniMsg(
                                    plugin.messages.commandAddBind
                                        .replace("%player%", arg[2])
                                        .replace("%target_id%", arg[1])
                                )
                                source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(message))
                            }
                        }
                        "deleteBind" -> {
                            val source = context.source
                            if (arg.size != 3) source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(getComponentFromMiniMsg(plugin.messages.noCommandFound)))
                            when(arg[1]) {
                                "id" -> {
                                    if (!WhitelistHelper.getInstance(plugin).checkPlayerExists(arg[2])) {
                                        val msg = plugin.messages.notExistsBind
                                            .replace("%player_name%", arg[2])
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(Component.text(msg, NamedTextColor.RED)))
                                    } else {
                                        plugin.database!!.removeBind(arg[2])
                                        val player = plugin.server.getPlayer(arg[2])
                                        if (player.isPresent) player.get().disconnect(
                                            getComponentFromMiniMsg(
                                                plugin.messages.kickServer
                                                    .replace(
                                                        "%groups%",
                                                        plugin.config!!.getLongListFromConfig("groups").toString()
                                                    )
                                            )
                                        )
                                        val message = getComponentFromMiniMsg(
                                            plugin.messages.commandDeleteBindById
                                                .replace("%player%", arg[2])
                                        )
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(message))
                                    }
                                }
                                "qq" -> {
                                    if (plugin.database!!.getBind(arg[2].toLong()).isNullOrEmpty()) {
                                        val msg = plugin.messages.qqEmptyBind
                                            .replace("%user_id%", arg[2])
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(Component.text(msg, NamedTextColor.RED)))
                                    } else {
                                        val wl = plugin.database!!.getBind(arg[2].toLong())
                                        plugin.database!!.removeBind(arg[2].toLong())
                                        wl!!.forEach {
                                            val player = plugin.server.getPlayer(it.key)
                                            if (player.isPresent) player.get().disconnect(
                                                getComponentFromMiniMsg(
                                                    plugin.messages.kickServer
                                                        .replace(
                                                            "%groups%",
                                                            plugin.config!!.getLongListFromConfig("groups").toString()
                                                        )
                                                )
                                            )
                                        }
                                        val message = getComponentFromMiniMsg(
                                            plugin.messages.commandDeleteBindByQQ
                                                .replace("%target_id%", arg[2])
                                        )
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(message))
                                    }
                                }
                            }
                        }
                        "queryBind" -> {
                            val source = context.source
                            if (arg.size != 3) source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(getComponentFromMiniMsg(plugin.messages.noCommandFound)))
                            when(arg[1]) {
                                "id" -> {
                                    val wl = plugin.database!!.getBind(arg[2])
                                    if (wl == null) {
                                        val msg = plugin.messages.idEmptyBind
                                            .replace("%player%", arg[2])
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(Component.text(msg, NamedTextColor.RED)))
                                    } else {
                                        val message = getComponentFromMiniMsg(
                                            plugin.messages.commandQueryBindById
                                                .replace("%player%", arg[2])
                                                .replace("%target_id%", wl.toString())
                                        )
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(message))
                                    }
                                }
                                "qq" -> {
                                    val wl = plugin.database!!.getBind(arg[2].toLong())
                                    if (wl.isNullOrEmpty()) {
                                        val msg = plugin.messages.qqEmptyBind
                                            .replace("%target_id%", arg[2])
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(Component.text(msg, NamedTextColor.RED)))
                                    } else {
                                        val message = getComponentFromMiniMsg(
                                            plugin.messages.commandQueryBindByQQ
                                                .replace("%target_id%", arg[2])
                                                .replace("%player%", wl.keys.joinToString())
                                        )
                                        source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(message))
                                    }
                                }
                            }
                        }
                    }
                        Command.SINGLE_SUCCESS
                    }
                )
                .executes { context: CommandContext<CommandSource> ->
                    val source = context.source
                    val message = getComponentFromMiniMsg(plugin.messages.prefix).append(getComponentFromMiniMsg(plugin.messages.noCommandFound))
                    source.sendMessage(getComponentFromMiniMsg(plugin.messages.prefix).append(message))
                    Command.SINGLE_SUCCESS
                }
                .build()

        // BrigadierCommand implements Command
        return BrigadierCommand(plumbotNode)
    }
}