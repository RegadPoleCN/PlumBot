package me.regadpole.plumbot.event.kook;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.config.Args;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.event.server.ServerManager;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
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

    KookBot kBot;
    private PlumBot plugin;
    String Prefix = Args.Prefix();

    @EventHandler
    public void onChannelMessageReceive(ChannelMessageEvent e) {

        for (long groupId:Config.getGroupQQs()) {
            if (!e.getChannel().getId().equalsIgnoreCase(kBot.getChannel(groupId).getId())) return;
        }

        Pattern pattern;
        Matcher matcher;

        ArrayList<String> groups = new ArrayList<>();
        for (long groupId:Config.getGroupQQs()) {
            groups.add(kBot.getChannel(groupId).getId());
        }

        ArrayList<String> admins = new ArrayList<>();
        for (long adminId:Config.getAdmins()) {
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
            plugin.getProxy().broadcast("§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看");
            return;
        }

        pattern = Pattern.compile("\"ap.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){

            plugin.getProxy().broadcast("§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看");
            return;
        }

        if(admins.contains(senderID)) {

            pattern = Pattern.compile(Prefix + "删除白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.WhiteList()) {
                    return;
                }
                String name = matcher.group().replace(Prefix + "删除白名单 ", "");
                if (name.isEmpty()) {
                    e.getMessage().reply("id不能为空");
                    return;
                }
                plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                    long nameForId = DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    if (nameForId == 0L) {
                        e.getMessage().reply("尚未申请白名单");
                        return;
                    }
                    DatabaseManager.removeBindid(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功移出白名单");
                });
                return;
            }

            pattern = Pattern.compile(Prefix + "删除User白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.WhiteList()) {
                    return;
                }
                String qq = matcher.group().replace(Prefix + "删除User白名单 ", "");
                if (qq.isEmpty()) {
                    e.getMessage().reply("QQ不能为空");
                    return;
                }
                plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                    String idForName = DatabaseManager.getBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    if (idForName == null) {
                        e.getMessage().reply("尚未申请白名单");
                        return;
                    }
                    DatabaseManager.removeBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功移出白名单");
                });
                return;
            }

        }

        if(msg.equals(Prefix+"帮助")) {
            List<String> messages = new LinkedList<>();
            StringBuilder stringBuilder = new StringBuilder();
            messages.add("成员命令:");
            messages.add(Prefix+"在线人数 查看服务器当前在线人数");
            messages.add(Prefix+"申请白名单 <ID> 为自己申请白名单");
            messages.add(Prefix+"删除白名单 删除自己的白名单");
            messages.add("管理命令:");
            messages.add(Prefix+"删除白名单 <ID> 删除指定游戏id的白名单");
            messages.add(Prefix+"删除User白名单 <QQ号/kookID> 删除指定群成员的白名单");
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
            if(!Config.Online()){
                return;
            }
            e.getMessage().reply("当前在线：" + "("+plugin.getProxy().getPlayers().size()+"人)"+ServerManager.listOnlinePlayer());
            return;
        }


        pattern = Pattern.compile(Prefix + "申请白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            String PlayerName = matcher.group().replace(Prefix + "申请白名单 ", "");
            if (PlayerName.isEmpty()) {
                e.getMessage().reply("id不能为空");
                return;
            }
            plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                if ((DatabaseManager.getBind(senderID, DataBase.type().toLowerCase(), PlumBot.getDatabase())!=null) || (DatabaseManager.getBindId(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()) != 0L)) {
                    e.getMessage().reply("绑定失败");
                    return;
                }
                DatabaseManager.addBind(PlayerName, senderID, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                e.getMessage().reply("成功申请白名单");
            });
            return;
        }

        pattern = Pattern.compile(Prefix + "删除白名单");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                String idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                if (idForName == null || idForName.isEmpty()) {
                    e.getMessage().reply("您尚未申请白名单");
                    return;
                }
                DatabaseManager.removeBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                e.getMessage().reply("成功移出白名单");
            });
            return;
        }

        if (Config.SDR()){
            String back = Config.getReturnsYaml().getString(msg);
            if(back!=null){
                e.getMessage().reply(back);
                return;
            }
        }

        if (!Config.Forwarding()){
            return;
        }

        if (Args.ForwardingMode()==1 && groups.contains(groupID)){
            pattern = Pattern.compile(Args.ForwardingPrefix()+".*");
            matcher = pattern.matcher(msg);
            if(!matcher.find()){
                return;
            }
            String fmsg = msg.replace(Args.ForwardingPrefix(), "");
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(fmsg);
            plugin.getProxy().broadcast("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
            return;
        }

        if(groups.contains(groupID)) {
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(msg);
            plugin.getProxy().broadcast("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
        }

    }

    @EventHandler
    public void onPrivateMessageReceive(PrivateMessageReceivedEvent e) {

        ArrayList<String> admins = new ArrayList<>();
        for (long adminId:Config.getAdmins()) {
            admins.add(kBot.getUser(adminId).getId());
        }

        if(e.getMessage().toString().equals(Prefix+"在线人数")) {
            if(!Config.Online()){
                return;
            }
            e.getMessage().reply("当前在线：" + "("+ plugin.getProxy().getPlayers().size()+"人)"+ ServerManager.listOnlinePlayer());
            return;
        }
    }

    @EventHandler
    public void onGroupDecreaseNotice(UserLeaveGuildEvent e) {
        String userId = e.getUser().getId();
        String groupId = e.getGuildId();
        String player = DatabaseManager.getBind(userId, DataBase.type().toLowerCase(), PlumBot.getDatabase());
        if (player == null) {
            return;
        }
        DatabaseManager.removeBindid(player, DataBase.type().toLowerCase(), PlumBot.getDatabase());
    }
}
