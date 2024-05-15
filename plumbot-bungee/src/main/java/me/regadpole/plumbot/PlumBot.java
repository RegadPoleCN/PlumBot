package me.regadpole.plumbot;

import me.regadpole.plumbot.bot.Bot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.command.Commands;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.event.server.ServerEvent;
import me.regadpole.plumbot.internal.Dependencies;
import me.regadpole.plumbot.internal.Environment;
import me.regadpole.plumbot.internal.database.Database;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.internal.maven.LibraryLoader;
import me.regadpole.plumbot.metrics.Metrics;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.IOException;

public final class PlumBot extends Plugin {

    public static PlumBot INSTANCE;

    private static Database database;
    private static Bot bot;
    private static Environment environment;

    @Override
    public void onLoad() {
        INSTANCE = this;

        try {
            Config.createConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }

        LibraryLoader.loadAll(Dependencies.class);
        getLogger().info("PlumBot依赖已加载成功");
    }

    @Override
    public void onEnable() {

        DatabaseManager.start();
        getProxy().getPluginManager().registerListener(this, new ServerEvent(this));
        getLogger().info("服务器事件监听器注册完毕");
        getProxy().getPluginManager().registerCommand(this, new Commands(this));
        getLogger().info("命令注册完毕");

        getProxy().getScheduler().runAsync(this, () -> {
            switch (Config.getBotMode()) {
                case "go-cqhttp":
                    bot = new QQBot(this);
                    bot.start();
                    getLogger().info("已启动go-cqhttp服务");
                    break;
                case "kook":
                    bot = new KookBot();
                    bot.start();
                    KookBot.setKookEnabled(true);
                    getLogger().info("已启动kook服务");
                    break;
                default:
                    getLogger().warning("无法启动服务，请检查配置文件，插件已关闭");
                    getProxy().getPluginManager().getPlugin("PlumBot").onDisable();
                    break;
            }
        });

        // All you have to do is adding the following two lines in your onEnable method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 21652; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        environment = new Environment();
        getLogger().info( "PlumBot已启动");

    }

    @Override
    public void onDisable() {
        switch (Config.getBotMode()) {
            case "go-cqhttp":
                bot.shutdown();
                getLogger().info("已关闭go-cqhttp服务");
                break;
            case "kook":
                bot.shutdown();
                getLogger().info("已关闭kook服务");
                break;
            default:
                getLogger().warning("无法正常关闭服务，将在服务器关闭后强制关闭");
                getProxy().getPluginManager().getPlugin("PlumBot").onDisable();
                break;
        }
        DatabaseManager.close();
        getLogger().info("PlumBot已关闭");
    }

    public static void say(String s) {
        CommandSender sender = INSTANCE.getProxy().getConsole();
        sender.sendMessage(s);
    }

    public static Bot getBot() {
        return bot;
    }
    public static Database getDatabase() {
        return database;
    }
    public void setDatabase(Database database) {
        PlumBot.database =database;
    }
    public Environment getEnvironment() {return environment;}
}
