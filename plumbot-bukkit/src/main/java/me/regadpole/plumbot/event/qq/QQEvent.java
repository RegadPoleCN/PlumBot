package me.regadpole.plumbot.event.qq;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Args;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.event.server.ServerManager;
import me.regadpole.plumbot.event.server.ServerTps;
import me.regadpole.plumbot.internal.FoliaSupport;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.event.notice.GroupDecreaseNotice;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQEvent {

    String Prefix = Args.Prefix();

    public void onFriendMessageReceive(PrivateMessage e){


        if(e.getMessage().equals(Prefix+"在线人数")) {
            if(!Config.Online()){
                return;
            }
            PlumBot.getBot().sendMsg(false, "当前在线：" + "("+Bukkit.getServer().getOnlinePlayers().size()+"人)"+ServerManager.listOnlinePlayer(), e.getUserId());
            return;
        }

        if(e.getMessage().equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            PlumBot.getBot().sendMsg(false, "当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT(),e.getUserId());
            return;
        }

        if(Config.getAdmins().contains(e.getUserId())) {

            Pattern pattern;
            Matcher matcher;

            pattern = Pattern.compile(Prefix+"cmd .*");
            matcher = pattern.matcher(e.getMessage());
            if (matcher.find()) {
                if(!Config.CMD()){
                    return;
                }
                String cmd = matcher.group().replace(Prefix+"cmd ", "");
                ServerManager.sendCmd(false, e.getUserId(), cmd, true);
                PlumBot.getBot().sendMsg(false, "已发送指令至服务器",e.getUserId());
            }

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
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看");
            return;
        }

        pattern = Pattern.compile("\"ap.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){

            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看");
            return;
        }

        if(Config.getAdmins().contains(senderID)) {

            pattern = Pattern.compile(Prefix+"cmd .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if(!Config.CMD()){
                    return;
                }
                String cmd = matcher.group().replace(Prefix+"cmd ", "");
                ServerManager.sendCmd(true, groupID, cmd, true);
                PlumBot.getBot().sendMsg(true, "已发送指令至服务器",groupID);
                return;
            }

            pattern = Pattern.compile(Prefix + "删除白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.WhiteList()) {
                    return;
                }
                String name = matcher.group().replace(Prefix + "删除白名单 ", "");
                if (name.isEmpty()) {
                    PlumBot.getBot().sendMsg(true, "id不能为空", groupID);
                    return;
                }
                PlumBot.getScheduler().runTaskAsynchronously(() -> {
                    long nameForId = DatabaseManager.getBind(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    if (nameForId == 0L) {
                        PlumBot.getBot().sendMsg(true, "尚未申请白名单", groupID);
                        return;
                    }
                    DatabaseManager.removeBindid(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    PlumBot.getBot().sendMsg(true, "成功移出白名单", groupID);
                });
                return;
            }

            if(matcher.find()){
                pattern = Pattern.compile(Prefix+".*");
                matcher = pattern.matcher(msg);
                if (!Config.SDC()){
                    return;
                }
                String scmd = matcher.group().replace(Prefix+"", "");
                String gcmd = Config.getCommandsYaml().getString("Admin."+scmd);
                if(gcmd!=null) {
                    ServerManager.sendCmd(true, groupID, gcmd, true);
                    return;
                }
            }

        }

        if(msg.equals(Prefix+"帮助")) {
            List<String> messages = new LinkedList<>();
            StringBuilder stringBuilder = new StringBuilder();
            messages.add("成员命令:");
            messages.add(Prefix+"在线人数 查看服务器当前在线人数");
            messages.add(Prefix+"tps 查看服务器当前tps");
            messages.add(Prefix+"申请白名单 为自己申请白名单");
            messages.add(Prefix+"删除白名单 删除自己的白名单");
            messages.add("管理命令:");
            messages.add(Prefix+"cmd 向服务器发送命令");
            messages.add(Prefix+"删除白名单 删除指定游戏id的白名单");
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

        if(msg.equals(Prefix+"在线人数")) {
            if(!Config.Online()){
                return;
            }
            PlumBot.getBot().sendMsg(true, "当前在线：" + "("+Bukkit.getServer().getOnlinePlayers().size()+"人)"+ServerManager.listOnlinePlayer(),groupID);
            return;
        }

        if(msg.equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            PlumBot.getBot().sendMsg(true, "当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT(),groupID);
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
                PlumBot.getBot().sendMsg(true, "id不能为空", groupID);
                return;
            }
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                if ((DatabaseManager.getBind(senderID, DataBase.type().toLowerCase(), PlumBot.getDatabase()) != null) || (DatabaseManager.getBind(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()) != 0L)) {
                    PlumBot.getBot().sendMsg(true, "绑定失败", groupID);
                    return;
                }
                DatabaseManager.addBind(PlayerName, senderID, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                PlumBot.getBot().sendMsg(true, "成功申请白名单", groupID);
            });
            return;
        }

        pattern = Pattern.compile(Prefix + "删除白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            String name = matcher.group().replace(Prefix + "删除白名单 ", "");
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                String idForName = DatabaseManager.getBind(senderID, DataBase.type().toLowerCase(), PlumBot.getDatabase());
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
                DatabaseManager.removeBindid(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                PlumBot.getBot().sendMsg(true, "成功移出白名单", groupID);
            });
            return;
        }


        if(matcher.find()){
            pattern = Pattern.compile(Prefix+".*");
            matcher = pattern.matcher(msg);
            if (!Config.SDC()){
                return;
            }
            String scmd = matcher.group().replace(Prefix+"", "");
            String gcmd = Config.getCommandsYaml().getString("User."+scmd);
            if(gcmd!=null) {
                ServerManager.sendCmd(true, groupID, gcmd, true);
                return;
            }
        }

        if (Config.SDR()){
            String back = Config.getReturnsYaml().getString(msg);
            if(back!=null){
                PlumBot.getBot().sendMsg(true, back,groupID);
                return;
            }
        }

        if (!Config.Forwarding()){
            return;
        }

        if (Args.ForwardingMode()==1){
            pattern = Pattern.compile(Args.ForwardingPrefix()+".*");
            matcher = pattern.matcher(msg);
            if(!matcher.find()){
                return;
            }
            String fmsg = matcher.group().replace(Args.ForwardingPrefix(), "");
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(fmsg);
            pattern = Pattern.compile("\\[CQ:.*].*");
            matcher = pattern.matcher(smsg);
            if (matcher.find()){
                String useMsg = matcher.group().replaceAll("\\[CQ:.*]", "");
                if (FoliaSupport.isFolia) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                    }
                }
                Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                return;
            }
            if (FoliaSupport.isFolia) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
                }
            }
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
            return;
        }

        if(Config.getGroupQQs().contains(groupID)) {
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(msg);
            pattern = Pattern.compile("\\[CQ:.*].*");
            matcher = pattern.matcher(smsg);
            if (matcher.find()){
                String useMsg = matcher.group().replaceAll("\\[CQ:.*]", "");
                if (FoliaSupport.isFolia) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                    }
                }
                Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                return;
            }
            if (FoliaSupport.isFolia) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
                }
            }
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
        }

    }

    public void onGroupDecreaseNotice(GroupDecreaseNotice e) {
        long userId = e.getUserId();
        long groupId = e.getGroupId();
        if (!FoliaSupport.isFolia) {
            String player = DatabaseManager.getBind(userId, DataBase.type().toLowerCase(), PlumBot.getDatabase());
            if (player == null) {
                return;
            }
            DatabaseManager.removeBindid(player, DataBase.type().toLowerCase(), PlumBot.getDatabase());
        }
    }


}
