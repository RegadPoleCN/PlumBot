package me.regadpole.plumbot.event.server;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerEvent {
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Pattern pattern;
        Matcher matcher;
        if (!Config.config.Forwarding.enable){
            return;
        }
        String name = StringTool.filterColor(event.getPlayer().getUsername());
        String message = StringTool.filterColor(event.getMessage());
        if (Config.config.Forwarding.mode == 1) {
            pattern = Pattern.compile(Config.config.Forwarding.prefix+".*");
            matcher = pattern.matcher(message);
            if(!matcher.find()){
                return;
            }
            String fmsg = matcher.group().replaceAll(Config.config.Forwarding.prefix, "");
            List<Long> groups = Config.bot.Groups;
            for (long groupID : groups){
                PlumBot.getBot().sendMsg(true, "[" + event.getPlayer().getCurrentServer().get().getServer().getServerInfo().getName() + "]" + name+":"+fmsg,groupID);
            }
            return;
        }
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "[" + event.getPlayer().getCurrentServer().get().getServer().getServerInfo().getName() + "]" + name+":"+message,groupID);
        }
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event){
        String name = StringTool.filterColor(event.getPlayer().getUsername());
        if (Config.config.WhiteList.enable) {
            PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                long qq;
                qq = (DatabaseManager.getBindId(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase()));
                if (qq == 0L) {
                    PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {event.getPlayer().disconnect(Component.text(Config.config.WhiteList.kickMsg));}).delay(2L, TimeUnit.SECONDS).schedule();
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    List<Long> groups = Config.bot.Groups;
                    for (long groupID : groups) {
                        PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", groupID);
                    }
                    return;
                }
                for (long groupID : Config.bot.Groups) {
                    if(!PlumBot.getBot().checkUserInGroup(qq, groupID)){
                        PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {event.getPlayer().disconnect(Component.text(Config.config.WhiteList.kickMsg));}).delay(2L, TimeUnit.SECONDS).schedule();
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        List<Long> groups = Config.bot.Groups;
                        for (long group : groups) {
                            PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", group);
                        }
                        DatabaseManager.removeBind(String.valueOf(qq), DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                        return;
                    }
                }
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(event.getOriginalServer()));
            }).schedule();
        }
    }

    @Subscribe
    public void onJoin(ServerConnectedEvent event){
        String name = StringTool.filterColor(event.getPlayer().getUsername());
        if (!Config.config.JoinAndLeave){
            return;
        }
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "玩家"+name+"加入服务器",groupID);
        }

    }

    @Subscribe
    public void onQuit(KickedFromServerEvent event){

        String name = StringTool.filterColor(event.getPlayer().getUsername());

        if (!Config.config.JoinAndLeave){
            return;
        }
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups){
            PlumBot.getBot().sendMsg(true, "玩家"+name+"退出服务器",groupID);
        }
    }

}
