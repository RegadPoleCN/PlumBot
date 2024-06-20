package me.regadpole.plumbot.hook;

import me.regadpole.plumbot.PlumBot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class GriefDefenderHook {


    public static Boolean hasGriefDefender;

    public static void hookGriefDefender() {

        Plugin authMe = Bukkit.getPluginManager().getPlugin("GriefDefender");
        try {
            if (authMe != null) {
                hasGriefDefender = true;
                PlumBot.INSTANCE.getLogger().info("GriefDefender 关联成功");
            }else{
                hasGriefDefender = false;
                PlumBot.INSTANCE.getLogger().info("GriefDefender 关联失败");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
