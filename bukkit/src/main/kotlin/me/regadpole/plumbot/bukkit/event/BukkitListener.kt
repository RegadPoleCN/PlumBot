package me.regadpole.plumbot.bukkit.event

import me.regadpole.plumbot.bukkit.event.impl.BukkitJoinEvent
import me.regadpole.plumbot.bukkit.hook.AuthMeHook
import me.regadpole.plumbot.bukkit.hook.GriefDefenderHook
import me.regadpole.plumbot.bukkit.hook.QuickShopHook
import me.regadpole.plumbot.bukkit.hook.ResidenceHook
import me.regadpole.plumbot.bukkit.listener.GDClaimEvent
import me.regadpole.plumbot.bukkit.listener.QsChatEvent
import me.regadpole.plumbot.bukkit.listener.QsHikariChatEvent
import me.regadpole.plumbot.bukkit.util.impl.BukkitPlayer
import me.regadpole.plumbot.event.GDeathEvent
import me.regadpole.plumbot.event.GPlayerChatEvent
import me.regadpole.plumbot.event.GQuitEvent
import me.regadpole.plumbot.listener.game.GameListener
import me.regadpole.plumbot.tool.StringTool
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

class BukkitListener {
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        GameListener.onJoin(BukkitJoinEvent(event.player))
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        GameListener.onQuit(GQuitEvent(BukkitPlayer(event.player)))
    }

    @SubscribeEvent
    fun onChat(event: AsyncPlayerChatEvent) {
        val name: String = StringTool.filterColor(event.player.displayName)
        val message: String = StringTool.filterColor(event.message)
        if (AuthMeHook.hasAuthMe) {
            if (!AuthMeHook.authMeApi.isAuthenticated(event.player)) {
                return
            }
        }
        if (ResidenceHook.hasRes) {
            if (ResidenceHook.resChatApi.getPlayerChannel(event.player.name) != null) {
                return
            }
        }
        if (QuickShopHook.hasQs) {
            if (event.player === QsChatEvent.getQsSender() && event.message === QsChatEvent.getQsMessage()) {
                return
            }
        }
        if (QuickShopHook.hasQsHikari) {
            if (event.player === QsHikariChatEvent.getQsSender() && event.message === QsHikariChatEvent.getQsMessage()) {
                return
            }
        }
        if (GriefDefenderHook.hasGriefDefender) {
            if (GDClaimEvent.getGDMessage() === event.message) {
                return
            }
        }
        GameListener.onChat(GPlayerChatEvent(event.message, BukkitPlayer(event.player)))
    }

    @SubscribeEvent
    fun onDeath(event: PlayerDeathEvent) {
        GameListener.onDie(GDeathEvent(BukkitPlayer(event.entity)))
    }
}