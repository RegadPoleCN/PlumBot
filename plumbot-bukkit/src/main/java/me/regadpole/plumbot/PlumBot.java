package me.regadpole.plumbot;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import me.regadpole.plumbot.command.Commands;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.event.qq.QQEvent;
import me.regadpole.plumbot.event.server.QsChatEvent;
import me.regadpole.plumbot.event.server.QsHikariChatEvent;
import me.regadpole.plumbot.event.server.ServerEvent;
import me.regadpole.plumbot.hook.AuthMeHook;
import me.regadpole.plumbot.hook.GriefDefenderHook;
import me.regadpole.plumbot.hook.QuickShopHook;
import me.regadpole.plumbot.hook.ResidenceHook;
import me.regadpole.plumbot.internal.Dependencies;
import me.regadpole.plumbot.internal.database.Database;
import me.regadpole.plumbot.internal.database.MySQL;
import me.regadpole.plumbot.internal.database.SQLite;
import me.regadpole.plumbot.internal.maven.LibraryLoader;
import me.regadpole.plumbot.metrics.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import me.regadpole.plumbot.bot.Bot;
import me.regadpole.plumbot.config.Config;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.config.CQConfig;
import sdk.connection.Connection;
import sdk.connection.ConnectionFactory;
import sdk.event.EventDispatchers;
import sdk.event.notice.GroupDecreaseNotice;
import sdk.listener.SimpleListener;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public final class PlumBot extends JavaPlugin implements Listener{

    public static PlumBot INSTANCE;

    private static TaskScheduler scheduler;

    private static CQConfig http_config;

    private static Bot bot;
    private static QQEvent qqEvent;

    private static Database database;

    @Override
    public void onLoad() {
        INSTANCE = this;

        Config.createConfig();

        LibraryLoader.loadAll(Dependencies.class);
        getLogger().info("PlumBot依赖已加载成功");
    }

    @Override
    public void onEnable() {

        try {
            switch (DataBase.type().toLowerCase()){
                case "sqlite":
                default: {
                    getLogger().info("Initializing SQLite database.");
                    database = (new SQLite());
                    break;
                }
                case "mysql": {
                    getLogger().info("Initializing MySQL database.");
                    database = (new MySQL());
                    break;
                }
            }
            database.initialize();
        } catch (ClassNotFoundException e) {
            getLogger().warning("Failed to initialize database, reason: " + e);
        }

        scheduler = UniversalScheduler.getScheduler(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        AuthMeHook.hookAuthme();
        ResidenceHook.hookRes();
        QuickShopHook.hookQuickShop();
        GriefDefenderHook.hookGriefDefender();
        getLogger().info("关联插件连接完毕");
        Bukkit.getPluginManager().registerEvents(new ServerEvent(), this);
        if (QuickShopHook.hasQs) Bukkit.getPluginManager().registerEvents(new QsChatEvent(),this);
        if (QuickShopHook.hasQsHikari) Bukkit.getPluginManager().registerEvents(new QsHikariChatEvent(),this);
        getLogger().info("服务器事件监听器注册完毕");
//        Bukkit.getPluginManager().registerEvents(new QQEvent(), this);
        qqEvent = new QQEvent();
        getLogger().info("QQ事件监听器注册完毕");
        Bukkit.getServer().getPluginCommand("plumbot").setExecutor(new Commands());
        getLogger().info("命令注册完毕");

        // All you have to do is adding the following two lines in your onEnable method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 19427; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info( "PlumBot已启动");

        http_config = new CQConfig(Config.getBotHttp(), Config.getBotToken(), Config.getBotIsAccessToken());
        bot = new Bot();
        LinkedBlockingQueue<String> blockingQueue = new LinkedBlockingQueue();//使用队列传输数据
        Connection connection = null;
        try {
            connection = ConnectionFactory.createHttpServer(Config.getBotListenPort(),"/",blockingQueue);
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
        dispatchers.addListener(new SimpleListener<GroupDecreaseNotice>() {//群聊人数减少监听
            @Override
            public void onMessage(GroupDecreaseNotice groupDecreaseNotice) {
                qqEvent.onGroupDecreaseNotice(groupDecreaseNotice);
            }
        });
        dispatchers.start(10);//线程组处理任务

        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            PlumBot.getBot().sendMsg(true, "PlumBot已启动", groupID);
        }
    }

    @Override
    public void onDisable() {

        getLogger().info("Closing database.");
        try {
            database.close();
        } catch (SQLException e) {
            getLogger().info("在关闭数据库时出现错误"+e);
        }

        getLogger().info("PlumBot已关闭");
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            bot.sendMsg(true, "PlumBot已关闭", groupID);
        }
    }

    public static void say(String s) {
        CommandSender sender = Bukkit.getConsoleSender();
        sender.sendMessage(s);
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public static Bot getBot() {
        return bot;
    }

    public static CQConfig getHttp_config() {
        return http_config;
    }

    public static Database getDatabase() {
        return database;
    }

}


