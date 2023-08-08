package me.regadpole.plumbot.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import me.regadpole.plumbot.PlumBot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.regadpole.plumbot.config.VelocityConfig;

import java.io.IOException;

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
        } else if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload": {
                    if (source.hasPermission("plumbot.command")) {
                        try {
                            plugin.vconf = new VelocityConfig(plugin);
                            plugin.vconf.loadConfig();
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
                    source.sendMessage(Component.text("§6/pb help :§f获取插件帮助"));
                    break;
                }
                default: {
                    source.sendMessage(Component.text("请使用/pb help查看帮助"));
                }
            }
        }else {
            source.sendMessage(Component.text("请使用/pb help查看帮助"));
        }

    }
}
