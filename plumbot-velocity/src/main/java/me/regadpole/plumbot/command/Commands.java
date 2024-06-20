package me.regadpole.plumbot.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.config.VelocityConfig;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import snw.jkook.command.ConsoleCommandSender;
import snw.jkook.plugin.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Commands implements SimpleCommand {

    private  final PlumBot plugin;

    public Commands(PlumBot plugin){
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        // Get the arguments after the command alias
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("请使用/pb help查看帮助"));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "info":
                StringBuilder stringbuilder = new StringBuilder();
                stringbuilder.append("§1---PlumBot 信息---").append("\n");
                stringbuilder.append("§a作者: ").append("§f").append(plugin.getEnvironment().author).append("\n");
                stringbuilder.append("§a版本: ").append("§f").append(plugin.getEnvironment().version).append("\n");
                stringbuilder.append("§a机器人平台: ").append("§f").append(Config.bot.Bot.Mode).append("\n");
                stringbuilder.append("§a数据库模式: ").append("§f").append(DbConfig.type).append("\n");
                stringbuilder.append("§a服务端版本: ").append("§f").append(plugin.getServer().getVersion().toString()).append("\n");
                source.sendMessage(Component.text(stringbuilder.toString()));
                break;
            case "reload": {
                if (source.hasPermission("plumbot.command")) {
                    try {
                        DatabaseManager.close();
                        PlumBot.getBot().shutdown();
                        plugin.vconf = new VelocityConfig(plugin);
                        plugin.vconf.loadConfig();
                        DatabaseManager.start();
                        PlumBot.getBot().start();
                        source.sendMessage(Component.text("配置文件已重新加载"));
                    } catch (IOException e) {
                        e.printStackTrace();
                        source.sendMessage(Component.text("配置文件加载时出错").color(NamedTextColor.RED));
                    }
                }else source.sendMessage(Component.text("你没有权限执行此命令"));
                break;
            }
            case "help": {
                source.sendMessage(Component.text("§6PlumBot 机器人帮助菜单"));
                source.sendMessage(Component.text("§6/pb reload :§f重载插件"));
                source.sendMessage(Component.text("§6/pb info :§f插件基本信息"));
                source.sendMessage(Component.text("§6/pb help :§f获取插件帮助"));
                break;
            }
            case "kook":
                if (args.length == 1) {
                    source.sendMessage(Component.text("命令错误，格式：/plumbot kook <value>"));
                    source.sendMessage(Component.text("value可选值：plugins，help"));
                    break;
                }
                if (args.length > 2) break;
                if (args.length == 2) {
                    if (KookBot.isKookEnabled()) {
                        source.sendMessage(Component.text("kook客户端未启动"));
                        break;
                    }
                    switch (args[1]) {
                        case "plugins":
                            Plugin[] plugins = KookBot.getKookClient().getCore().getPluginManager().getPlugins();
                            String result = String.format("%s (%d): %s", source instanceof ConsoleCommandSender ? "Installed and running plugins" : "已安装并正在运行的插件", plugins.length, String.join(", ", (Iterable) Arrays.stream(plugins).map((plugin) -> {
                                return plugin.getDescription().getName();
                            }).collect(Collectors.toSet())));
                            source.sendMessage(Component.text(result));
                            break;
                    }
                }
                break;
            default: {
                source.sendMessage(Component.text("错误的指令用法，请使用/pb help查看命令使用方法"));
            }
        }
    }
}
