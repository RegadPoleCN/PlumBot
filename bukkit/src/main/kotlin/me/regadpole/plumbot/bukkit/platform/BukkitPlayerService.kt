package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.platform.PlatformPlayerService
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import org.bukkit.Server

private const val PLAYERS_PER_LINE = 5

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
        val playersPerLine = PLAYERS_PER_LINE
        var list = server.onlinePlayers.map { it.name }.sorted().toList()
        var result = ""
        while(list.size > playersPerLine) {
            result += list.slice(0..<playersPerLine).joinToString(postfix = "\n  ")
            list = list.drop(playersPerLine)
        }
        result += list.joinToString()
        result += "\n"
        return result
    }
}
