package org.linear.linearbot.hook;

import com.griefdefender.api.Core;
import com.griefdefender.api.GriefDefender;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.linear.linearbot.LinearBot;

public class GriefDefenderHook {


    public static Boolean hasGriefDefender;

    public static void hookGriefDefender() {

        Plugin authMe = Bukkit.getPluginManager().getPlugin("GriefDefender");
        try {
            if (authMe != null) {
                hasGriefDefender = true;
                LinearBot.INSTANCE.getLogger().info("GriefDefender 关联成功");
            }else{
                hasGriefDefender = false;
                LinearBot.INSTANCE.getLogger().info("GriefDefender 关联失败");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
