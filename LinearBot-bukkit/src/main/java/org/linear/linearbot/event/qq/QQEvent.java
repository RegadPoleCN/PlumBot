package org.linear.linearbot.event.qq;

import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.linear.linearbot.LinearBot;
import org.linear.linearbot.bot.Bot;
import org.linear.linearbot.config.Config;
import org.linear.linearbot.event.server.ServerManager;
import org.linear.linearbot.event.server.ServerTps;
import org.linear.linearbot.tool.StringTool;
import org.linear.linearbot.config.Args;

import java.io.*;
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
            LinearBot.getBot().sendPrivateMsg("当前在线：" + Bukkit.getServer().getOnlinePlayers()+"("+Bukkit.getServer().getOnlinePlayers().size()+"人)", e.getUserId());
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
                String cmd = Pattern.compile(Prefix+"cmd .*").matcher(e.getMessage()).group().replace(Prefix+"cmd ", "");
                ServerManager.sendCmd(cmd,e.getUserId(),false);
                LinearBot.getBot().sendPrivateMsg("已发送指令至服务器",e.getUserId());
            }

            return;
        }

        Bukkit.broadcastMessage("§6"+"[私聊消息]"+"§a"+e.getSender().getNickname()+"§f"+":"+e.getMessage());
    }

    public void onGroupMessageReceive(GroupMessage e){

        Pattern pattern;
        Matcher matcher;

        String msg = e.getMessage();
        long groupID= e.getGroupId();
        long senderID = e.getUserId();
        String groupName = LinearBot.getBot().getGroupInfo(e.getGroupId()).getGroupName();

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
            LinearBot.getBot().sendGroupMsg(stringBuilder.toString(),groupID);
        }

        if(msg.equals(Prefix+"在线人数")) {
            if(!Config.Online()){
                return;
            }
            LinearBot.getBot().sendGroupMsg("当前在线：" + "("+Bukkit.getServer().getOnlinePlayers().size()+"人)"+ServerManager.listOnlinePlayer(),groupID);
            return;
        }

        if(msg.equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            LinearBot.getBot().sendGroupMsg("当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT(),groupID);
            return;
        }

//        pattern = Pattern.compile(Prefix+"申请白名单 .*");
//        matcher = pattern.matcher(msg);
//        if (matcher.find()) {
//            if(!Config.WhiteList()){
//                return;
//            }
//            String PlayerName = matcher.group().replace(Prefix+"申请白名单 ", "");
//            if (((MiraiMC.getBind(Bukkit.getOfflinePlayer(PlayerName).getUniqueId()) != 0) || (MiraiMC.getBind(e.getSenderID()) != null))) {
//                MiraiBot.getBot(e.getBotID()).getGroup(e.getGroupID()).sendMessage("绑定失败");
//                return;
//            }
//            YamlConfiguration white = YamlConfiguration.loadConfiguration(Config.WhitelistFile());
//            List<String> nameList = white.getStringList("name");
//            try {
//                nameList.add(PlayerName);
//                white.set("name", nameList);
//                white.save(Config.WhitelistFile());
//                MiraiMC.addBind(Bukkit.getOfflinePlayer(PlayerName).getUniqueId(),senderID);
//            } catch (IOException ex) {
//                Bot.sendMsg("出现异常:"+ex,groupID);
//            }
//            Bot.sendMsg("成功申请白名单",groupID);
//            return;
//        }
//
//        pattern = Pattern.compile(Prefix+"删除白名单 .*");
//        matcher = pattern.matcher(msg);
//        if (matcher.find()) {
//            if(!Config.WhiteList()){
//                return;
//            }
//            YamlConfiguration white = YamlConfiguration.loadConfiguration(Config.WhitelistFile());
//            List<String> nameList = white.getStringList("name");
//            String name = matcher.group().replace(Prefix+"删除白名单 ", "");
//            if(MiraiMC.getBind(senderID) != Bukkit.getOfflinePlayer(name).getUniqueId()){
//                Bot.sendMsg("你无权这样做",groupID);
//                return;
//            }
//            //ServerManager.sendCmd("whitelist remove "+name,groupID,false);
//
//            try {
//                nameList.remove(name);
//                white.set("name",nameList);
//                white.save(Config.WhitelistFile());
//                MiraiMC.removeBind(Bukkit.getOfflinePlayer(name).getUniqueId());
//            } catch (IOException ex) {
//                Bot.sendMsg("出现异常:"+ex,groupID);
//            }
//            Bot.sendMsg("成功移出白名单",groupID);
//            return;
//        }

        pattern = Pattern.compile(Prefix+".*");
        matcher = pattern.matcher(msg);
        if(matcher.find()){
            if (!Config.SDC()){
                return;
            }
            String scmd = matcher.group().replace(Prefix+"", "");
            String gcmd = Config.getCommandsYaml().getString("User."+scmd);
            if(gcmd!=null) {
                ServerManager.sendCmd(gcmd,groupID,false);
                return;
            }
        }

        if (Config.SDR()){
            String back = Config.getReturnsYaml().getString(msg);
            if(back!=null){
                LinearBot.getBot().sendGroupMsg(back,groupID);
                return;
            }
        }

        if(Config.getAdmins().contains(senderID)) {

            pattern = Pattern.compile(Prefix+"cmd .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if(!Config.CMD()){
                    return;
                }
                String cmd = matcher.group().replace(Prefix+"cmd ", "");
                ServerManager.sendCmd(cmd,groupID,true);
                return;
            }

//            pattern = Pattern.compile(Prefix+"删除白名单 .*");
//            matcher = pattern.matcher(msg);
//            if (matcher.find()) {
//                if(!Config.WhiteList()){
//                    return;
//                }
//                YamlConfiguration white = YamlConfiguration.loadConfiguration(Config.WhitelistFile());
//                List<String> nameList = white.getStringList("name");
//                String name = matcher.group().replace(Prefix+"删除白名单 ", "");
//                try {
//                    nameList.remove(name);
//                    white.set("name",nameList);
//                    white.save(Config.WhitelistFile());
//                    MiraiMC.removeBind(Bukkit.getOfflinePlayer(name).getUniqueId());
//                } catch (IOException ex) {
//                    Bot.sendMsg("出现异常:"+ex,groupID);
//                }
//                Bot.sendMsg("成功移出白名单",groupID);
//                return;
//            }

            pattern = Pattern.compile(Prefix+".*");
            matcher = pattern.matcher(msg);
            if(matcher.find()){
                if (!Config.SDC()){
                    return;
                }
                String scmd = matcher.group().replace(Prefix+"", "");
                String gcmd = Config.getCommandsYaml().getString("Admin."+scmd);
                if(gcmd!=null) {
                    ServerManager.sendCmd(gcmd,groupID,false);
                    return;
                }
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
            String name = StringTool.filterColor(e.getSender().getNickname());
            String smsg = StringTool.filterColor(msg);
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
        }

        if(Config.getGroupQQs().contains(groupID)) {
            String name = StringTool.filterColor(e.getSender().getNickname());
            String smsg = StringTool.filterColor(msg);
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
        }

    }

//    @EventHandler
//    public void MemberLeaveEvent(MiraiMemberLeaveEvent event){
//        long targetID = event.getTargetID();
//        long groupID = event.getGroupID();
//        UUID uuid = MiraiMC.getBind(targetID);
//        if(uuid == null){
//            return;
//        }
//        YamlConfiguration white = YamlConfiguration.loadConfiguration(Config.WhitelistFile());
//        List<String> nameList = white.getStringList("name");
//        try {
//            nameList.remove(Bukkit.getOfflinePlayer(uuid).getName());
//            white.set("name",nameList);
//            white.save(Config.WhitelistFile());
//            MiraiMC.removeBind(uuid);
//        } catch (IOException ex) {
//            Bot.sendMsg("出现异常:"+ex,groupID);
//        }
//    }


}
