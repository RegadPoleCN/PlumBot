package me.regadpole.plumbot.event.qq;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.config.Args;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.event.server.ServerManager;
import me.regadpole.plumbot.event.server.ServerTps;
import me.regadpole.plumbot.internal.FoliaSupport;
import me.regadpole.plumbot.internal.WhitelistHelper;
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
                String para = matcher.group().replace(Prefix + "删除白名单 ", "");
                if (para.isEmpty()) {
                    bot.sendMsg(true, "参数不能为空", groupID);
                    return;
                }
                if (para.startsWith("id:")) {
                    String name = para.substring(3);
                    if (name.isEmpty()) {
                        bot.sendMsg(true, "id不能为空", groupID);
                        return;
                    }
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        if (WhitelistHelper.checkIDNotExist(name)) {
                            bot.sendMsg(true, "ID尚未申请白名单", groupID);
                            return;
                        }
                        DatabaseManager.removeBindid(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        bot.sendMsg(true, "成功移出白名单", groupID);
                    });
                    return;
                } else if (para.startsWith("qq:")) {
                    String qq = para.substring(3);
                    if (qq.isEmpty()) {
                        bot.sendMsg(true, "QQ不能为空", groupID);
                        return;
                    }
                    String[] p = qq.split(" ");
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        List<String> idForName = DatabaseManager.getBind(p[0], DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (idForName.isEmpty()) {
                            bot.sendMsg(true, p[0]+"尚未申请白名单", groupID);
                            return;
                        }
                        try{
                            int num = Integer.parseInt(p[1]);
                            List<String> id = WhitelistHelper.removeAndGet(p[0], num, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                            bot.sendMsg(true, "成功移出白名单，"+p[0]+"目前拥有的白名单为"+id, groupID);
                        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
                            bot.sendMsg(true, "请正确输入序号", groupID);
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    });
                    return;
                } else {
                    try {
                        int num = Integer.parseInt(matcher.group().replace(Prefix + "删除白名单 ", ""));
                        PlumBot.getScheduler().runTaskAsynchronously(() -> {
                            List<String> idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                            if (idForName.isEmpty()) {
                                bot.sendMsg(true, "您尚未申请白名单", groupID);
                                return;
                            }
                            List<String> id = WhitelistHelper.removeAndGet(String.valueOf(senderID), num, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                            bot.sendMsg(true, "成功移出白名单，您目前拥有的白名单为" + id, groupID);
                        });
                    } catch (NumberFormatException | IndexOutOfBoundsException exception) {
                        bot.sendMsg(true, "请正确输入序号", groupID);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                    return;
                }
            }

            pattern = Pattern.compile(Prefix+".*");
            matcher = pattern.matcher(msg);
            if(matcher.find()){
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

            pattern = Pattern.compile(Prefix + "查询白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.WhiteList()) {
                    return;
                }
                String para = matcher.group().replace(Prefix + "查询白名单 ", "");
                if (para.isEmpty()) {
                    bot.sendMsg(true, "参数不能为空", groupID);
                    return;
                }
                if (para.startsWith("id:")) {
                    String name = para.substring(3);
                    if (name.isEmpty()) {
                        bot.sendMsg(true, "id不能为空", groupID);
                        return;
                    }
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        long qq = DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (qq==0L) {
                            bot.sendMsg(true, "ID尚未申请白名单", groupID);
                            return;
                        }
                        bot.sendMsg(true, name+"的申请用户为"+qq, groupID);
                    });
                    return;
                } else if (para.startsWith("qq:")) {
                    String qq = para.substring(3);
                    if (qq.isEmpty()) {
                        bot.sendMsg(true, "QQ不能为空", groupID);
                        return;
                    }
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        List<String> id = DatabaseManager.getBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (id.isEmpty()) {
                            bot.sendMsg(true, qq+"尚未申请白名单", groupID);
                            return;
                        }
                        bot.sendMsg(true, qq+"拥有白名单ID："+id, groupID);
                    });
                    return;
                }
                return;
            }

        }

        if(msg.equals(Prefix+"帮助")) {
            List<String> messages = new LinkedList<>();
            StringBuilder stringBuilder = new StringBuilder();
            messages.add("成员命令:");
            messages.add(Prefix+"在线人数 查看服务器当前在线人数");
            messages.add(Prefix+"tps 查看服务器当前tps");
            messages.add(Prefix+"申请白名单 <ID> 为自己申请白名单");
            messages.add(Prefix+"删除白名单 <序号> 删除自己的白名单");
            messages.add(Prefix+"查询白名单 查询自己的白名单");
            messages.add("管理命令:");
            messages.add(Prefix+"cmd 向服务器发送命令");
            messages.add(Prefix+"申请白名单 <qq> <id> 为指定用户申请白名单");
            messages.add(Prefix+"删除白名单 <id:ID 或 qq:QQ 序号> 删除指定游戏id或qq的白名单");
            messages.add(Prefix+"查询白名单 <id:ID 或 qq:QQ> 查询指定游戏id或qq的白名单");
            messages.add("ps:请通过查询白名单获得序号");
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

        pattern = Pattern.compile(Prefix + "查询白名单");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                List<String> idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                if (idForName.isEmpty()) {
                    bot.sendMsg(true, "您尚未申请白名单", groupID);
                    return;
                }
                bot.sendMsg(true, senderID +"拥有白名单ID："+idForName, groupID);
            });
            return;
        }

        pattern = Pattern.compile(Prefix + "申请白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            String PlayerName = matcher.group().replace(Prefix + "申请白名单 ", "");
            String[] para = PlayerName.split(" ");
            if (para.length==1){
                if (PlayerName.isEmpty()) {
                    bot.sendMsg(true, "id不能为空", groupID);
                    return;
                }
                if(Config.getAdmins().contains(senderID)) {
                    PlumBot.getScheduler().runTaskAsynchronously(()->{
                        if (!WhitelistHelper.checkIDNotExist(PlayerName)) {
                            bot.sendMsg(true, "绑定失败，此ID已绑定用户" + DatabaseManager.getBindId(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()), groupID);
                            return;
                        }
                        List<String> id = WhitelistHelper.addAndGet(PlayerName, String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        bot.sendMsg(true, "成功申请白名单，您目前的白名单为"+id, groupID);
                    });
                    return;
                }
                PlumBot.getScheduler().runTaskAsynchronously(() -> {
                    if (!WhitelistHelper.checkCount(String.valueOf(senderID))) {
                        bot.sendMsg(true, "绑定失败，该用户已绑定："+DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase()), groupID);
                        return;
                    }
                    if (!WhitelistHelper.checkIDNotExist(PlayerName)){
                        bot.sendMsg(true, "绑定失败，此ID已绑定用户"+DatabaseManager.getBindId(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()), groupID);
                        return;
                    }
                    List<String> id = WhitelistHelper.addAndGet(PlayerName, String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    bot.sendMsg(true, "成功申请白名单，您目前的白名单为"+id, groupID);
                });
                return;
            } else if (para.length==2) {
                if(Config.getAdmins().contains(senderID)) {
                    PlumBot.getScheduler().runTaskAsynchronously(()->{
                        if (!WhitelistHelper.checkIDNotExist(para[1])) {
                            bot.sendMsg(true, "绑定失败，此ID已绑定用户"+DatabaseManager.getBindId(para[1], DataBase.type().toLowerCase(), PlumBot.getDatabase()), groupID);
                            return;
                        }
                        List<String> id = WhitelistHelper.addAndGet(para[1], para[0], DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        bot.sendMsg(true, "成功申请白名单，"+para[0]+"目前的白名单为"+id, groupID);
                    });
                    return;
                }
            }
            return;
        }

        pattern = Pattern.compile(Prefix + "删除白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.WhiteList()) {
                return;
            }
            try {
                int num = Integer.parseInt(matcher.group().replace(Prefix + "删除白名单 ", ""));
                PlumBot.getScheduler().runTaskAsynchronously(() -> {
                    List<String> idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    if (idForName.isEmpty()) {
                        bot.sendMsg(true, "您尚未申请白名单", groupID);
                        return;
                    }
                    List<String> result = WhitelistHelper.removeAndGet(String.valueOf(senderID), num, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    bot.sendMsg(true, "成功移出白名单，您目前的白名单为" + result, groupID);
                });
            } catch (NumberFormatException | IndexOutOfBoundsException exception) {
                bot.sendMsg(true, "请正确输入序号", groupID);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return;
        }

        pattern = Pattern.compile(Prefix+".*");
        matcher = pattern.matcher(msg);
        if(matcher.find()){
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
            String fmsg = msg.replace(Args.ForwardingPrefix(), "");
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(fmsg);
            pattern = Pattern.compile("\\[CQ:.*].*");
            matcher = pattern.matcher(smsg);
            if (matcher.find()){
                String useMsg = smsg.replaceAll("\\[CQ:.*]", "");
                if (FoliaSupport.isFolia) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                    }
                    return;
                }
                Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                return;
            }
            if (FoliaSupport.isFolia) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
                }
                return;
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
                String useMsg = smsg.replaceAll("\\[CQ:.*]", "");
                if (FoliaSupport.isFolia) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                    }
                    return;
                }
                Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + useMsg);
                return;
            }
            if (FoliaSupport.isFolia) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
                }
                return;
            }
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
        }

    }

    public void onGroupDecreaseNotice(GroupDecreaseNotice e) {
        long userId = e.getUserId();
        long groupId = e.getGroupId();
        PlumBot.getScheduler().runTaskAsynchronously(() -> {
            List<String> player = DatabaseManager.getBind(String.valueOf(userId), DataBase.type().toLowerCase(), PlumBot.getDatabase());
            if (player.isEmpty()) {
                return;
            }
            DatabaseManager.removeBind(String.valueOf(userId), DataBase.type().toLowerCase(), PlumBot.getDatabase());
        });
    }


}
