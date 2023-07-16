package org.linear.linearbot;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.linear.linearbot.bot.Bot;
import org.linear.linearbot.command.Commands;
import org.linear.linearbot.config.Config;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import org.linear.linearbot.event.qq.QQEvent;
import org.linear.linearbot.event.server.QsChatEvent;
import org.linear.linearbot.event.server.ServerEvent;
import org.linear.linearbot.hook.AuthMeHook;
import org.linear.linearbot.hook.GriefDefenderHook;
import org.linear.linearbot.hook.QuickShopHook;
import org.linear.linearbot.hook.ResidenceHook;
import org.linear.linearbot.metrics.Metrics;
import sdk.config.CQConfig;
import sdk.connection.Connection;
import sdk.connection.ConnectionFactory;
import sdk.event.EventDispatchers;
import sdk.listener.SimpleListener;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public final class LinearBot extends JavaPlugin implements Listener{

    public static LinearBot INSTANCE;

    private static CQConfig http_config;

    private static Bot bot;
    private static QQEvent qqEvent;

    @Override
    public void onEnable() {

        INSTANCE = this;


        Config.createConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        AuthMeHook.hookAuthme();
        ResidenceHook.hookRes();
        QuickShopHook.hookQuickShop();
        GriefDefenderHook.hookGriefDefender();
        getLogger().info("关联插件连接完毕");
        Bukkit.getPluginManager().registerEvents(new ServerEvent(), this);
        if (QuickShopHook.hasQs) Bukkit.getPluginManager().registerEvents(new QsChatEvent(),this);
//        if (QuickShopHook.hasQsHikari) Bukkit.getPluginManager().registerEvents(new QsHikariChatEvent(),this);
        getLogger().info("服务器事件监听器注册完毕");
//        Bukkit.getPluginManager().registerEvents(new QQEvent(), this);
        qqEvent = new QQEvent();
        getLogger().info("QQ事件监听器注册完毕");
        Bukkit.getServer().getPluginCommand("linearbot").setExecutor(new Commands());
        getLogger().info("命令注册完毕");

        // All you have to do is adding the following two lines in your onEnable method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 17137; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info( "LinearBot已启动");
        /*List<Long> groups = Config.getGroupQQ();
        for (long groupID : groups) {
            Bot.sendMsg("插件已启动", groupID);
        }*/

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
        dispatchers.start(10);//线程组处理任务

        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            LinearBot.getBot().sendGroupMsg("LinearBot已启动", groupID);
        }
//        runAfterDone();
    }

    @Override
    public void onDisable() {
        getLogger().info("LinearBot已关闭");
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            bot.sendGroupMsg("LinearBot已关闭", groupID);
        }
    }

    public static void say(String s) {
        CommandSender sender = Bukkit.getConsoleSender();
        sender.sendMessage(s);
    }

//    public void runAfterDone() {
//        Runnable thread = () -> {
////            http_config = new CQConfig(Config.getBotHttp(), Config.getBotToken(), Config.getBotIsAccessToken());
////            bot = new Bot();
////            LinkedBlockingQueue<String> blockingQueue = new LinkedBlockingQueue();//使用队列传输数据
////            Connection connection = null;
////            try {
////                connection = ConnectionFactory.createHttpServer(Config.getBotListenPort(),"/",blockingQueue);
////            } catch (Exception e) {
////                throw new RuntimeException(e);
////            }
////            connection.create();
////            EventDispatchers dispatchers = new EventDispatchers(blockingQueue);//创建事件分发器
//////            GroupMessageListener groupMessageListener = new GroupMessageListener();
//////            groupMessageListener.addHandler("天气", new Handler<GroupMessage>() {
//////                @Override
//////                public void handle(GroupMessage groupMessage) {
//////                    System.out.println(groupMessage);
//////                }
//////            });
//////            dispatchers.addListener(groupMessageListener);
////            dispatchers.addListener(new SimpleListener<PrivateMessage>() {//私聊监听
////                @Override
////                public void onMessage(PrivateMessage privateMessage) {
////                    // Create the event here
////                    org.linear.linearbot.event.custom.PrivateMessage event = new org.linear.linearbot.event.custom.PrivateMessage();
////                    // Call the event
////                    Bukkit.getServer().getPluginManager().callEvent(event);
////                }
////            });
////            dispatchers.addListener(new SimpleListener<GroupMessage>() {//群聊消息监听
////                @Override
////                public void onMessage(GroupMessage groupMessage) {
////                    // Create the event here
////                    org.linear.linearbot.event.custom.GroupMessage event = new org.linear.linearbot.event.custom.GroupMessage();
////                    // Call the event
////                    Bukkit.getServer().getPluginManager().callEvent(event);
////                }
////            });
////
////            dispatchers.start(10);//线程组处理任务
////
////            List<Long> groups = Config.getGroupQQs();
////            for (long groupID : groups) {
////                LinearBot.getBot().sendGroupMsg("LinearBot已启动", groupID);
////            }
//
//        };
//        INSTANCE.getServer().getScheduler().runTask(INSTANCE, thread);
//    }

    public static Bot getBot() {
        return bot;
    }

    public static CQConfig getHttp_config() {
        return http_config;
    }
}


