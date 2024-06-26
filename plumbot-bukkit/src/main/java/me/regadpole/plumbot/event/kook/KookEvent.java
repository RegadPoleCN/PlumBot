package me.regadpole.plumbot.event.kook;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
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
    public KookEvent(KookBot kookBot){
        kBot=kookBot;
    }

    KookBot kBot;

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
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看");
            return;
        }

        pattern = Pattern.compile("\"ap.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()){

            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看");
            return;
        }

        if(admins.contains(senderID)) {

            pattern = Pattern.compile(Prefix+"cmd .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if(!Config.CMD()){
                    return;
                }
                String cmd = matcher.group().replace(Prefix+"cmd ", "");
                e.getMessage().reply("已发送指令至服务器");
                PlumBot.getScheduler().runTaskAsynchronously(()->{
                    String img = ServerManager.sendCmd(cmd, true);
                    KookBot.getKookBot().sendChannelFileReply(e, img);
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
                    e.getMessage().reply("参数不能为空");
                    return;
                }
                if (para.startsWith("id:")) {
                    String name = para.substring(3);
                    if (name.isEmpty()) {
                        e.getMessage().reply("id不能为空");
                        return;
                    }
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        if (WhitelistHelper.checkIDNotExist(name)) {
                            e.getMessage().reply("ID尚未申请白名单");
                            return;
                        }
                        DatabaseManager.removeBindid(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        e.getMessage().reply("成功移出白名单");
                    });
                    return;
                } else if (para.startsWith("qq:")) {
                    String qq = para.substring(3);
                    if (qq.isEmpty()) {
                        e.getMessage().reply("QQ不能为空");
                        return;
                    }
                    String[] p = qq.split(" ");
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        List<String> idForName = DatabaseManager.getBind(p[0], DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (idForName.isEmpty()) {
                            e.getMessage().reply(p[0]+"尚未申请白名单");
                            return;
                        }
                        int num = Integer.parseInt(p[1]);
                        List<String> id = WhitelistHelper.removeAndGet(p[0], num, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        e.getMessage().reply("成功移出白名单，"+p[0]+"目前拥有的白名单为"+id);
                    });
                    return;
                } else {
                    int num = Integer.parseInt(matcher.group().replace(Prefix + "删除白名单 ", ""));
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        List<String> idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (idForName.isEmpty()) {
                            e.getMessage().reply("您尚未申请白名单");
                            return;
                        }
                        List<String> id = WhitelistHelper.removeAndGet(String.valueOf(senderID), num, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        e.getMessage().reply("成功移出白名单，您目前拥有的白名单为"+id);
                    });
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
                        String img = ServerManager.sendCmd(gcmd, true);
                        KookBot.getKookBot().sendChannelFileReply(e, img);
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
                    e.getMessage().reply("参数不能为空");
                    return;
                }
                if (para.startsWith("id:")) {
                    String name = para.substring(3);
                    if (name.isEmpty()) {
                        e.getMessage().reply("id不能为空");
                        return;
                    }
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        long qq = DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (qq==0L) {
                            e.getMessage().reply("ID尚未申请白名单");
                            return;
                        }
                        e.getMessage().reply(name+"的申请用户为"+qq);
                    });
                    return;
                } else if (para.startsWith("qq:")) {
                    String qq = para.substring(3);
                    if (qq.isEmpty()) {
                        e.getMessage().reply("QQ不能为空");
                        return;
                    }
                    PlumBot.getScheduler().runTaskAsynchronously(() -> {
                        List<String> id = DatabaseManager.getBind(qq, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        if (id.isEmpty()) {
                            e.getMessage().reply(qq+"尚未申请白名单");
                            return;
                        }
                        e.getMessage().reply(qq+"拥有白名单ID："+id);
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
            messages.add(Prefix+"删除白名单 <id:ID 或 qq:QQ 序号> 删除指定游戏id或kook用户的白名单");
            messages.add(Prefix+"查询白名单 <id:ID 或 qq:QQ> 查询指定游戏id或kook用户的白名单");
            messages.add("ps:请通过查询白名单获得序号");
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
            e.getMessage().reply("当前在线：" + "("+Bukkit.getServer().getOnlinePlayers().size()+"人)"+ServerManager.listOnlinePlayer());
            return;
        }

        if(msg.equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            e.getMessage().reply("当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT());
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
                    e.getMessage().reply("您尚未申请白名单");
                    return;
                }
                e.getMessage().reply(senderID +"拥有白名单ID："+idForName);
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
                    e.getMessage().reply("id不能为空");
                    return;
                }
                if(Config.getAdmins().contains(senderID)) {
                    if (!WhitelistHelper.checkIDNotExist(PlayerName)) {
                        e.getMessage().reply("绑定失败，此ID已绑定用户"+DatabaseManager.getBindId(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                        return;
                    }
                    List<String> id = WhitelistHelper.addAndGet(PlayerName, String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功申请白名单，您目前的白名单为"+id);
                    return;
                }
                PlumBot.getScheduler().runTaskAsynchronously(() -> {
                    if (!WhitelistHelper.checkCount(String.valueOf(senderID))) {
                        e.getMessage().reply("绑定失败，该用户已绑定："+DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                        return;
                    }
                    if (!WhitelistHelper.checkIDNotExist(PlayerName)){
                        e.getMessage().reply("绑定失败，此ID已绑定用户"+DatabaseManager.getBindId(PlayerName, DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                        return;
                    }
                    List<String> id = WhitelistHelper.addAndGet(PlayerName, String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功申请白名单，您目前的白名单为"+id);
                });
                return;
            } else if (para.length==2) {
                if(Config.getAdmins().contains(senderID)) {
                    if (!WhitelistHelper.checkIDNotExist(para[1])) {
                        e.getMessage().reply("绑定失败，此ID已绑定用户"+DatabaseManager.getBindId(para[1], DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                        return;
                    }
                    List<String> id = WhitelistHelper.addAndGet(para[1], para[0], DataBase.type().toLowerCase(), PlumBot.getDatabase());
                    e.getMessage().reply("成功申请白名单，"+para[0]+"目前的白名单为"+id);
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
            int num = Integer.parseInt(matcher.group().replace(Prefix + "删除白名单 ", ""));
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                List<String> idForName = DatabaseManager.getBind(String.valueOf(senderID), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                if (idForName.isEmpty()) {
                    e.getMessage().reply("您尚未申请白名单");
                    return;
                }
                List<String> result = WhitelistHelper.removeAndGet(String.valueOf(senderID), num, DataBase.type().toLowerCase(), PlumBot.getDatabase());
                e.getMessage().reply("成功移出白名单，您目前的白名单为"+result);
            });
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
                    String img = ServerManager.sendCmd(gcmd, true);
                    KookBot.getKookBot().sendChannelFileReply(e, img);
                });
                return;
            }
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
            if (FoliaSupport.isFolia) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
                }
                return;
            }
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
            return;
        }

        if(groups.contains(groupID)) {
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(msg);
            if (FoliaSupport.isFolia) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
                }
                return;
            }
            Bukkit.broadcastMessage("§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg);
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
            e.getMessage().reply("当前在线：" + "("+ Bukkit.getServer().getOnlinePlayers().size()+"人)"+ ServerManager.listOnlinePlayer());
            return;
        }

        if(e.getMessage().toString().equals(Prefix+"tps")) {
            if(!Config.TPS()){
                return;
            }
            ServerTps st = new ServerTps();
            e.getMessage().reply("当前tps：" + st.getTps() + "\n" + "当前MSPT：" + st.getMSPT());
            return;
        }

        if(admins.contains(e.getUser().getId())) {

            Pattern pattern;
            Matcher matcher;

            pattern = Pattern.compile(Prefix+"cmd .*");
            matcher = pattern.matcher(e.getMessage().toString());
            if (matcher.find()) {
                if(!Config.CMD()){
                    return;
                }
                String cmd = matcher.group().replace(Prefix+"cmd ", "");
                e.getMessage().reply("已发送指令至服务器");
                PlumBot.getScheduler().runTaskAsynchronously(()->{
                    String img = ServerManager.sendCmd(cmd, true);
                    KookBot.getKookBot().sendPrivateFileReply(e, img);
                });
            }
        }
    }

    @EventHandler
    public void onGroupDecreaseNotice(UserLeaveGuildEvent e) {
        String userId = e.getUser().getId();
        String groupId = e.getGuildId();
        List<String> player = DatabaseManager.getBind(userId, DataBase.type().toLowerCase(), PlumBot.getDatabase());
        if (player.isEmpty()) {
            return;
        }
        DatabaseManager.removeBind(userId, DataBase.type().toLowerCase(), PlumBot.getDatabase());
    }
}
