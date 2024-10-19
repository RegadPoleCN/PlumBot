package me.regadpole.plumbot.bukkit.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import taboolib.common.platform.function.IOKt;

public class GriefDefenderHook {


    public static Boolean hasGriefDefender;

    public static void hookGriefDefender() {

        Plugin authMe = Bukkit.getPluginManager().getPlugin("GriefDefender");
        try {
            if (authMe != null) {
                hasGriefDefender = true;
                IOKt.info("GriefDefender 关联成功");
            }else{
                hasGriefDefender = false;
                IOKt.info("GriefDefender 关联失败");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
