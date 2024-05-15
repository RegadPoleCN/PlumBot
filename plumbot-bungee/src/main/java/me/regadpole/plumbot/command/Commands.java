package me.regadpole.plumbot.command;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.config.DataBase;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import snw.jkook.command.ConsoleCommandSender;
import snw.jkook.plugin.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Commands extends Command {

    private PlumBot plugin;

    public Commands(PlumBot plugin){
        super("plumbot", "plumbot.command", "pb", "PlumBot");
        this.plugin = plugin;
    }

    /**
     * @param commandSender
     * @param strings
     */
    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        if (strings.length == 0) {
            commandSender.sendMessage("请使用/pb help查看命令使用方法");
            return;
        }

        switch (strings[0]) {
            case "info":
                StringBuilder stringbuilder = new StringBuilder();
                stringbuilder.append("§1---PlumBot 信息---").append("\n");
                stringbuilder.append("§a作者: ").append("§f").append(plugin.getEnvironment().author).append("\n");
                stringbuilder.append("§a版本: ").append("§f").append(plugin.getEnvironment().version).append("\n");
                stringbuilder.append("§a机器人平台: ").append("§f").append(Config.getBotMode()).append("\n");
                stringbuilder.append("§a数据库模式: ").append("§f").append(DataBase.type()).append("\n");
                stringbuilder.append("§a服务端版本: ").append("§f").append(plugin.getProxy().getVersion()).append("\n");
                commandSender.sendMessage(stringbuilder.toString());
                break;
            case "reload":
                if (strings.length != 1) return;
                DatabaseManager.close();
                PlumBot.getBot().shutdown();
                try {
                    Config.loadConfig();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                DatabaseManager.start();
                PlumBot.getBot().start();
                commandSender.sendMessage("PlumBot已重载");
                break;
            case "help":
                if (strings.length != 1) return;
                commandSender.sendMessage("§6PlumBot 机器人帮助菜单");
                commandSender.sendMessage("§6/pb reload :§f重载插件");
                commandSender.sendMessage("§6/pb help :§f获取插件帮助");
                commandSender.sendMessage("§6/pb info :§f插件基本信息");
                commandSender.sendMessage("§6/pb kook help :§f获取kook帮助");
                commandSender.sendMessage("§6/pb kook plugins :§f获取kook插件列表");
                break;
            case "kook":
                if (strings.length == 1) {
                    commandSender.sendMessage("命令错误，格式：/plumbot kook <value>");
                    commandSender.sendMessage("value可选值：plugins，help");
                    return;
                }
                if (strings.length > 2) return;
                if (strings.length == 2) {
                    if (KookBot.isKookEnabled()) {
                        commandSender.sendMessage("kook客户端未启动");
                        return;
                    }
                    switch (strings[1]) {
                        case "plugins":
                            Plugin[] plugins = KookBot.getKookClient().getCore().getPluginManager().getPlugins();
                            String result = String.format("%s (%d): %s", commandSender instanceof ConsoleCommandSender ? "Installed and running plugins" : "已安装并正在运行的插件", plugins.length, String.join(", ", (Iterable) Arrays.stream(plugins).map((plugin) -> {
                                return plugin.getDescription().getName();
                            }).collect(Collectors.toSet())));
                            commandSender.sendMessage(result);
                            return;
                    }
                }
                break;
            default:
                if (strings.length != 1) return;
                commandSender.sendMessage("错误的指令用法，请使用/pb help查看命令使用方法");
                break;
        }
    }
}
