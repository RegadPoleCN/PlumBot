package me.regadpole.plumbot.bukkit

import me.regadpole.plumbot.PlatformImpl
import me.regadpole.plumbot.bukkit.hook.AuthMeHook
import me.regadpole.plumbot.bukkit.hook.GriefDefenderHook
import me.regadpole.plumbot.bukkit.hook.QuickShopHook
import me.regadpole.plumbot.bukkit.hook.ResidenceHook
import me.regadpole.plumbot.bukkit.listener.GDClaimEvent
import me.regadpole.plumbot.bukkit.listener.QsChatEvent
import me.regadpole.plumbot.bukkit.listener.QsHikariChatEvent
import org.bukkit.Bukkit
import taboolib.common.platform.function.info

class BukkitImpl: PlatformImpl() {
    override fun load() {
        AuthMeHook.hookAuthme()
        ResidenceHook.hookRes()
        QuickShopHook.hookQuickShop()
        GriefDefenderHook.hookGriefDefender()
        info("关联插件连接完毕")
        if (QuickShopHook.hasQs) Bukkit.getPluginManager().registerEvents(QsChatEvent(), Bukkit.getPluginManager().getPlugin("PlumBot")!!)
        if (QuickShopHook.hasQsHikari) Bukkit.getPluginManager().registerEvents(QsHikariChatEvent(), Bukkit.getPluginManager().getPlugin("PlumBot")!!)
        if (GriefDefenderHook.hasGriefDefender) Bukkit.getPluginManager().registerEvents(GDClaimEvent(), Bukkit.getPluginManager().getPlugin("PlumBot")!!)
    }

    override fun getPlayerList(): List<String> {
        TODO("Not yet implemented")
    }

    override fun getTPS(): List<Double> {
        TODO("Not yet implemented")
    }

    override fun dispatchCommand(cmd: String): String {
        TODO("Not yet implemented")
    }
}