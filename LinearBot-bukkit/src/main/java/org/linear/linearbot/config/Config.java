package org.linear.linearbot.config;

import org.linear.linearbot.LinearBot;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class Config {
    private static final LinearBot INSTANCE = LinearBot.INSTANCE;
    private static final File botFile = new File(INSTANCE.getDataFolder(), "bot.yml");
    private static final File configFile = new File(INSTANCE.getDataFolder(), "config.yml");
    private static final File returnsFile = new File(INSTANCE.getDataFolder(), "returns.yml");
    private static final File commandsFile = new File(INSTANCE.getDataFolder(), "commands.yml");
    private static final File whitelistFile = new File(INSTANCE.getDataFolder(), "whitelist.yml");
    private static final File qqlistFile = new File(INSTANCE.getDataFolder(), "qqlist.yml");
    private static YamlConfiguration bot;
    private static YamlConfiguration config;
    private static YamlConfiguration returns;
    private static YamlConfiguration commands;
    private static YamlConfiguration whitelist;
    private static YamlConfiguration qqlist;

    public static void createConfig(){
        File[] allFile = {qqlistFile,botFile,configFile,returnsFile,commandsFile,whitelistFile};
        for (File file : allFile) {
            if (!file.exists()) {
                INSTANCE.saveResource(file.getName(), true);
            }
        }
        loadConfig();
        if (!Config.getBotYamlVersion().equals("1.1")){
            INSTANCE.saveResource(botFile.getName(), true);
        }
        if (!Config.getConfigYamlVersion().equals("1.2.2")){
            INSTANCE.saveResource(configFile.getName(), true);
        }
        if (!Config.getCommandsYamlVersion().equals("1.2.2")){
            INSTANCE.saveResource(commandsFile.getName(), true);
        }
        if (!Config.getReturnsYamlVersion().equals("1.2")){
            INSTANCE.saveResource(configFile.getName(), true);
        }
        if (!Config.getWhitelistYamlVersion().equals("1.2.2")){
            INSTANCE.saveResource(whitelistFile.getName(), true);
        }
        if (!Config.getQQlistYamlVersion().equals("1.2.2")){
            INSTANCE.saveResource(qqlistFile.getName(), true);
        }
    }

    public static String getBotYamlVersion(){
        return getBotYaml().getString("Ver");
    }

    public static String getConfigYamlVersion(){
        return getConfigYaml().getString("Ver");
    }

    public static String getCommandsYamlVersion(){
        return getCommandsYaml().getString("Ver");
    }

    public static String getReturnsYamlVersion(){
        return getReturnsYaml().getString("Ver");
    }

    public static String getWhitelistYamlVersion(){
        return getReturnsYaml().getString("Ver");
    }

    public static String getQQlistYamlVersion(){ return getQQListYaml().getString("Ver"); }

    public static void loadConfig(){
        bot = YamlConfiguration.loadConfiguration(botFile);
        config = YamlConfiguration.loadConfiguration(configFile);
        returns = YamlConfiguration.loadConfiguration(returnsFile);
        commands = YamlConfiguration.loadConfiguration(commandsFile);
        whitelist = YamlConfiguration.loadConfiguration(whitelistFile);
        qqlist = YamlConfiguration.loadConfiguration(qqlistFile);
    }

    public static YamlConfiguration getBotYaml(){
        return bot;
    }

    public static YamlConfiguration getConfigYaml() {
        return config;
    }

    public static YamlConfiguration getReturnsYaml() {return returns;}

    public static YamlConfiguration getCommandsYaml() {return commands;}

    public static YamlConfiguration getWhitelistYaml() {return whitelist;}

    public static YamlConfiguration getQQListYaml() {return qqlist;}

    public static String getBotHttp() {
        return getBotYaml().getString("Bot.Http");
    }
    public static String getBotToken() {
        return getBotYaml().getString("Bot.Token");
    }
    public static boolean getBotIsAccessToken() {
        return getBotYaml().getBoolean("Bot.IsAccessToken");
    }
    public static int getBotListenPort() {
        return getBotYaml().getInt("Bot.ListenPort");
    }
    public static List<Long> getGroupQQs(){
        return getBotYaml().getLongList("Groups");
    }

    public static List<Long> getAdmins() {
        return Config.getBotYaml().getLongList("Admins");
    }

    public static boolean DieReport(){
        return  getConfigYaml().getBoolean("DieReport");
    }

    public static boolean Forwarding(){
        return  getConfigYaml().getBoolean("Forwarding.enable");
    }

    public static boolean WhiteList(){
        return  getConfigYaml().getBoolean("WhiteList.enable");
    }

    public static boolean JoinAndLeave(){
        return  getConfigYaml().getBoolean("JoinAndLeave");
    }

    public static boolean CMD(){
        return  getConfigYaml().getBoolean("CMD");
    }

    public static boolean Online(){
        return  getConfigYaml().getBoolean("Online");
    }

    public static boolean TPS() {return getConfigYaml().getBoolean("TPS");}

    public static boolean SDC() {return getConfigYaml().getBoolean("SDC");}

    public static boolean SDR() {return getConfigYaml().getBoolean("SDR");}

    public static File WhitelistFile() {return whitelistFile;}
    
    public static File QQListFile() {return qqlistFile;}

}
