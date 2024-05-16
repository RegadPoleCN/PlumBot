package me.regadpole.plumbot.config;

import me.regadpole.plumbot.PlumBot;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class Config {
    private static final PlumBot INSTANCE = PlumBot.INSTANCE;
    private static final File botFile = new File(INSTANCE.getDataFolder(), "bot.yml");
    private static final File configFile = new File(INSTANCE.getDataFolder(), "config.yml");
    private static final File returnsFile = new File(INSTANCE.getDataFolder(), "returns.yml");
    private static final File kook = new File(INSTANCE.getDataFolder(), "kook");
    private static final File kookConf = new File(kook, "kbc.yml");
    private static final File kookPlu = new File(kook, "plugins");
    private static Configuration bot;
    private static Configuration config;
    private static Configuration returns;

    public static void createConfig() throws IOException {
        if (!INSTANCE.getDataFolder().exists()) {
            INSTANCE.getDataFolder().mkdir();
        }
        if (!kook.exists()) {
            kook.mkdirs();
        }
        if (!kookPlu.exists()) {
            kookPlu.mkdirs();
        }
        File[] allFile = {botFile,configFile,returnsFile};
        for (File file : allFile) {
            if (!file.exists()) {
                try (InputStream in = INSTANCE.getResourceAsStream(file.getName())) {
                    Files.copy(in, file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!kookConf.exists()) {
//            try (InputStream in = INSTANCE.getResourceAsStream(kookConf.getParentFile().getName()+File.separator+kookConf.getName())) {
            try (InputStream in = INSTANCE.getResourceAsStream(kookConf.getParentFile().getName()+"/"+kookConf.getName())) {
                Files.copy(in, kookConf.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        loadConfig();
        if (!Config.getBotYamlVersion().equals("1.3.0")){
            try (InputStream in = INSTANCE.getResourceAsStream(botFile.getName())) {
                Files.copy(in, kookConf.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!Config.getConfigYamlVersion().equals("1.3.1")){
            try (InputStream in = INSTANCE.getResourceAsStream(configFile.getName())) {
                Files.copy(in, kookConf.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!Config.getReturnsYamlVersion().equals("1.2")){
            try (InputStream in = INSTANCE.getResourceAsStream(returnsFile.getName())) {
                Files.copy(in, kookConf.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getBotYamlVersion(){
        return getBotYaml().getString("Ver");
    }

    public static String getConfigYamlVersion(){
        return getConfigYaml().getString("Ver");
    }

    public static String getReturnsYamlVersion(){
        return getReturnsYaml().getString("Ver");
    }

    public static void loadConfig() throws IOException {
        bot = ConfigurationProvider.getProvider(YamlConfiguration.class).load(botFile);;
        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        returns = ConfigurationProvider.getProvider(YamlConfiguration.class).load(returnsFile);
    }

    public static Configuration getBotYaml(){
        return bot;
    }

    public static Configuration getConfigYaml() {
        return config;
    }

    public static Configuration getReturnsYaml() {return returns;}

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

    public static boolean Forwarding(){
        return  getConfigYaml().getBoolean("Forwarding.enable");
    }

    public static boolean WhiteList(){
        return  getConfigYaml().getBoolean("WhiteList.enable");
    }

    public static boolean JoinAndLeave(){
        return  getConfigYaml().getBoolean("JoinAndLeave");
    }

    public static boolean Online(){
        return  getConfigYaml().getBoolean("Online");
    }

    public static boolean SDR() {return getConfigYaml().getBoolean("SDR");}

}
