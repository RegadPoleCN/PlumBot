package me.regadpole.plumbot.command;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import snw.jkook.command.ConsoleCommandSender;
import snw.jkook.plugin.Plugin;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor{

    private PlumBot plugin;

    public Commands(PlumBot plugin){
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("请使用/pb help查看命令使用方法");
            return true;
        }

        switch (args[0]) {
            case "info":
                StringBuilder stringbuilder = new StringBuilder();
                stringbuilder.append("§1---PlumBot 信息---").append("\n");
                stringbuilder.append("§a作者: ").append("§f").append(plugin.getEnvironment().author).append("\n");
                stringbuilder.append("§a版本: ").append("§f").append(plugin.getEnvironment().version).append("\n");
                stringbuilder.append("§a机器人平台: ").append("§f").append(Config.getBotMode()).append("\n");
                stringbuilder.append("§a数据库模式: ").append("§f").append(DataBase.type()).append("\n");
                stringbuilder.append("§a服务端版本: ").append("§f").append(Bukkit.getVersion()).append("\n");
                stringbuilder.append("§a兼容插件: ").append("\n");
                stringbuilder.append("\t").append("§bAuthMe: ").append(plugin.getEnvironment().authme).append("\n");
                stringbuilder.append("\t").append("§bGriefDefender: ").append(plugin.getEnvironment().griefdefender).append("\n");
                stringbuilder.append("\t").append("§bResidence: ").append(plugin.getEnvironment().residence).append("\n");
                stringbuilder.append("\t").append("§bQuickShop: ").append(plugin.getEnvironment().quickshop).append("\n");
                stringbuilder.append("\t").append("§bQuickShopHikari: ").append(plugin.getEnvironment().quickshophikari).append("\n");
                sender.sendMessage(stringbuilder.toString());
                break;
            case "reload":
                if (args.length != 1) return true;
                DatabaseManager.close();
                PlumBot.getBot().shutdown();
                Config.loadConfig();
                DatabaseManager.start();
                PlumBot.getBot().start();
                sender.sendMessage("PlumBot已重载");
                break;
            case "help":
                if (args.length != 1) return true;
                sender.sendMessage("§6PlumBot 机器人帮助菜单");
                sender.sendMessage("§6/pb reload :§f重载插件");
                sender.sendMessage("§6/pb help :§f获取插件帮助");
                sender.sendMessage("§6/pb info :§f插件基本信息");
                sender.sendMessage("§6/pb kook help :§f获取kook帮助");
                sender.sendMessage("§6/pb kook plugins :§f获取kook插件列表");
                break;
            case "kook":
                if (args.length == 1) {
                    sender.sendMessage("命令错误，格式：/plumbot kook <value>");
                    sender.sendMessage("value可选值：plugins，help");
                    return true;
                }
                if (args.length > 2) return true;
                if (args.length == 2) {
                    if (KookBot.isKookEnabled()) {
                        sender.sendMessage("kook客户端未启动");
                        return true;
                    }
                    switch (args[1]) {
                        case "plugins":
                            Plugin[] plugins = KookBot.getKookClient().getCore().getPluginManager().getPlugins();
                            String result = String.format("%s (%d): %s", sender instanceof ConsoleCommandSender ? "Installed and running plugins" : "已安装并正在运行的插件", plugins.length, String.join(", ", (Iterable) Arrays.stream(plugins).map((plugin) -> {
                                return plugin.getDescription().getName();
                            }).collect(Collectors.toSet())));
                            sender.sendMessage(result);
                            return true;
                    }
                }
                break;
            default:
                if (args.length != 1) return true;
                sender.sendMessage("错误的指令用法，请使用/pb help查看命令使用方法");
                break;
        }
        return true;
    }
}
