package org.linear.linearbot.event.server;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import org.linear.linearbot.bot.Bot;
import org.linear.linearbot.internal.Config;
import org.linear.linearbot.tool.StringTool;

import java.util.List;

public class ServerEvent {
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        if (!Config.config.Forwarding){
            return;
        }
        String name = StringTool.filterColor(event.getPlayer().getUsername());
        String message = StringTool.filterColor(event.getMessage());
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups){
            Bot.sendMsg("[" + event.getPlayer().getCurrentServer().get().getServer().getServerInfo().getName() + "]" + name+":"+message,groupID);
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
            Bot.sendMsg("玩家"+name+"加入服务器",groupID);
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
            Bot.sendMsg("玩家"+name+"退出服务器",groupID);
        }
    }

}
