package me.regadpole.plumbot.listener

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.runTask


class ServerListener(private val plugin: PlumBot) {

    fun onChat(event: PlayerChatEvent) {
        event.sender
        if (event.isCancelled) return
        if (!plugin.config!!.getBooleanFromConfig("feature", "message", "enable")) return

        val message = event.content.replace(Regex("&([0-9a-fklmnor])")) { matchResult ->
            "§" + matchResult.groupValues[1]
        }.replace(Regex("#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})"), "")
        if (plugin.config!!.getIntegerFromConfig("feature", "message", "mode") == 0) {
//          # server, player_name, message
            plugin.config!!.getLongListFromConfig("groups").forEach {
                plugin.bot!!.sendMsg(true, it,
                    plugin.messages.server2ob
                    .replace("%server%", )
                    .replace("%player_name%", event.sender.username)
                    .replace("%message%", message), plugin.config!!.getBooleanFromConfig("feature", "message", "pic"))
            }
            return
        } else if (plugin.config!!.getIntegerFromConfig("feature", "message", "mode") == 1 &&
            Regex(plugin.config!!.getStringFromConfig("feature", "message", "prefix")!!).matchesAt(event.content, 0)
        ) {
//          # server, player_name, message
            plugin.config!!.getLongListFromConfig("groups").forEach {
                plugin.bot!!.sendMsg(true, it,
                    plugin.messages.server2ob
                        .replace("%server%", event.player.currentServer.get().serverInfo.name)
                        .replace("%player_name%", event.sender.username)
                        .replace("%message%", message.replace(plugin.config!!.getStringFromConfig("feature", "message", "prefix")!!, ""))
                    , plugin.config!!.getBooleanFromConfig("feature", "message", "pic"))
            }
            return
        }
    }

    @Subscribe
    fun onPreLogin(event: PreLoginEvent) {
        if (!event.result.isAllowed) return
        val lock = Object()
        val name = event.username

        if (plugin.config!!.getBooleanFromConfig("feature", "bind", "whitelist")) {
            runTask {
                synchronized(lock) {
                    val qq = (plugin.database!!.getBind(name))
                    if (qq == 0L || qq == null) {
                        event.result = PreLoginEvent.PreLoginComponentResult.denied(
                            getComponentFromMiniMsg(
                                plugin.messages.kickServer
                                    .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
                            )
                        )
                        plugin.config!!.getLongListFromConfig("groups").forEach {
                            plugin.bot!!.sendMsg(
                                true,
                                it,
                                plugin.messages.kickPlatform
                                    .replace("%player_name%", name),
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                        }
                        lock.notifyAll()
                        return@runTask
                    }
                    if (!plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "joinServer")) {
                        lock.notifyAll()
                        return@runTask
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
//                                    plugin.messages.joinServer
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
                    event.result = PreLoginEvent.PreLoginComponentResult.allowed()
                    plugin.config!!.getLongListFromConfig("groups").forEach {
                        plugin.bot!!.sendMsg(
                            true,
                            it,
                            plugin.messages.joinServer
                                .replace("%player_name%", name),
                            plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "pic")
                        )
                    }
                    lock.notifyAll()
                    return@runTask
                }
            }
            synchronized(lock){
                lock.wait()
            }
        } else {
            if (plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "joinServer")) {
                plugin.config!!.getLongListFromConfig("groups").forEach {
                    plugin.bot!!.sendMsg(true, it,
                        plugin.messages.joinServer
                            .replace("%player_name%", name)
                        , plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "pic"))
                }
            }
        }
        runTask {
            plugin.database!!.setUUID(name, event.uniqueId!!)
        }
    }

    @Subscribe
    fun onLeave(event: DisconnectEvent) {
        if (event.loginStatus != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) return
        if (plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "leaveServer")) {
            plugin.config!!.getLongListFromConfig("groups").forEach {
                plugin.bot!!.sendMsg(true, it,
                    plugin.messages.leaveServer
                        .replace("%player_name%", event.player.username)
                        .replace("%server%", event.player.currentServer.get().serverInfo.name)
                    , plugin.config!!.getBooleanFromConfig("feature", "joinAndLeave", "pic"))
            }
        }
    }

}