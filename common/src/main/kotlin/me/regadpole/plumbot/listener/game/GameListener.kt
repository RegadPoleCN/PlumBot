package me.regadpole.plumbot.listener.game

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.event.GDeathEvent
import me.regadpole.plumbot.event.GJoinEvent
import me.regadpole.plumbot.event.GPlayerChatEvent
import me.regadpole.plumbot.event.GQuitEvent

object GameListener {
    fun onChat(event: GPlayerChatEvent) {
        if (PlumBot.getConfig().getConfig().groups.forwarding.message.enable) {
            if (PlumBot.getConfig().getConfig().groups.forwarding.message.mode == 1 && !event.message.startsWith(
                PlumBot.getConfig().getConfig().groups.forwarding.message.prefix?: "")) {
                return
            }
            var message: String = PlumBot.getLangConfig().getLangConf().forwarding.toPlatform?: return
            message = message.replace("%server_name%", event.player.server)
                .replace("%world_name%", event.player.position.world)
                .replace("%message%", event.message)
            PlumBot.getConfig().getConfig().groups.enableGroups.forEach {
                PlumBot.getBot().sendGroupMsg(it, message)
            }
        }
    }

    fun onDie(event: GDeathEvent) {
        if (PlumBot.getConfig().getConfig().groups.forwarding.dieMessage) {
            var message: String = PlumBot.getLangConfig().getLangConf().messageOnDie?: return
            message = message.replace("%player_name%", event.player.name)
                .replace("%world_name%", event.player.position.world)
                .replace("%pos%", event.player.position.toXYZ("/"))
            PlumBot.getConfig().getConfig().groups.enableGroups.forEach {
                PlumBot.getBot().sendGroupMsg(it, message)
            }
        }
    }

    fun onJoin(event: GJoinEvent) {
        if (PlumBot.getDatabase().getBindByName(event.player.name) == null) {
            var message: String = PlumBot.getLangConfig().getLangConf().whitelist.kickServer?: return
            message = message.replace("%enable_groups%", PlumBot.getConfig().getConfig().groups.enableGroups.joinToString("/"))
            event.kick(message)
            message = PlumBot.getLangConfig().getLangConf().whitelist.kickPlatform?: return
            message = message.replace("%player_name%", event.player.name)
                .replace("%server_name%", event.player.server)
            PlumBot.getConfig().getConfig().groups.enableGroups.forEach {
                PlumBot.getBot().sendGroupMsg(it, message)
            }
        } else if (PlumBot.getConfig().getConfig().groups.forwarding.joinAndLeave) {
            var message: String = PlumBot.getLangConfig().getLangConf().playerJoinMsg?: return
            message = message.replace("%player_name%", event.player.name)
                .replace("%server_name%", event.player.server)
            PlumBot.getConfig().getConfig().groups.enableGroups.forEach {
                PlumBot.getBot().sendGroupMsg(it, message)
            }
        }
    }

    fun onQuit(event: GQuitEvent) {
        if (PlumBot.getConfig().getConfig().groups.forwarding.joinAndLeave) {
            var message: String = PlumBot.getLangConfig().getLangConf().playerLeaveMsg?: return
            message = message.replace("%player_name%", event.player.name)
                .replace("%server_name%", event.player.server)
            PlumBot.getConfig().getConfig().groups.enableGroups.forEach {
                PlumBot.getBot().sendGroupMsg(it, message)
            }
        }
    }
}