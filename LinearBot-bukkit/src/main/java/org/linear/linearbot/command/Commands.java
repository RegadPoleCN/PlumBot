package org.linear.linearbot.command;

import org.linear.linearbot.config.Config;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Commands implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("请使用/lb help查看命令使用方法");
            return true;
        }

        if(args.length != 0) {
            switch (args[0]) {
                /*
                case "bind":
                if (args.length != 2) return true;
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ConfigManager.getMessage_Server("ArgsError"));
                    return true;
                }
                Player player = (Player) sender;
                try {
                    long qq = Long.parseLong(args[1]);
                    if (ConfigManager.getBindUser().containsKey(qq)) {
                        player.sendMessage(ConfigManager.getMessage_Server("QQReapt"));
                        return true;
                    }
                    player.sendMessage(ConfigManager.getMessage_Server("ChangeBind")
                            .replaceAll("%qq%", String.valueOf(qq)));
                    for (long group : ConfigManager.getGroups()) {
                        Bot.getMiraiBot()
                                .getGroup(group)
                                .sendMessage(ConfigManager.getMessage_QQ("ChangeBind")
                                        .replaceAll("%qq%", String.valueOf(qq))
                                        .replaceAll("%player%", player.getName()));
                    }
                    PlayerData.changeBind(player.getName(), qq);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    player.sendMessage(ConfigManager.getMessage_Server("ArgsError"));
                }
                break;*/
                case "reload":
                    if (args.length != 1) return true;
                    Config.loadConfig();
                    sender.sendMessage("LinearBot配置文件已重载");
                    break;
                case "help":
                    if (args.length != 1) return true;
                    sender.sendMessage("§6LinearBot 机器人帮助菜单");
                    sender.sendMessage("§6/lb reload :§f重载插件");
                    sender.sendMessage("§6/lb help :§f获取插件帮助");
                    break;
                default:
                    if (args.length != 1) return true;
                    sender.sendMessage("错误的指令用法，请使用/lb help查看命令使用方法");
                    break;
            }
        }
    return true;
    }
}
