package me.regadpole.plumbot.command;

import me.regadpole.plumbot.config.Config;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Commands implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("请使用/pb help查看命令使用方法");
            return true;
        }

        if(args.length != 0) {
            switch (args[0]) {
                case "reload":
                    if (args.length != 1) return true;
                    Config.loadConfig();
                    sender.sendMessage("PlumBot配置文件已重载");
                    break;
                case "help":
                    if (args.length != 1) return true;
                    sender.sendMessage("§6PlumBot 机器人帮助菜单");
                    sender.sendMessage("§6/pb reload :§f重载插件");
                    sender.sendMessage("§6/pb help :§f获取插件帮助");
                    break;
                default:
                    if (args.length != 1) return true;
                    sender.sendMessage("错误的指令用法，请使用/pb help查看命令使用方法");
                    break;
            }
        }
    return true;
    }
}
