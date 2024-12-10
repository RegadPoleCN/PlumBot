package me.regadpole.plumbot.bot;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.event.kook.KookEvent;
import me.regadpole.plumbot.tool.TextToImg;
import snw.jkook.JKook;
import snw.jkook.config.ConfigurationSection;
import snw.jkook.config.file.YamlConfiguration;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.Channel;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.event.channel.ChannelMessageEvent;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;
import snw.jkook.message.component.BaseComponent;
import snw.jkook.message.component.card.CardBuilder;
import snw.jkook.message.component.card.MultipleCardComponent;
import snw.jkook.message.component.card.Size;
import snw.jkook.message.component.card.Theme;
import snw.jkook.message.component.card.element.ImageElement;
import snw.jkook.message.component.card.module.ContainerModule;
import snw.jkook.util.PageIterator;
import snw.kookbc.impl.CoreImpl;
import snw.kookbc.impl.KBCClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static me.regadpole.plumbot.PlumBot.INSTANCE;

public class KookBot implements Bot {

    private static KBCClient kookClient;
    private static KookBot kookBot;
    private static boolean kookEnabled = false;

    @Override
    public void start() {
        CoreImpl kookcore;
        File kookFolder = new File(INSTANCE.getDataFolder(), "kook");
        ConfigurationSection config;
        File kookPlugins;
        KBCClient kook;
        kookcore = new CoreImpl();
        if (JKook.getCore() == null) JKook.setCore(kookcore);
        config = YamlConfiguration.loadConfiguration(new File(kookFolder, "kbc.yml"));
        kookPlugins = new File(kookFolder, "plugins");
        kook = new KBCClient(kookcore, config, kookPlugins, Config.getKookBotToken(), "websocket");
        kook.start();
        kookClient = kook;
        kookBot = new KookBot();
        kook.getCore().getEventManager().registerHandlers(kook.getInternalPlugin(), new KookEvent(this));
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            PlumBot.getBot().sendMsg(true, "PlumBot已启动", groupID);
        }
    }

    @Override
    public void shutdown() {
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups) {
            sendChannelMessage("PlumBot已关闭", getChannel(groupID));
        }
        kookClient.shutdown();
    }

    @Override
    public void sendMsg(boolean isGroup, String message, long id) {
        if (id == 0L) {return;}
        if ("".equals(message)) {return;}
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            if (isGroup) {
                sendChannelMessage(message, getChannel(id));
            }
            else {
                sendPrivateMessage(message, getUser(id));
            }
        });
    }

    public void sendMsg(boolean isGroup, BaseComponent message, long id) {
        if (id == 0L) {return;}
        if (message.toString().isEmpty()) {return;}
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            if (isGroup) {
                sendChannelMessage(message, getChannel(id));
            }
            else {
                sendPrivateMessage(message, getUser(id));
            }
        });
    }

    public void sendChannelReply(ChannelMessageEvent e, String s) {
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            e.getMessage().reply(s);
        });
    }

    public void sendPrivateReply(PrivateMessageReceivedEvent e, String s) {
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            e.getMessage().reply(s);
        });
    }

    public void sendPrivateFileReply(PrivateMessageReceivedEvent e, String s) {
        List<ImageElement> list = new ArrayList<ImageElement>();
        list.add(new ImageElement(createFile(s), "", false));
        MultipleCardComponent card = new CardBuilder()
                .setTheme(Theme.PRIMARY)
                .setSize(Size.LG)
                .addModule(new ContainerModule(list))
                .build();
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            e.getMessage().reply(card);
        });
    }

    public void sendChannelFileReply(ChannelMessageEvent e, String s) {
        List<ImageElement> list = new ArrayList<ImageElement>();
        list.add(new ImageElement(createFile(s), "", false));
        MultipleCardComponent card = new CardBuilder()
                .setTheme(Theme.PRIMARY)
                .setSize(Size.LG)
                .addModule(new ContainerModule(list))
                .build();
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            e.getMessage().reply(card);
        });
    }

    private void sendChannelMessage(String s, TextChannel channel) {
        channel.sendComponent(s);
    }

    private void sendChannelMessage(BaseComponent s, TextChannel channel) {
        channel.sendComponent(s);
    }

    private void sendPrivateMessage(String s, User user) {user.sendPrivateMessage(s);}
    private void sendPrivateMessage(BaseComponent s,User user) {user.sendPrivateMessage(s);}

    private String createFile(String s) {
        try {
            File file = File.createTempFile("PlumBot-", ".png");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(TextToImg.toImgBinArray(s));
            fos.close();
            return kookClient.getCore().getHttpAPI().uploadFile(file);
        } catch (IOException e) {
            INSTANCE.getSLF4JLogger().error(e.getMessage());
        }
        return null;
    }

    @Override
    public String getGroupName(long groupId) {
        Channel channel = getChannel(groupId);
        return channel.getName();
    }

    public TextChannel getChannel(long groupId) {
        return (TextChannel) kookClient.getCore().getHttpAPI().getChannel(String.valueOf(groupId));
    }

    @Override
    public boolean checkUserInGroup(long userId, long groupId){
        PageIterator<Set<User>> iterator = getChannel(groupId).getGuild().getUsers();
        while(iterator.hasNext()){
            for (User user : iterator.next()) {
                if (user.getId().equalsIgnoreCase(String.valueOf(userId))){
                    return true;
                }
            }
        }
        return false;
    }

    public User getUser(long id) {
        return kookClient.getCore().getHttpAPI().getUser(String.valueOf(id));
    }

    public static KBCClient getKookClient() {return kookClient;}

    public static void setKookEnabled(boolean kook) {kookEnabled = kook;}

    public static boolean isKookEnabled() {return kookEnabled;}

    public static KookBot getKookBot() {return kookBot;}
}
