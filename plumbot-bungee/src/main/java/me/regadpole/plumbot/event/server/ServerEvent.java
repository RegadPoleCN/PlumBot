package me.regadpole.plumbot.event.server;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Args;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerEvent implements Listener {

    private PlumBot plugin;

    public ServerEvent(PlumBot plugin){
        this.plugin=plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(ChatEvent event) {

        Pattern pattern;
        Matcher matcher;
        ProxiedPlayer player = ServerManager.getServerPlayer(event.getSender().getAddress());

        if (!Config.Forwarding()){
            return;
        }
        String name = StringTool.filterColor(player.getDisplayName());
        String message = StringTool.filterColor(event.getMessage());
        if (Args.ForwardingMode() == 1) {
            pattern = Pattern.compile(Args.ForwardingPrefix()+".*");
            matcher = pattern.matcher(message);
            if(!matcher.find()){
                return;
            }
            String fmsg = matcher.group().replaceAll(Args.ForwardingPrefix(), "");
            List<Long> groups = Config.getGroupQQs();
            for (long groupID : groups){
                PlumBot.getBot().sendMsg(true, "[服务器]"+name+":"+fmsg,groupID);
            }
            return;
        }

        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "[服务器]"+name+":"+message,groupID);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(ServerSwitchEvent event){
        ProxiedPlayer player = event.getPlayer();
        String name = player.getName();

        if (Config.WhiteList()) {
            plugin.getProxy().getScheduler().runAsync(plugin, () -> {
                long qq;
                qq = (DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                if (qq == 0L) {
                    player.disconnect(Args.WhitelistKick());
                    List<Long> groups = Config.getGroupQQs();
                    for (long groupID : groups) {
                        PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", groupID);
                    }
                    return;
                }
                for (long groupID : Config.getGroupQQs()) {
                    if(!PlumBot.getBot().checkUserInGroup(qq, groupID)){
                        player.disconnect(Args.WhitelistKick());
                        List<Long> groups = Config.getGroupQQs();
                        for (long group : groups) {
                            PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", group);
                        }
                        DatabaseManager.removeBind(String.valueOf(qq), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        return;
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(ServerConnectedEvent event){

        String name = event.getPlayer().getName();

        if (!Config.JoinAndLeave()){
            return;
        }
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "玩家"+name+"加入游戏",groupID);
        }
    }

    @EventHandler
    public void onQuit(PlayerDisconnectEvent event){

        String name = StringTool.filterColor(event.getPlayer().getDisplayName());

        if (!Config.JoinAndLeave()){
            return;
        }
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "玩家"+name+"退出游戏",groupID);
        }
    }
}
