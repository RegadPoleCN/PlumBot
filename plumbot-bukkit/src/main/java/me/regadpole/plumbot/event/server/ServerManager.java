package me.regadpole.plumbot.event.server;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.tool.TextToImg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ServerManager {

    public static String listOnlinePlayer() {
        List<String> onlinePlayer = new LinkedList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            onlinePlayer.add(p.getName());
        }
        return Arrays.toString(onlinePlayer.toArray()).replace("\\[|\\]", "");
    }

    public static List<String> msgList = new LinkedList<>();

    public static String sendCmd(String cmd, boolean disp) {
        AtomicReference<String> returnStr = new AtomicReference<>("无返回值");
        if(!Config.CMD()){
            returnStr.set("未开启CMD命令功能");
        }

        CommandSender commandSender = new ConsoleSender();

        PlumBot.getScheduler().runTask(() -> {
            msgList.clear();
            Bukkit.dispatchCommand(commandSender, cmd);
        });
        PlumBot.getScheduler().runTaskLaterAsynchronously(() -> {
            StringBuilder stringBuilder = new StringBuilder();
            if (msgList.size() == 0) {
                msgList.add("无返回值");
            }
            for (String msg : msgList) {
                if (msgList.get(msgList.size() - 1).equalsIgnoreCase(msg)) {
                    stringBuilder.append(msg);
                } else {
                    stringBuilder.append(msg).append("\n");
                }
            }
            if(!disp){
                msgList.clear();
                returnStr.set("无返回值");
            }
            if(stringBuilder.toString().length()<=5000){
//                PlumBot.getBot().sendMsg(isGroup, stringBuilder.toString(), id);
                switch (Config.getBotMode()) {
                    case "go-cqhttp":
                        returnStr.set(TextToImg.toImgCQCode(stringBuilder.toString()));
                        break;
                    case "kook":
                        returnStr.set(TextToImg.toImgBinary(stringBuilder.toString()));
                        break;
                    default:
                        break;
                }
            }else {
                returnStr.set("返回值过长");
            }
            msgList.clear();
        }, 10L);
        return returnStr.get();
    }

}
