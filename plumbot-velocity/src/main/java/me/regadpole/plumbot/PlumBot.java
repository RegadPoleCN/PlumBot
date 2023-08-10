package me.regadpole.plumbot;

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
import me.regadpole.plumbot.event.qq.QQEvent;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.command.Commands;
import me.regadpole.plumbot.config.VelocityConfig;
import me.regadpole.plumbot.event.server.ServerEvent;
import me.regadpole.plumbot.internal.Dependencies;
import me.regadpole.plumbot.internal.maven.LibraryLoader;
import me.regadpole.plumbot.metrics.Metrics;
import org.slf4j.Logger;
import sdk.config.CQConfig;
import sdk.connection.Connection;
import sdk.connection.ConnectionFactory;
import sdk.event.EventDispatchers;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.listener.SimpleListener;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Plugin(id = "plumbot", name = "PlumBot", version = "1.2.1",
        url = "https://github.com/RegadPoleCN/PlumBot", description = "A plugin for Minecraft!", authors = {"Linear,RegadPole"})
public class PlumBot {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private PluginContainer pluginContainer;
    private final Metrics.Factory metricsFactory;
    public VelocityConfig vconf;

    private static CQConfig http_config;

    private static Bot bot;
    private static QQEvent qqEvent;

    public static PlumBot INSTANCE;

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

        server.getEventManager().register(this, new ServerEvent());
        logger.info("服务器事件监听器注册成功");
        qqEvent = new QQEvent(this);
        logger.info("QQ事件监听器注册成功");
        CommandManager manager = server.getCommandManager();
        CommandMeta linearbot = manager.metaBuilder("plumbot").aliases("pb", "PlumBot").build();
        manager.register(linearbot, new Commands(this));
        logger.info("插件命令监听器注册成功");

        // All you have to do is adding the following two lines in your onProxyInitialization method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 19428;
        Metrics metrics = metricsFactory.make(this, pluginId);
        metrics.addCustomChart(new Metrics.SimplePie("chart_id", () -> "value"));
        logger.info("PlumBot 已启动");


        http_config = new CQConfig(Config.bot.Bot.HTTP, Config.bot.Bot.Token, Config.bot.Bot.IsAccessToken);
        bot = new Bot();
        LinkedBlockingQueue<String> blockingQueue = new LinkedBlockingQueue();//使用队列传输数据
        Connection connection = null;
        try {
            connection = ConnectionFactory.createHttpServer(Config.bot.Bot.ListenPort,"/",blockingQueue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        connection.create();
        EventDispatchers dispatchers = new EventDispatchers(blockingQueue);//创建事件分发器
        dispatchers.addListener(new SimpleListener<PrivateMessage>() {//私聊监听
            @Override
            public void onMessage(PrivateMessage privateMessage) {
                qqEvent.onFriendMessageReceive(privateMessage);
            }
        });
        dispatchers.addListener(new SimpleListener<GroupMessage>() {//群聊消息监听
            @Override
            public void onMessage(GroupMessage groupMessage) {
                qqEvent.onGroupMessageReceive(groupMessage);
            }
        });
        dispatchers.start(10);//线程组处理任务

        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups) {
            PlumBot.getBot().sendGroupMsg("PlumBot已启动", groupID);
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onProxyShutdown(ProxyShutdownEvent event) {
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups) {
            bot.sendMsg(true, "PlumBot已关闭", groupID);
        }

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

    public static CQConfig getHttp_config() {
        return http_config;
    }
}