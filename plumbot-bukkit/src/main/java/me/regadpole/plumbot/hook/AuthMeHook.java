package me.regadpole.plumbot.hook;

import fr.xephi.authme.api.v3.AuthMeApi;
import me.regadpole.plumbot.PlumBot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class AuthMeHook {

    public static Boolean hasAuthMe;
    public static AuthMeApi authMeApi;

    public static void hookAuthme() {

        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        try {
            if (authMe != null) {
                hasAuthMe = true;
                authMeApi = AuthMeApi.getInstance();
                PlumBot.INSTANCE.getLogger().info("AuthMe 关联成功");
            }else{
                hasAuthMe = false;
                PlumBot.INSTANCE.getLogger().info("AuthMe 关联失败");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
