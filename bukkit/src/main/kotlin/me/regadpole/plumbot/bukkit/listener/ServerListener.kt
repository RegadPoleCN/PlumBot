package me.regadpole.plumbot.bukkit.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

class ServerListener(private val plugin: PlumBot): Listener {
    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (event.isCancelled) return
        if (!plugin.config.getBooleanFromConfig("feature", "message", "enable")) return

        val message = event.message.replace(Regex("&([0-9a-fklmnor])")) { matchResult ->
            "§" + matchResult.groupValues[1]
        }.replace(Regex("#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})"), "")
        if (plugin.config.getIntegerFromConfig("feature", "message", "mode") == 0) {
//          # server, player_name, message
            plugin.config.getLongListFromConfig("groups").forEach {
                BotProvider.getBot()?.sendMsg(true, it,
                    plugin.messages.server2ob
                    .replace("%server%", Bukkit.getServer().name)
                    .replace("%player_name%", event.player.name)
                    .replace("%message%", message), plugin.config.getBooleanFromConfig("feature", "message", "pic"))
            }
            return
        } else if (plugin.config.getIntegerFromConfig("feature", "message", "mode") == 1 &&
            Regex(plugin.config.getStringFromConfig("feature", "message", "prefix")!!).matchesAt(event.message, 0)
        ) {
//          # server, player_name, message
            plugin.config.getLongListFromConfig("groups").forEach {
                BotProvider.getBot()?.sendMsg(true, it,
                    plugin.messages.server2ob
                        .replace("%server%", Bukkit.getServer().name)
                        .replace("%player_name%", event.player.name)
                        .replace("%message%", message.replace(plugin.config.getStringFromConfig("feature", "message", "prefix")!!, ""))
                    , plugin.config.getBooleanFromConfig("feature", "message", "pic"))
            }
            return
        }
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        val lock = Object()
        val name = event.name

//        if (FloodgateHook.hasFloodgate) {
//            if (FloodgateHook.floodgateApi!!.isFloodgatePlayer(event.uniqueId)) {
//                if (FloodgateHook.floodgateApi!!.getPlayer(event.uniqueId).isLinked) {
//                    name = FloodgateHook.floodgateApi!!.getPlayer(event.uniqueId).linkedPlayer.javaUsername
//                }
//            }
//        }

        if (plugin.config.getBooleanFromConfig("feature", "bind", "whitelist")) {
            plugin.submitAsync {
                synchronized(lock) {
                    val qq = (DatabaseProvider.getBindByName(name))
                    if (qq.isNullOrEmpty()) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                            getLegacyFromComponent(getComponentFromMiniMsg(
                                plugin.messages.kickServer
                                    .replace("%groups%", plugin.config.getLongListFromConfig("groups").toString())
                            ))
                        )
                        plugin.config.getLongListFromConfig("groups").forEach {
                            BotProvider.getBot()?.sendMsg(
                                true,
                                it,
                                plugin.messages.kickPlatform
                                    .replace("%player_name%", name),
                                plugin.config.getBooleanFromConfig("feature", "bind", "pic")
                            )
                        }
                        lock.notifyAll()
                        return@submitAsync
                    }
                    if (!plugin.config.getBooleanFromConfig("feature", "joinAndLeave", "joinProxy")) {
                        lock.notifyAll()
                        return@submitAsync
                    }
//                    var isInGroup = false
//                    val grouplock = Object()
//                    plugin.config!!.getLongListFromConfig("groups").forEach {
//                        if (plugin.bot!!.checkUserInGroup(qq, it, plugin.config!!.getLongListFromConfig("groups").last() == it, grouplock)) {
//                            isInGroup = true
//                        }
//                    }
//                    synchronized(grouplock) {
//                        grouplock.wait()
//                        if (isInGroup) {
//                            event.result = PreLoginEvent.PreLoginComponentResult.allowed()
//                            plugin.config!!.getLongListFromConfig("groups").forEach {
//                                plugin.bot!!.sendMsg(
//                                    true,
//                                    it,
//                                    plugin.messages.joinProxy
//                                        .replace("%player_name%", name),
//                                    plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "pic")
//                                )
//                            }
//                            lock.notifyAll()
//                            return@runTask
//                        } else {
//                            event.result = PreLoginEvent.PreLoginComponentResult.denied(
//                                getComponentFromMiniMsg(
//                                    plugin.messages.kickServer
//                                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
//                                )
//                            )
//                            plugin.config!!.getLongListFromConfig("groups").forEach {
//                                plugin.bot!!.sendMsg(
//                                    true,
//                                    it,
//                                    plugin.messages.kickPlatform
//                                        .replace("%player_name%", name),
//                                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
//                                )
//                            }
//                            lock.notifyAll()
//                            return@runTask
//                        }
//                    }
                    event.allow()
                    plugin.config.getLongListFromConfig("groups").forEach {
                        BotProvider.getBot()?.sendMsg(
                            true,
                            it,
                            plugin.messages.joinProxy
                                .replace("%player_name%", name),
                            plugin.config.getBooleanFromConfig("feature", "joinAndLeave", "pic")
                        )
                    }
                    lock.notifyAll()
                    return@submitAsync
                }
            }
            synchronized(lock){
                lock.wait()
            }
        } else {
            if (plugin.config.getBooleanFromConfig("feature", "joinAndLeave", "joinProxy")) {
                plugin.config.getLongListFromConfig("groups").forEach {
                    BotProvider.getBot()?.sendMsg(true, it,
                        plugin.messages.joinProxy
                            .replace("%player_name%", name)
                        , plugin.config.getBooleanFromConfig("feature", "joinAndLeave", "pic"))
                }
            }
        }
//        var dataName = name
//        if (FloodgateHook.hasFloodgate) {
//            if (FloodgateHook.floodgateApi!!.isFloodgatePlayer(event.uniqueId)) {
//                dataName = if (FloodgateHook.floodgateApi!!.getPlayer(event.uniqueId).isLinked) {
//                    FloodgateHook.floodgateApi!!.getPlayer(event.uniqueId).linkedPlayer.javaUsername
//                } else {
//                    FloodgateHook.floodgateApi!!.getPlayer(event.uniqueId).javaUsername
//                }
//            }
//        }
//        plugin.database!!.setUUID(dataName, event.uniqueId!!)
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        plugin.submitAsync {
            if (plugin.config.getBooleanFromConfig("feature", "joinAndLeave", "leaveProxy")) {
                plugin.config.getLongListFromConfig("groups").forEach {
                    BotProvider.getBot()?.sendMsg(
                        true,
                        it,
                        plugin.messages.leaveProxy
                            .replace("%player_name%", event.player.name)
                            .replace("%server%", Bukkit.getServer().name),
                        plugin.config.getBooleanFromConfig("feature", "joinAndLeave", "pic")
                    )
                }
            }
        }
    }

}