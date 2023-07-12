package org.linear.linearbot.bot;

import me.dreamvoid.miraimc.api.MiraiBot;
import org.linear.linearbot.internal.Config;

public class Bot {
    public static void sendMsg(String msg,long groupID){
        MiraiBot.getBot(Config.bot.Bot.QQ).getGroup(groupID).sendMessageMirai(msg);
    }
}
