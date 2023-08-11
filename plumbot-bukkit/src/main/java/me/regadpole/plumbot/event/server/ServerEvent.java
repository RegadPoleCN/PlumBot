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
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerEvent implements Listener{

    @EventHandler
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
                PlumBot.getBot().sendGroupMsg("[服务器]"+name+":"+fmsg,groupID);
            }
            return;
        }
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendGroupMsg("[服务器]"+name+":"+message,groupID);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){

        Player player = event.getPlayer();

        String name = StringTool.filterColor(player.getDisplayName());

        String realName = StringTool.filterColor(player.getName());
        if (Config.WhiteList()){
            AtomicLong qq = new AtomicLong();
            PlumBot.getScheduler().runTaskAsynchronously(() -> qq.set(DatabaseManager.getBind(realName, DataBase.type().toLowerCase(), PlumBot.getDatabase())));
            if (qq.get() == 0L){
                player.kickPlayer(Config.getConfigYaml().getString("WhiteList.kickMsg"));
                List<Long> groups = Config.getGroupQQs();
                for (long groupID : groups){
                    PlumBot.getBot().sendMsg(true, "玩家"+realName+"因为未在白名单中被踢出",groupID);
                }
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
        ServerManager.sendCmd(true, 0L, "msg "+name+" "+msg, false);
        List<Long> groups = Config.getGroupQQs();
        for (long groupID : groups){
            PlumBot.getBot().sendGroupMsg("玩家"+name+msg,groupID);
        }
    }
}
