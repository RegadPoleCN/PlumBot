package org.linear.linearbot.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.linear.linearbot.LinearBot;

public class QuickShopHook {

    public static Boolean hasQs;

    public static Boolean hasQsHikari;

    public static void hookQuickShop() {

        Plugin quickShop = Bukkit.getPluginManager().getPlugin("QuickShop");
        try {
            if (quickShop != null) {
                hasQs = true;
                hasQsHikari = false;
                LinearBot.INSTANCE.getLogger().info("QuickShop-Reremake 关联成功");
            }else{
                hasQs = false;
                Plugin quickShopHikari = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
                try {
                    if (quickShopHikari != null) {
                        hasQsHikari = true;
                        LinearBot.INSTANCE.getLogger().info("QuickShop-Hikari 关联成功");
                    }else{
                        hasQsHikari = false;
                        LinearBot.INSTANCE.getLogger().info("QuickShop 关联失败");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
