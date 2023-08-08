package me.regadpole.plumbot.event.server;

import me.regadpole.plumbot.hook.AuthMeHook;
import me.regadpole.plumbot.hook.GriefDefenderHook;
import me.regadpole.plumbot.hook.QuickShopHook;
import me.regadpole.plumbot.hook.ResidenceHook;
import me.regadpole.plumbot.tool.StringTool;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Config;

import java.util.List;

public class ServerEvent implements Listener{

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
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

        /*if (Args.WhitelistMode()==1){
            if (Config.getWhitelistYaml().getString(realName)==null){
                player.kickPlayer(Config.getConfigYaml().getString("Whitelist.kickMsg"));
            }
            List<Long> groups = Config.getGroupQQs();
            for (long groupID : groups){
                Bot.sendMsg("玩家"+name+"因为未在白名单中被踢出",groupID);
            }
            return;
        }*/
//        boolean whitelisted;
//        YamlConfiguration white = YamlConfiguration.loadConfiguration(Config.WhitelistFile());
//        List<String> names = white.getStringList("name");
//        whitelisted = names.contains(event.getPlayer().getName());
//
//        if(!whitelisted){
//            if (Config.getWhitelistYaml().getString(realName)==null){
//                player.kickPlayer(Config.getConfigYaml().getString("Whitelist.kickMsg"));
//            }
//            List<Long> groups = Config.getGroupQQs();
//            for (long groupID : groups){
//                LinearBot.getBot().sendGroupMsg("玩家"+name+"因为未在白名单中被踢出",groupID);
//            }
//            return;
//        }

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
