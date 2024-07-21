package me.regadpole.plumbot.event.server;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Args;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.hook.AuthMeHook;
import me.regadpole.plumbot.hook.GriefDefenderHook;
import me.regadpole.plumbot.hook.QuickShopHook;
import me.regadpole.plumbot.hook.ResidenceHook;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerEvent implements Listener{

    private PlumBot plugin;

    public ServerEvent(PlumBot plugin){
        this.plugin=plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {

        Pattern pattern;
        Matcher matcher;

        if (!Config.Forwarding()){
            return;
        }
        String name = StringTool.filterColor(event.getPlayer().getDisplayName());
        String message = StringTool.filterColor(event.getMessage());
        if (AuthMeHook.hasAuthMe) {if (!AuthMeHook.authMeApi.isAuthenticated(event.getPlayer())) {return;} }
        if (ResidenceHook.hasRes) {if (ResidenceHook.resChatApi.getPlayerChannel(event.getPlayer().getName()) != null) {return;}}
        if (QuickShopHook.hasQs) {if (event.getPlayer() == QsChatEvent.getQsSender() && event.getMessage() == QsChatEvent.getQsMessage()) {return;}}
        if (QuickShopHook.hasQsHikari) {if (event.getPlayer() == QsHikariChatEvent.getQsSender() && event.getMessage() == QsHikariChatEvent.getQsMessage()) {return;}}
        if (GriefDefenderHook.hasGriefDefender) {if (GDClaimEvent.getGDMessage() == event.getMessage()){return;}}
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event){
        String name = event.getName();

        if (Config.WhiteList()) {
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                long qq;
                qq = (DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                if (qq == 0L) {
                    PlumBot.getScheduler().runTask(() -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, Args.WhitelistKick()));
                    List<Long> groups = Config.getGroupQQs();
                    for (long groupID : groups) {
                        PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", groupID);
                    }
                    return;
                }
                for (long groupID : Config.getGroupQQs()) {
                    if(!PlumBot.getBot().checkUserInGroup(qq, groupID)){
                        PlumBot.getScheduler().runTask(() -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, Args.WhitelistKick()));
                        List<Long> groups = Config.getGroupQQs();
                        for (long group : groups) {
                            PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", group);
                        }
                        DatabaseManager.removeBind(String.valueOf(qq), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        return;
                    }
                }
                PlumBot.getScheduler().runTask(event::allow);

            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event){

        Player player = event.getPlayer();
        String name = player.getName();

        if (Config.WhiteList()) {
            PlumBot.getScheduler().runTaskAsynchronously(() -> {
                long qq;
                qq = (DatabaseManager.getBindId(name, DataBase.type().toLowerCase(), PlumBot.getDatabase()));
                if (qq == 0L) {
                    PlumBot.getScheduler().runTask(() -> player.kickPlayer(Args.WhitelistKick()));
                    List<Long> groups = Config.getGroupQQs();
                    for (long groupID : groups) {
                        PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", groupID);
                    }
                    return;
                }
                for (long groupID : Config.getGroupQQs()) {
                    if(!PlumBot.getBot().checkUserInGroup(qq, groupID)){
                        PlumBot.getScheduler().runTask(() -> player.kickPlayer(Args.WhitelistKick()));
                        List<Long> groups = Config.getGroupQQs();
                        for (long group : groups) {
                            PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", group);
                        }
                        DatabaseManager.removeBind(String.valueOf(qq), DataBase.type().toLowerCase(), PlumBot.getDatabase());
                        return;
                    }
                }
                if (!Config.JoinAndLeave()){
                    return;
                }
                List<Long> groups = Config.getGroupQQs();
                for (long groupID : groups){
                    PlumBot.getBot().sendMsg(true, "玩家"+name+"加入游戏",groupID);
                }
            });
        } else {
            if (!Config.JoinAndLeave()){
                return;
            }
            List<Long> groups = Config.getGroupQQs();
            for (long groupID : groups){
                PlumBot.getBot().sendMsg(true, "玩家"+name+"加入游戏",groupID);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){

        String name = StringTool.filterColor(event.getPlayer().getDisplayName());

        if (!Config.JoinAndLeave()){
            return;
        }
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "玩家"+name+"退出游戏",groupID);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
        if(!Config.DieReport()){
            return;
        }
        Player player=event.getEntity();
        String name= player.getName();
        Location location=player.getLocation();
        int x= (int) location.getX();
        int y= (int) location.getY();
        int z= (int) location.getZ();
        String msg = "死在了"+location.getWorld().getName()+"世界"+"("+x+","+y+","+z+")";
        ServerManager.sendCmd("msg "+name+" "+msg, false);
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "玩家"+name+msg,groupID);
        }
    }
}
