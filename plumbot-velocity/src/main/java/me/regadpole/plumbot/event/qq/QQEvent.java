package me.regadpole.plumbot.event.qq;

import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.VelocityConfig;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.event.notice.GroupDecreaseNotice;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQEvent {

    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().build();

    private final PlumBot plugin;
    private VelocityConfig velocityConfig;

    private String Prefix = Config.config.Forwarding.prefix;

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
        }
    }

    public void onGroupMessageReceive(GroupMessage e){

        Pattern pattern;
        Matcher matcher;

        String msg = e.getMessage();
        long groupID= e.getGroupId();
        long senderID = e.getUserId();
        String senderName;
        String groupName = PlumBot.getBot().getGroupInfo(e.getGroupId()).getGroupName();
        {
            if (e.getSender().getCard().isEmpty()) {
                senderName = e.getSender().getNickname();
            } else {
                senderName = e.getSender().getCard();
            }
        }

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

        if(Config.bot.Admins.contains(senderID)) {
            pattern = Pattern.compile("/删除白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.config.WhiteList.enable) {
                    return;
                }
                String name = matcher.group().replace("/删除白名单 ", "");
                if (name.isEmpty()) {
                    PlumBot.getBot().sendMsg(true, "id不能为空", groupID);
                    return;
                }
                PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                    long nameForId = DatabaseManager.getBind(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    if (nameForId == 0L) {
                        PlumBot.getBot().sendMsg(true, "尚未申请白名单", groupID);
                        return;
                    }
                    DatabaseManager.removeBindid(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    PlumBot.getBot().sendMsg(true, "成功移出白名单", groupID);
                });
                return;
            }
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

        pattern = Pattern.compile("/申请白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.config.WhiteList.enable) {
                return;
            }
            String PlayerName = matcher.group().replace("/申请白名单 ", "");
            if (PlayerName.isEmpty()) {
                PlumBot.getBot().sendMsg(true, "id不能为空", groupID);
                return;
            }
            PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                if ((DatabaseManager.getBind(senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase()) != null) || (DatabaseManager.getBind(PlayerName, DbConfig.type.toLowerCase(), PlumBot.getDatabase()) != 0L)) {
                    PlumBot.getBot().sendMsg(true, "绑定失败", groupID);
                    return;
                }
                DatabaseManager.addBind(PlayerName, senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                PlumBot.getBot().sendMsg(true, "成功申请白名单", groupID);
            }).schedule();
            return;
        }

        pattern = Pattern.compile("/删除白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.config.WhiteList.enable) {
                return;
            }
            String name = matcher.group().replace("/删除白名单 ", "");
            PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                String idForName = DatabaseManager.getBind(senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                if (idForName == null || idForName.isEmpty()) {
                    PlumBot.getBot().sendMsg(true, "您尚未申请白名单", groupID);
                    return;
                }
                if (name.isEmpty()) {
                    PlumBot.getBot().sendMsg(true, "id不能为空", groupID);
                    return;
                }
                if (!idForName.equals(name)) {
                    PlumBot.getBot().sendMsg(true, "你无权这样做", groupID);
                    return;
                }
                DatabaseManager.removeBindid(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                PlumBot.getBot().sendMsg(true, "成功移出白名单", groupID);
            }).schedule();
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

        if (!Config.config.Forwarding.enable){
            return;
        }

        if (Config.config.Forwarding.mode==1 && Config.bot.Groups.contains(groupID)){
            pattern = Pattern.compile(Prefix+".*");
            matcher = pattern.matcher(msg);
            if(!matcher.find()){
                return;
            }
            String fmsg = matcher.group().replace(Prefix, "");
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(fmsg);
            pattern = Pattern.compile("\\[CQ:.*].*");
            matcher = pattern.matcher(smsg);
            if (matcher.find()){
                String useMsg = matcher.group().replaceAll("\\[CQ:.*]", "");
                String sendMsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg;
                plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendMsg));});
                return;
            }
            String message = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(message));});
            return;
        }

        if(Config.bot.Groups.contains(groupID)) {
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(msg);
            pattern = Pattern.compile("\\[CQ:.*].*");
            matcher = pattern.matcher(smsg);
            if (matcher.find()){
                String useMsg = matcher.group().replaceAll("\\[CQ:.*]", "");
                String sendMsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg;
                plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendMsg));});
                return;
            }
            String message = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(message));});
        }


    }

    public void onGroupDecreaseNotice(GroupDecreaseNotice e) {
        long userId = e.getUserId();
        long groupId = e.getGroupId();
        String player = DatabaseManager.getBind(userId, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
        if (player == null) {
            return;
        }
        DatabaseManager.removeBindid(player, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
    }


}
