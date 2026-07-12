package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.platform.PlatformPlayerService
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import org.bukkit.Server

class BukkitPlayerService(
    private val server: Server,
    private val configProvider: () -> YamlConfigurator,
): PlatformPlayerService {
    override fun kickPlayer(name: String) {
        val kickMessage = getLegacyFromComponent(getComponentFromMiniMsg(
            Messages.kickServer
                .replace("%groups%", configProvider().getLongList("groups").toString())
        ))
        server.getPlayer(name)?.kickPlayer(kickMessage)
    }

    override fun listPlayers(): List<String> {
        return server.onlinePlayers
            .map { it.name }
            .sorted()
            .toList()
    }

    override fun listPlayerString(): String {
        val newLine = 5
        var list = server.onlinePlayers.map { it.name }.sorted().toList()
        var result = ""
        while(list.size > newLine) {
            result += list.slice(0..<newLine).joinToString(postfix = "\n  ")
            list = list.drop(newLine)
        }
        result += list.joinToString()
        result += "\n"
        return result
    }
}
