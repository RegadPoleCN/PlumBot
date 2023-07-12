package org.linear.linearbot;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.linear.linearbot.bot.Bot;
import org.linear.linearbot.command.Commands;
import org.linear.linearbot.config.VelocityConfig;
import org.linear.linearbot.event.qq.QQEvent;
import org.linear.linearbot.event.server.ServerEvent;
import org.linear.linearbot.internal.Config;
import org.linear.linearbot.metrics.Metrics;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(id = "linearbot", name = "LinearBot", version = "1.2.1",
        url = "https://github.com/LinearBit/LinearBot", description = "A plugin for MiraiMC!", authors = {"Linear,RegadPole"}, dependencies = {@Dependency(id = "miraimc")})
public class LinearBot {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private PluginContainer pluginContainer;
    private final Metrics.Factory metricsFactory;
    public VelocityConfig vconf;

    @Inject
    public LinearBot(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;

        logger.info("It's a plugin for MiraiMC!");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
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

        server.getEventManager().register(this, new ServerEvent());
        logger.info("服务器事件监听器注册成功");
        server.getEventManager().register(this, new QQEvent(this));
        logger.info("QQ事件监听器注册成功");
        CommandManager manager = server.getCommandManager();
        CommandMeta linearbot = manager.metaBuilder("linearbot").aliases("lb", "LinearBot").build();
        manager.register(linearbot, new Commands(this));
        logger.info("插件命令监听器注册成功");

        // All you have to do is adding the following two lines in your onProxyInitialization method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 17478;
        Metrics metrics = metricsFactory.make(this, pluginId);
        metrics.addCustomChart(new Metrics.SimplePie("chart_id", () -> "value"));
        logger.info("LinearBot 已启动");

        runAfterDone();
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups) {
            Bot.sendMsg("LinearBot已关闭", groupID);
        }

        getLogger().info("LinearBot已关闭");
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

    public void runAfterDone() {
        this.getServer().getScheduler().buildTask(this, () -> {
            for (long groupID : Config.bot.Groups) {
                Bot.sendMsg("LinearBot已启动", groupID);
            }
        }).delay(10L, TimeUnit.SECONDS).schedule();

    }
}