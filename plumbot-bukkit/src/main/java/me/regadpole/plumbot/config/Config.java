package me.regadpole.plumbot.config;

import me.regadpole.plumbot.PlumBot;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class Config {
    private static final PlumBot INSTANCE = PlumBot.INSTANCE;
    private static final File botFile = new File(INSTANCE.getDataFolder(), "bot.yml");
    private static final File configFile = new File(INSTANCE.getDataFolder(), "config.yml");
    private static final File returnsFile = new File(INSTANCE.getDataFolder(), "returns.yml");
    private static final File commandsFile = new File(INSTANCE.getDataFolder(), "commands.yml");
    private static final File ttf = new File(INSTANCE.getDataFolder(), "MiSans-Normal.ttf");
    private static final File kook = new File(INSTANCE.getDataFolder(), "kook");
    private static final File kookConf = new File(kook, "kbc.yml");
    private static final File kookPlu = new File(kook, "plugins");
    private static YamlConfiguration bot;
    private static YamlConfiguration config;
    private static YamlConfiguration returns;
    private static YamlConfiguration commands;

    public static void createConfig(){
        if (!kook.exists()) {
            kook.mkdirs();
        }
        if (!kookPlu.exists()) {
            kookPlu.mkdirs();
        }
        File[] allFile = {botFile,configFile,returnsFile,commandsFile, ttf};
        for (File file : allFile) {
            if (!file.exists()) {
                INSTANCE.saveResource(file.getName(), true);
            }
        }
        if (!kookConf.exists()) {
            INSTANCE.saveResource(kookConf.getParentFile().getName()+File.separator+kookConf.getName(), true);
        }
        loadConfig();
        if (!Config.getBotYamlVersion().equals("1.3.0")){
            INSTANCE.saveResource(botFile.getName(), true);
        }
        if (!Config.getConfigYamlVersion().equals("1.3.2")){
            INSTANCE.saveResource(configFile.getName(), true);
        }
        if (!Config.getCommandsYamlVersion().equals("1.2.2")){
            INSTANCE.saveResource(commandsFile.getName(), true);
        }
        if (!Config.getReturnsYamlVersion().equals("1.2")){
            INSTANCE.saveResource(configFile.getName(), true);
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

    public static void loadConfig(){
        bot = YamlConfiguration.loadConfiguration(botFile);
        config = YamlConfiguration.loadConfiguration(configFile);
        returns = YamlConfiguration.loadConfiguration(returnsFile);
        commands = YamlConfiguration.loadConfiguration(commandsFile);
    }

    public static YamlConfiguration getBotYaml(){
        return bot;
    }

    public static YamlConfiguration getConfigYaml() {
        return config;
    }

    public static YamlConfiguration getReturnsYaml() {return returns;}

    public static YamlConfiguration getCommandsYaml() {return commands;}

    public static String getBotMode() {
        return getBotYaml().getString("Bot.Mode").toLowerCase();
    }
    public static String getCqBotHttp() {
        return getBotYaml().getString("Bot.go-cqhttp.Http");
    }
    public static String getCqBotToken() {
        return getBotYaml().getString("Bot.go-cqhttp.Token");
    }
    public static boolean getCqBotIsAccessToken() {
        return getBotYaml().getBoolean("Bot.go-cqhttp.IsAccessToken");
    }
    public static int getCqBotListenPort() {
        return getBotYaml().getInt("Bot.go-cqhttp.ListenPort");
    }
    public static String getKookBotToken() {
        return getBotYaml().getString("Bot.Kook.Token");
    }

    public static List<Long> getGroupQQs(){
        return getBotYaml().getLongList("Groups");
    }

    public static List<Long> getAdmins() {
        return getBotYaml().getLongList("Admins");
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

    public static int WhiteListMaxCount(){
        return  getConfigYaml().getInt("WhiteList.maxCount");
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

}
