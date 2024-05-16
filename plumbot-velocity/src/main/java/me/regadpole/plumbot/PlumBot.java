package me.regadpole.plumbot;

import com.alibaba.fastjson.JSONArray;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.regadpole.plumbot.bot.Bot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.command.Commands;
import me.regadpole.plumbot.config.VelocityConfig;
import me.regadpole.plumbot.event.server.ServerEvent;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.Dependencies;
import me.regadpole.plumbot.internal.Environment;
import me.regadpole.plumbot.internal.database.Database;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.internal.database.MySQL;
import me.regadpole.plumbot.internal.database.SQLite;
import me.regadpole.plumbot.internal.maven.LibraryLoader;
import me.regadpole.plumbot.metrics.Metrics;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

@Plugin(id = "plumbot", name = "PlumBot", version = "1.3.2",
        url = "https://github.com/RegadPoleCN/PlumBot", description = "A bot plugin for QQ or Kook.", authors = {"Linear,RegadPole"})
public class PlumBot {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private PluginContainer pluginContainer;
    private final Metrics.Factory metricsFactory;
    public VelocityConfig vconf;
    private static Bot bot;
    private static Environment environment;

    public static PlumBot INSTANCE;
    private static Database database;

    @Inject
    public PlumBot(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;

        INSTANCE = this;

        // Do some operation demanding access to the Velocity API here.
        // For instance, we could register an event:
        try {
            vconf = new VelocityConfig(this);
            vconf.loadConfig();
            logger.info("配置文件获取成功");
        }catch (Exception e) {
            getLogger().warn("An error occurred while loading plugin.");
            e.printStackTrace();
        }

        LibraryLoader.loadAll(Dependencies.class);
        logger.info("PlumBot依赖已加载完成");

        logger.info("It's a plugin for Minecraft!");
    }



    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        getServer().getScheduler().buildTask(this, () -> {
            try {
                File botFile = new File(getDataFolder(), "bot.yml");
                InputStream botIs = new FileInputStream(botFile);
                Yaml yaml = new Yaml();
                Map<String, Object> botObj = yaml.load(botIs);
                Config.bot.Groups = !Objects.isNull(botObj.get("Groups")) ? JSONArray.parseArray(botObj.get("Groups").toString(), Long.class) : new ArrayList<>();
                Config.bot.Admins = !Objects.isNull(botObj.get("Admins")) ? JSONArray.parseArray(botObj.get("Admins").toString(), Long.class) : new ArrayList<>();
                botIs.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).schedule();

        DatabaseManager.start();
        server.getEventManager().register(this, new ServerEvent());
        logger.info("服务器事件监听器注册成功");
        CommandManager manager = server.getCommandManager();
        CommandMeta linearbot = manager.metaBuilder("plumbot").aliases("pb", "PlumBot").build();
        manager.register(linearbot, new Commands(this));
        logger.info("插件命令监听器注册成功");

        getServer().getScheduler().buildTask(this, () -> {
            switch (Config.bot.Bot.Mode) {
                case "go-cqhttp":
                    bot = new QQBot(INSTANCE);
                    bot.start();
                    getLogger().info("已启动go-cqhttp服务");
                    break;
                case "kook":
                    bot = new KookBot(this);
                    bot.start();
                    KookBot.setKookEnabled(true);
                    getLogger().info("已启动kook服务");
                    break;
                default:
                    getLogger().warn("无法启动服务，请检查配置文件");
                    break;
            }
        }).schedule();

        // All you have to do is adding the following two lines in your onProxyInitialization method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 19428;
        Metrics metrics = metricsFactory.make(this, pluginId);
        metrics.addCustomChart(new Metrics.SimplePie("chart_id", () -> "value"));

        environment = new Environment();
        logger.info("PlumBot 已启动");


    }

    @Subscribe(order = PostOrder.FIRST)
    public void onProxyShutdown(ProxyShutdownEvent event) {

        switch (Config.bot.Bot.Mode) {
            case "go-cqhttp":
                bot.shutdown();
                getLogger().info("已关闭go-cqhttp服务");
                break;
            case "kook":
                bot.shutdown();
                getLogger().info("已关闭kook服务");
                break;
            default:
                getLogger().warn("无法正常关闭服务，将在服务器关闭后强制关闭");
                break;
        }
        DatabaseManager.close();
        getLogger().info("PlumBot已关闭");
    }

    public Logger getLogger() {
        return logger;
    }

    public File getDataFolder() {
        return dataDirectory.toFile();
    }
    public ProxyServer getServer() {
        return server;
    }

    public PluginContainer getPluginContainer(){
        return pluginContainer;
    }

    public static Bot getBot() {
        return bot;
    }

    public static Database getDatabase() {
        return database;
    }
    public void setDatabase(Database database) {
        PlumBot.database = database;
    }
    public Environment getEnvironment() {return environment;}
}