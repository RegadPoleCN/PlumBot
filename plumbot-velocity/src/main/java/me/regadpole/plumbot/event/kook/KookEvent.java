package me.regadpole.plumbot.event.kook;

import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import snw.jkook.event.EventHandler;
import snw.jkook.event.Listener;
import snw.jkook.event.channel.ChannelMessageEvent;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;
import snw.jkook.event.user.UserLeaveGuildEvent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KookEvent implements Listener {
    public KookEvent(KookBot kookBot, PlumBot plugin){
        kBot=kookBot;
        this.plugin=plugin;
    }

    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().build();

    PlumBot plugin;
    KookBot kBot;

    String Prefix = Config.config.Forwarding.prefix;

    @EventHandler
    public void onChannelMessageReceive(ChannelMessageEvent e) {

        KookBot bot = (KookBot) PlumBot.getBot();

        for (long groupId:Config.bot.Groups) {
            if (!e.getChannel().getId().equalsIgnoreCase(kBot.getChannel(groupId).getId())) return;
        }

        Pattern pattern;
        Matcher matcher;

        ArrayList<String> groups = new ArrayList<>();
        for (long groupId:Config.bot.Groups) {
            groups.add(kBot.getChannel(groupId).getId());
        }

        ArrayList<String> admins = new ArrayList<>();
        for (long adminId:Config.bot.Admins) {
            admins.add(kBot.getUser(adminId).getId());
        }

        String msg = e.getMessage().getComponent().toString();
        String  groupID= e.getChannel().getId();
        String senderID = e.getMessage().getSender().getId();
        String senderName = e.getMessage().getSender().getNickName(e.getChannel().getGuild());
        String groupName = e.getChannel().getGuild().getName()+"/"+e.getChannel().getName();

        pattern = Pattern.compile("<?xm.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){
            String sendmsg = "§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看";
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendmsg));});
            return;
        }

        pattern = Pattern.compile("\"ap.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){
            String sendmsg = "§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看";
             plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendmsg));});
            return;
        }

        if(admins.contains(senderID)) {
            pattern = Pattern.compile(Prefix + "删除白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.config.WhiteList.enable) {
                    return;
                }
                String name = matcher.group().replace(Prefix + "删除白名单 ", "");
                if (name.isEmpty()) {
                    e.getMessage().reply("id不能为空");
                    return;
                }
                plugin.getServer().getScheduler().buildTask(plugin, () -> {
                    long nameForId = DatabaseManager.getBindId(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    if (nameForId == 0L) {
                        e.getMessage().reply("尚未申请白名单");
                        return;
                    }
                    DatabaseManager.removeBindid(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功移出白名单");
                }).schedule();
                return;
            }

            pattern = Pattern.compile(Prefix + "删除User白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.config.WhiteList.enable) {
                    return;
                }
                String qq = matcher.group().replace(Prefix + "删除User白名单 ", "");
                if (qq.isEmpty()) {
                    e.getMessage().reply("QQ不能为空");
                    return;
                }
                PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                    String idForName = DatabaseManager.getBind(qq, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    if (idForName == null) {
                        e.getMessage().reply("尚未申请白名单");
                        return;
                    }
                    DatabaseManager.removeBind(qq, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功移出白名单");
                }).schedule();
                return;
            }

        }

        if(msg.equals(Prefix+"帮助")) {
            List<String> messages = new LinkedList<>();
            StringBuilder stringBuilder = new StringBuilder();
            messages.add("成员命令:");
            messages.add("/在线人数 查看服务器当前在线人数");
            messages.add("/申请白名单 <ID> 为自己申请白名单");
            messages.add("/删除白名单 删除自己的白名单");
            messages.add("管理命令:");
            messages.add("/删除白名单 <ID> 删除指定游戏id的白名单");
            messages.add("/删除User白名单 <QQ号/kookID> 删除指定群成员的白名单");
            for (String message : messages) {
                if (messages.get(messages.size() - 1).equalsIgnoreCase(message)) {
                    stringBuilder.append(message.replaceAll("§\\S", ""));
                } else {
                    stringBuilder.append(message.replaceAll("§\\S", "")).append("\n");
                }
            }
            e.getMessage().reply(stringBuilder.toString());
            return;
        }

        if(msg.equals(Prefix+"在线人数")) {
            if(!Config.config.Online){
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            e.getMessage().reply("当前在线：" + "("+plugin.getServer().getAllPlayers().size()+"人)"+pname);
            return;
        }

        pattern = Pattern.compile(Prefix + "申请白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.config.WhiteList.enable) {
                return;
            }
            String PlayerName = matcher.group().replace(Prefix + "申请白名单 ", "");
            if (PlayerName.isEmpty()) {
                e.getMessage().reply("id不能为空");
                return;
            }
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                if ((DatabaseManager.getBind(senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase())!=null) || (DatabaseManager.getBindId(PlayerName, DbConfig.type.toLowerCase(), PlumBot.getDatabase()) != 0L)) {
                    e.getMessage().reply("绑定失败");
                    return;
                }
                DatabaseManager.addBind(PlayerName, senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                e.getMessage().reply("成功申请白名单");
            }).schedule();
            return;
        }

        pattern = Pattern.compile("/删除白名单");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.config.WhiteList.enable) {
                return;
            }
            PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                String idForName = DatabaseManager.getBind(String.valueOf(senderID), DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                if (idForName == null || idForName.isEmpty()) {
                    e.getMessage().reply("您尚未申请白名单");
                    return;
                }
                DatabaseManager.removeBind(String.valueOf(senderID), DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                e.getMessage().reply("成功移出白名单");
            }).schedule();
            return;
        }

        if (Config.config.SDR){
            if (plugin.vconf.getReturnsObj().get(msg) == null) return;
            String back = String.valueOf(plugin.vconf.getReturnsObj().get(msg));
            if(back!=null){
                e.getMessage().reply(back);
                return;
            }
        }

        if (!Config.config.Forwarding.enable){
            return;
        }

        if (Config.config.Forwarding.mode==1 && groups.contains(groupID)){
            pattern = Pattern.compile(Config.config.Forwarding.prefix+".*");
            matcher = pattern.matcher(msg);
            if(!matcher.find()){
                return;
            }
            String fmsg = msg.replace(Config.config.Forwarding.prefix, "");
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(fmsg);
            String sendmsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendmsg));});
            return;
        }

        if(groups.contains(groupID)) {
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(msg);
            String sendmsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {server.sendMessage(SERIALIZER.deserialize(sendmsg));});
        }

    }

    @EventHandler
    public void onPrivateMessageReceive(PrivateMessageReceivedEvent e) {

        for (long adminId:Config.bot.Admins) {
            if (!e.getUser().getId().equalsIgnoreCase(kBot.getUser(adminId).getId())) return;
        }

        ArrayList<String> admins = new ArrayList<>();
        for (long adminId:Config.bot.Admins) {
            admins.add(kBot.getUser(adminId).getId());
        }

        if(e.getMessage().toString().equals(Prefix+"在线人数")) {
            if(!Config.config.Online){
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            e.getMessage().reply("当前在线：" + "("+ plugin.getServer().getAllPlayers().size()+"人)"+ pname);
        }

    }

    @EventHandler
    public void onGroupDecreaseNotice(UserLeaveGuildEvent e) {
        String userId = e.getUser().getId();
        String groupId = e.getGuildId();
        String player = DatabaseManager.getBind(userId, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
        if (player == null) {
            return;
        }
        DatabaseManager.removeBindid(player, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
    }
}
