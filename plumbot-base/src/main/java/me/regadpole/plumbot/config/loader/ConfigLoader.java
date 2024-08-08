package me.regadpole.plumbot.config.loader;

import lombok.NonNull;
import me.regadpole.plumbot.LifeCycle;
import me.regadpole.plumbot.Plugin;
import me.regadpole.plumbot.config.Configuration;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class ConfigLoader {
    private File dataFolder;
    private final Plugin plugin;

    public ConfigLoader(@NonNull File folder) {
        dataFolder = folder;
        this.plugin = LifeCycle.getPlugin();
    }

    private final File botFile = new File(dataFolder, "bot.yml");
    private final File configFile = new File(dataFolder, "config.yml");
    private final File returnsFile = new File(dataFolder, "returns.yml");
    private final File commandsFile = new File(dataFolder, "commands.yml");
    private final File ttf = new File(dataFolder, "MiSans-Normal.ttf");
    private final File kook = new File(dataFolder, "kook");
    private final File kookConf = new File(kook, "kbc.yml");
    private final File kookPlu = new File(kook, "plugins");
    private YamlConfiguration bot;
    private YamlConfiguration config;
    private YamlConfiguration returns;
    private YamlConfiguration commands;

    public void createConfig(){

        if(!dataFolder.exists() && !dataFolder.mkdirs()) throw new RuntimeException("Failed to create data folder!");
        File[] allFile = {botFile,configFile,returnsFile,commandsFile,ttf};
        for (File file : allFile) {
            if (!file.exists()) {
                try (InputStream is = plugin.getClass().getResourceAsStream("/" + file.getName())) {
                    assert is != null;
                    Files.copy(is, file.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (!kook.exists()) {
            kook.mkdirs();
        }
        if (!kookPlu.exists()) {
            kookPlu.mkdirs();
        }
        if (!kookConf.exists()) {
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + kookConf.getParentFile().getName() + "/" + kookConf.getName())) {
                assert is != null;
                Files.copy(is, kookConf.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        loadConfig();
        if (!Configuration.bot.Ver.equals("1.3.0")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + botFile.getName())) {
                assert is != null;
                Files.copy(is, botFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                loadConfig();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!Configuration.config.Ver.equals("1.3.3")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + configFile.getName())) {
                assert is != null;
                Files.copy(is, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                loadConfig();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!Configuration.commands.Ver.equals("1.2.2")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + commandsFile.getName())) {
                assert is != null;
                Files.copy(is, commandsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                loadConfig();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!Configuration.returns.Ver.equals("1.2")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + returnsFile.getName())) {
                assert is != null;
                Files.copy(is, returnsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                loadConfig();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public YamlConfiguration getBotYaml(){
        return bot;
    }
    public YamlConfiguration getConfigYaml() {
        return config;
    }
    public YamlConfiguration getReturnsYaml() {return returns;}
    public YamlConfiguration getCommandsYaml() {return commands;}

    public void loadConfig(){
        bot = YamlConfiguration.loadConfiguration(botFile);
        config = YamlConfiguration.loadConfiguration(configFile);
        returns = YamlConfiguration.loadConfiguration(returnsFile);
        commands = YamlConfiguration.loadConfiguration(commandsFile);

        Configuration.bot.Ver = getBotYaml().getString("Ver");
        Configuration.bot.Bot.Mode = getBotYaml().getString("Bot.Mode").toLowerCase();
        Configuration.bot.Bot.gocqhttp.HTTP = getBotYaml().getString("Bot.go-cqhttp.Http");
        Configuration.bot.Bot.gocqhttp.Token = getBotYaml().getString("Bot.go-cqhttp.Token");
        Configuration.bot.Bot.gocqhttp.IsAccessToken = getBotYaml().getBoolean("Bot.go-cqhttp.IsAccessToken");
        Configuration.bot.Bot.gocqhttp.ListenPort = getBotYaml().getInt("Bot.go-cqhttp.ListenPort");
        Configuration.bot.Bot.kook.Token = getBotYaml().getString("Bot.Kook.Token");
        Configuration.bot.Groups = getBotYaml().getLongList("Groups");
        Configuration.bot.Admins = getBotYaml().getLongList("Admins");

        Configuration.config.Ver = getConfigYaml().getString("Ver");
        Configuration.config.Prefix = getConfigYaml().getString("Prefix");
        Configuration.config.DieReport = getConfigYaml().getBoolean("DieReport");
        Configuration.config.TPS = getConfigYaml().getBoolean("TPS");
        Configuration.config.Forwarding.enable = getConfigYaml().getBoolean("Forwarding.enable");
        Configuration.config.Forwarding.mode = getConfigYaml().getInt("Forwarding.mode");
        Configuration.config.Forwarding.prefix =getConfigYaml().getString("Forwarding.prefix");
        Configuration.config.WhiteList.enable =  getConfigYaml().getBoolean("WhiteList.enable");
        Configuration.config.WhiteList.kickMsg = getConfigYaml().getString("WhiteList.kickMsg");
        Configuration.config.WhiteList.maxCount = getConfigYaml().getInt("WhiteList.maxCount");
        Configuration.config.JoinAndLeave = getConfigYaml().getBoolean("JoinAndLeave");
        Configuration.config.Online =getConfigYaml().getBoolean("Online");
        Configuration.config.CMD = getConfigYaml().getBoolean("CMD");
        Configuration.config.SDC = getConfigYaml().getBoolean("SDC");
        Configuration.config.SDR = getConfigYaml().getBoolean("SDR");
        Configuration.config.Maven = getConfigYaml().getString("Maven");

        Configuration.returns.Ver = getReturnsYaml().getString("Ver");
        Configuration.commands.Ver = getCommandsYaml().getString("Ver");

        Configuration.Database.type = getConfigYaml().getString("database.type");
        Configuration.Database.settings.sqlite.path = getConfigYaml().getString("database.settings.sqlite.path").replace("%plugin_folder%", LifeCycle.getPlugin().getDataFolder().toPath().toString());
        Configuration.Database.settings.mysql.host = getConfigYaml().getString("database.settings.mysql.host");
        Configuration.Database.settings.mysql.port = getConfigYaml().getString("database.settings.mysql.port");
        Configuration.Database.settings.mysql.database = getConfigYaml().getString("database.settings.mysql.database");
        Configuration.Database.settings.mysql.user = getConfigYaml().getString("database.settings.mysql.user");
        Configuration.Database.settings.mysql.password = getConfigYaml().getString("database.settings.mysql.password");
        Configuration.Database.settings.mysql.parameters = getConfigYaml().getString("database.settings.mysql.parameters");
        Configuration.Database.settings.pool.connectionTimeout = getConfigYaml().getLong("database.settings.pool.connectionTimeout");
        Configuration.Database.settings.pool.idleTimeout = getConfigYaml().getLong("database.settings.pool.idleTimeout");
        Configuration.Database.settings.pool.maxLifetime = getConfigYaml().getLong("database.settings.pool.maxLifetime");
        Configuration.Database.settings.pool.maximumPoolSize = getConfigYaml().getInt("database.settings.pool.maximumPoolSize");
        Configuration.Database.settings.pool.keepaliveTime = getConfigYaml().getLong("database.settings.pool.keepaliveTime");
        Configuration.Database.settings.pool.minimumIdle = getConfigYaml().getInt("database.settings.pool.minimumIdle");
        Configuration.Database.settings.pool.connectionTestQuery = getConfigYaml().getString("database.settings.pool.connectionTestQuery", "");
    }

    public void reloadConfig() throws IOException {
        loadConfig();
    }
}
