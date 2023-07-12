package org.linear.linearbot.event.qq;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import me.dreamvoid.miraimc.api.MiraiBot;
import me.dreamvoid.miraimc.velocity.event.message.passive.MiraiFriendMessageEvent;
import me.dreamvoid.miraimc.velocity.event.message.passive.MiraiGroupMessageEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.linear.linearbot.LinearBot;
import org.linear.linearbot.bot.Bot;
import org.linear.linearbot.config.VelocityConfig;
import org.linear.linearbot.internal.Config;
import org.linear.linearbot.tool.StringTool;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQEvent {

    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().build();

    private final LinearBot plugin;
    private VelocityConfig velocityConfig;

    public QQEvent(LinearBot plugin) {
        this.plugin = plugin;
    }
    @Subscribe
    public void onFriendMessageReceive(MiraiFriendMessageEvent e){

        if(e.getMessage().equals("/在线人数")) {
            if(!Config.config.Online){
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            MiraiBot.getBot(e.getBotID()).getFriend(e.getSenderID()).sendMessage("当前在线：" + "("+plugin.getServer().getAllPlayers().size()+"人)"+pname);
            return;
        }

        plugin.getServer().sendMessage(Component.text("§6"+"[私聊消息]"+"§a"+e.getSenderName()+"§f"+":"+e.getMessage()));
    }

    @Subscribe
    public void onGroupMessageReceive(MiraiGroupMessageEvent e){

        Pattern pattern;
        Matcher matcher;

        String msg = e.getMessage();
        long groupID= e.getGroupID();
        long senderID = e.getSenderID();

        pattern = Pattern.compile("<?xm.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){
            return;
        }

        pattern = Pattern.compile("\"ap.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){
            return;
        }

        if(msg.equals("/在线人数")) {
            if(!Config.config.Online){
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            Bot.sendMsg("当前在线：" + "("+plugin.getServer().getAllPlayers().size()+"人)"+pname,groupID);
            return;
        }

        if(Config.bot.Groups.contains(groupID)) {
            if (!Config.config.Forwarding){
                return;
            }
            String name = StringTool.filterColor(e.getSenderName());
            String smsg = StringTool.filterColor(msg);
            String message = "§6" + "[" + e.getGroupName() + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(message));});
        }

        if (Config.config.SDR){
            if (plugin.vconf.getReturnsObj().get(msg) == null) return;
            String back = String.valueOf(plugin.vconf.getReturnsObj().get(msg));
            if(back!=null){
                Bot.sendMsg(back,groupID);
            }
        }

    }

}
