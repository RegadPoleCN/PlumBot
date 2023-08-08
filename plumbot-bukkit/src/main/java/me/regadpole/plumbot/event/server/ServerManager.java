package me.regadpole.plumbot.event.server;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ServerManager {

    public static String listOnlinePlayer() {
        List<String> onlinePlayer = new LinkedList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            onlinePlayer.add(p.getName());
        }
        return Arrays.toString(onlinePlayer.toArray()).replace("\\[|\\]", "");
    }

    public static List<String> msgList = new LinkedList<>();

    public static void sendCmd(boolean isGroup, long id, String cmd, boolean disp) {
        if(!Config.CMD()){
            PlumBot.getBot().sendMsg(isGroup, "未开启CMD命令功能", id);
        }

        CommandSender commandSender = new ConsoleSender();
        new BukkitRunnable(){
            @Override
            public void run(){
                msgList.clear();
                Bukkit.dispatchCommand(commandSender, cmd);
            }
        }.runTask(PlumBot.INSTANCE);
        Bukkit.getScheduler().runTaskLaterAsynchronously(PlumBot.INSTANCE, () -> {
            StringBuilder stringBuilder = new StringBuilder();
            if (msgList.size() == 0) {
                msgList.add("无返回值");
            }
            for (String msg : msgList) {
                if (msgList.get(msgList.size() - 1).equalsIgnoreCase(msg)) {
                    stringBuilder.append(msg.replaceAll("§\\S", ""));
                } else {
                    stringBuilder.append(msg.replaceAll("§\\S", "")).append("\n");
                }
            }
            if(!disp){
                msgList.clear();
                PlumBot.getBot().sendMsg(isGroup, "无返回值", id);
                return;
            }
            if(stringBuilder.toString().length()<=5000){
                PlumBot.getBot().sendMsg(isGroup, stringBuilder.toString(), id);
            }else {
                PlumBot.getBot().sendMsg(isGroup, "返回值过长", id);
            }
            msgList.clear();
        }, 4L);
    }

}