package me.regadpole.plumbot.event.qq;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.QQBot;
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

        QQBot bot = (QQBot) PlumBot.getBot();

        Pattern pattern;
        Matcher matcher;

        if(e.getMessage().equals(Prefix+"在线人数")) {
            if(!Config.Online()){
                return;
            }
            bot.sendMsg(false, "当前在线：" + "("+Bukkit.getServer().getOnlinePlayers().size()+"人)"+ServerManager.listOnlinePlayer(), e.getUserId());
            return;
        }

        if(e.getMessage().equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            bot.sendMsg(false, "当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT(),e.getUserId());
            return;
        }

        if(Config.getAdmins().contains(e.getUserId())) {
            pattern = Pattern.compile(Prefix+"cmd .*");
            matcher = pattern.matcher(e.getMessage());
            if (matcher.find()) {
                if(!Config.CMD()){
                    return;
                }
                String cmd = matcher.group().replace(Prefix+"cmd ", "");
                bot.sendMsg(false, "已发送指令至服务器",e.getUserId());
                PlumBot.getScheduler().runTaskAsynchronously(()->{
                    String sendCqMsg = ServerManager.sendCmd(cmd, true);
                    bot.sendCQMsg(false, sendCqMsg, e.getUserId());
                });
            }
        }
    }

    public void onGroupMessageReceive(GroupMessage e){

        Pattern pattern;
        Matcher matcher;

        QQBot bot = (QQBot) PlumBot.getBot();
        String msg = e.getMessage();
        long groupID= e.getGroupId();
        long senderID = e.getUserId();
        String senderName;
        String groupName = bot.getGroupInfo(e.getGroupId()).getGroupName();

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
                bot.sendMsg(true, "已发送指令至服务器",groupID);
                PlumBot.getScheduler().runTaskAsynchronously(()->{
                    String sendCqMsg = ServerManager.sendCmd(cmd, true);
                    bot.sendCQMsg(true, sendCqMsg, groupID);
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
                if (name.isEmpty()) {
                    bot.sendMsg(true, "id不能为空", groupID);
                    return;
                }
                PlumBot.getScheduler().runTaskAsynchronously(() -> {
                    long nameForId = DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    if (nameForId == 0L) {
                        bot.sendMsg(true, "尚未申请白名单", groupID);
                        return;
                    }
                    DatabaseManager.removeBindid(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    bot.sendMsg(true, "成功移出白名单", groupID);
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
                    bot.sendMsg(true, "QQ不能为空", groupID);
                    return;
                }
                PlumBot.getScheduler().runTaskAsynchronously(() -> {
                    String idForName = DatabaseManager.getBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    if (idForName == null) {
                        bot.sendMsg(true, "尚未申请白名单", groupID);
                        return;
                    }
                    DatabaseManager.removeBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    bot.sendMsg(true, "成功移出白名单", groupID);
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
                    PlumBot.getScheduler().runTaskAsynchronously(()->{
                        String sendCqMsg = ServerManager.sendCmd(gcmd, true);
                        bot.sendCQMsg(true, sendCqMsg, groupID);
                    });
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
            messages.add(Prefix+"申请白名单 <ID> 为自己申请白名单");
            messages.add(Prefix+"删除白名单 删除自己的白名单");
            messages.add("管理命令:");
            messages.add(Prefix+"cmd 向服务器发送命令");
            messages.add(Prefix+"删除白名单 <ID> 删除指定游戏id的白名单");
            messages.add(Prefix+"删除User白名单 <QQ号/kookID> 删除指定群成员的白名单");
        for (String message : messages) {
                if (messages.get(messages.size() - 1).equalsIgnoreCase(message)) {
                    stringBuilder.append(message.replaceAll("§\\S", ""));
                } else {
                    stringBuilder.append(message.replaceAll("§\\S", "")).append("\n");
                }
            }
            bot.sendMsg(true, stringBuilder.toString(),groupID);
            return;
        }

        if(msg.equals(Prefix+"在线人数")) {
            if(!Config.Online()){
                return;
            }
            bot.sendMsg(true, "当前在线：" + "("+Bukkit.getServer().getOnlinePlayers().size()+"人)"+ServerManager.listOnlinePlayer(),groupID);
            return;
        }

        if(msg.equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            bot.sendMsg(true, "当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT(),groupID);
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
                bot.sendMsg(true, "id不能为空", groupID);
                return;
            }
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                if ((DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase()) != null) || (DatabaseManager.getBindId(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()) != 0L)) {
                    bot.sendMsg(true, "绑定失败", groupID);
                    return;
                }
                DatabaseManager.addBind(PlayerName, String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                bot.sendMsg(true, "成功申请白名单", groupID);
            });
            return;
        }

        pattern = Pattern.compile(Prefix + "删除白名单");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                String idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                if (idForName == null || idForName.isEmpty()) {
                    bot.sendMsg(true, "您尚未申请白名单", groupID);
                    return;
                }
                DatabaseManager.removeBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                bot.sendMsg(true, "成功移出白名单", groupID);
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
                PlumBot.getScheduler().runTaskAsynchronously(()->{
                    String sendCqMsg = ServerManager.sendCmd(gcmd, true);
                    bot.sendCQMsg(true, sendCqMsg, groupID);
                });
                return;
            }
        }

        if (Config.SDR()){
            String back = Config.getReturnsYaml().getString(msg);
            if(back!=null){
                bot.sendMsg(true, back,groupID);
                return;
            }
        }

        if (!Config.Forwarding()){
            return;
        }

        if (Args.ForwardingMode()==1 && Config.getGroupQQs().contains(groupID)){
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
        String player = DatabaseManager.getBind(String.valueOf(userId), DataBase.type().toLowerCase(), PlumBot.getDatabase());
        if (player == null) {
            return;
        }
        DatabaseManager.removeBind(String.valueOf(userId), DataBase.type().toLowerCase(), PlumBot.getDatabase());
    }


}
