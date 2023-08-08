package me.regadpole.plumbot.event.qq;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.VelocityConfig;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.tool.StringTool;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQEvent {

    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().build();

    private final PlumBot plugin;
    private VelocityConfig velocityConfig;

    public QQEvent(PlumBot plugin) {
        this.plugin = plugin;
    }

    public void onFriendMessageReceive(PrivateMessage e){

        if(e.getMessage().equals("/在线人数")) {
            if(!Config.config.Online){
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            PlumBot.getBot().sendMsg(false, "当前在线：" + "("+plugin.getServer().getAllPlayers().size()+"人)"+pname, e.getUserId());
            return;
        }

        plugin.getServer().sendMessage(Component.text("§6"+"[私聊消息]"+"§a"+e.getSender().getNickname()+"§f"+":"+e.getMessage()));
    }

    public void onGroupMessageReceive(GroupMessage e){

        Pattern pattern;
        Matcher matcher;

        String msg = e.getMessage();
        long groupID= e.getGroupId();
        long senderID = e.getUserId();
        String groupName = PlumBot.getBot().getGroupInfo(e.getGroupId()).getGroupName();

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

        if(msg.equals("/帮助")) {
            List<String> messages = new LinkedList<>();
            StringBuilder stringBuilder = new StringBuilder();
            messages.add("成员命令:");
            messages.add("/在线人数 查看服务器当前在线人数");
            for (String message : messages) {
                if (messages.get(messages.size() - 1).equalsIgnoreCase(message)) {
                    stringBuilder.append(message.replaceAll("§\\S", ""));
                } else {
                    stringBuilder.append(message.replaceAll("§\\S", "")).append("\n");
                }
            }
            PlumBot.getBot().sendMsg(true, stringBuilder.toString(),groupID);
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
            PlumBot.getBot().sendMsg(true, "当前在线：" + "("+plugin.getServer().getAllPlayers().size()+"人)"+pname, groupID);
            return;
        }

        if (Config.config.SDR){
            if (plugin.vconf.getReturnsObj().get(msg) == null) return;
            String back = String.valueOf(plugin.vconf.getReturnsObj().get(msg));
            if(back!=null){
                PlumBot.getBot().sendMsg(true, back,groupID);
                return;
            }
        }

        if(Config.bot.Groups.contains(groupID)) {
            if (!Config.config.Forwarding){
                return;
            }
            String name = StringTool.filterColor(e.getSender().getCard());
            String smsg = StringTool.filterColor(msg);
            String message = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            pattern = Pattern.compile("\\[CQ:.*].*");
            matcher = pattern.matcher(smsg);
            if (matcher.find()){
                String useMsg = matcher.group().replaceAll("\\[CQ:.*]", "");
                String sendMsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg;
                plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendMsg));});
                return;
            }
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(message));});
        }


    }

}
