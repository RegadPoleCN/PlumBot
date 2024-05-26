package me.regadpole.plumbot.config;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class VelocityConfig {
    private static VelocityConfig Instance;
    private final PlumBot plugin;
    private Map<String, Object> returnsObj;

    public VelocityConfig(PlumBot plugin){
        Instance = this;
        this.plugin = plugin;
        Config.PluginDir = plugin.getDataFolder();
    }

    public void loadConfig() throws IOException {
        File botFile = new File(plugin.getDataFolder(), "bot.yml");
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File returnsFile = new File(plugin.getDataFolder(), "returns.yml");
        File kook = new File(plugin.getDataFolder(), "kook");
        File kookConf = new File(kook, "kbc.yml");
        File kookPlu = new File(kook, "plugins");

        if(!Config.PluginDir.exists() && !Config.PluginDir.mkdirs()) throw new RuntimeException("Failed to create data folder!");
        File[] allFile = {botFile,configFile,returnsFile};
        for (File file : allFile) {
            if (!file.exists()) {
                try (InputStream is = plugin.getClass().getResourceAsStream("/" + file.getName())) {
                    assert is != null;
                    Files.copy(is, file.toPath());
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
            }
        }

        InputStream botIs = new FileInputStream(botFile);
        InputStream configIs = new FileInputStream(configFile);
        InputStream returnsIs = new FileInputStream(returnsFile);

        Yaml yaml = new Yaml();

        Map<String, Object> botObj = yaml.load(botIs);
        Map<String, Object> configObj = yaml.load(configIs);
        Map<String, Object> returnsObj = yaml.load(returnsIs);
        this.returnsObj = returnsObj;

        Config.bot.Ver = !Objects.isNull(botObj.get("Ver")) ? String.valueOf(botObj.get("Ver")) : "1.0";
        Map<String, Object> botMap = !Objects.isNull(botObj.get("Bot")) ? (Map<String, Object>) botObj.get("Bot") : new HashMap<>();
        Config.bot.Bot.Mode = !Objects.isNull(botMap.get("Mode")) ? String.valueOf(botMap.get("Mode")).toLowerCase() : "go-cqhttp";
        Map<String, Object> cqMap = !Objects.isNull(botMap.get("go-cqhttp")) ? (Map<String, Object>) botMap.get("go-cqhttp") : new HashMap<>();
        Config.bot.Bot.gocqhttp.HTTP = !Objects.isNull(cqMap.get("Http")) ? String.valueOf(cqMap.get("Http")) : "http://127.0.0.1:5700";
        Config.bot.Bot.gocqhttp.Token = !Objects.isNull(cqMap.get("Token")) ? String.valueOf(cqMap.get("Token")) : "";
        Config.bot.Bot.gocqhttp.IsAccessToken = !Objects.isNull(cqMap.get("IsAccessToken")) ? Boolean.parseBoolean(String.valueOf(cqMap.get("IsAccessToken"))) : false;
        Config.bot.Bot.gocqhttp.ListenPort = !Objects.isNull(cqMap.get("ListenPort")) ? Integer.parseInt(String.valueOf(cqMap.get("ListenPort"))) : 5701;
        Map<String, Object> kookMap = !Objects.isNull(botMap.get("Kook")) ? (Map<String, Object>) botMap.get("Kook") : new HashMap<>();
        Config.bot.Bot.kook.Token = !Objects.isNull(kookMap.get("Token")) ? String.valueOf(kookMap.get("Token")) : "";
        Config.bot.Groups = !Objects.isNull(botObj.get("Groups")) ? (List<Long>) botObj.get("Groups") : new ArrayList<>();
        Config.bot.Admins = !Objects.isNull(botObj.get("Admins")) ? (List<Long>) botObj.get("Admins") : new ArrayList<>();

        Config.config.Ver = !Objects.isNull(configObj.get("Ver")) ? String.valueOf(configObj.get("Ver")) : "1.0";
        Map<String, Object> forwardingMap = !Objects.isNull(configObj.get("Forwarding")) ? (Map<String, Object>) configObj.get("Forwarding") : new HashMap<>();
        Config.config.Forwarding.enable = !Objects.isNull(forwardingMap.get("enable")) ? Boolean.parseBoolean(String.valueOf(forwardingMap.get("enable"))) : true;
        Config.config.Forwarding.mode = !Objects.isNull(forwardingMap.get("mode")) ? Integer.parseInt(String.valueOf(forwardingMap.get("mode"))) : 0;
        Config.config.Forwarding.prefix = !Objects.isNull(forwardingMap.get("prefix")) ? String.valueOf(forwardingMap.get("prefix")) : "#";
        Map<String, Object> wlMap = !Objects.isNull(configObj.get("WhiteList")) ? (Map<String, Object>) configObj.get("WhiteList") : new HashMap<>();
        Config.config.WhiteList.enable = !Objects.isNull(configObj.get("enable")) ? Boolean.parseBoolean(String.valueOf(wlMap.get("enable"))) : false;
        Config.config.WhiteList.kickMsg = !Objects.isNull(configObj.get("kickMsg")) ? String.valueOf(wlMap.get("kickMsg")) : "请加入qq群:xxx申请白名单";
        Config.config.JoinAndLeave = !Objects.isNull(configObj.get("JoinAndLeave")) ? Boolean.parseBoolean(String.valueOf(configObj.get("JoinAndLeave"))) : false;
        Config.config.Online = !Objects.isNull(configObj.get("Online")) ? Boolean.parseBoolean(String.valueOf(configObj.get("Online"))) : false;
        Config.config.SDR = !Objects.isNull(configObj.get("SDR")) ? Boolean.parseBoolean(String.valueOf(configObj.get("SDR"))) : false;
        Config.config.Maven = !Objects.isNull(configObj.get("Maven")) ? String.valueOf(configObj.get("Maven")) : "https://repo1.maven.org/maven2";

        Map<String, Object> dbMap = !Objects.isNull(configObj.get("database")) ? (Map<String, Object>) configObj.get("database") : new HashMap<>();
        DbConfig.type = !Objects.isNull(dbMap.get("type")) ? String.valueOf(dbMap.get("type")) : "sqlite";
        Map<String, Object> dbsettingsMap = !Objects.isNull(dbMap.get("settings")) ? (Map<String, Object>) dbMap.get("settings") : new HashMap<>();
        Map<String, Object> sqliteMap = !Objects.isNull(dbsettingsMap.get("sqlite")) ? (Map<String, Object>) dbsettingsMap.get("sqlite") : new HashMap<>();
        DbConfig.settings.sqlite.path = (!Objects.isNull(sqliteMap.get("path")) ? String.valueOf(sqliteMap.get("path")) : "%plugin_folder%/database.db").replace("%plugin_folder%", PlumBot.INSTANCE.getDataFolder().toPath().toString());
        Map<String, Object> mysqlMap = !Objects.isNull(dbsettingsMap.get("mysql")) ? (Map<String, Object>) dbsettingsMap.get("mysql") : new HashMap<>();
        DbConfig.settings.mysql.host = !Objects.isNull(mysqlMap.get("host")) ? String.valueOf(mysqlMap.get("host")) : "localhost";
        DbConfig.settings.mysql.port = !Objects.isNull(mysqlMap.get("port")) ? String.valueOf(mysqlMap.get("port")) : "3306";
        DbConfig.settings.mysql.database = !Objects.isNull(mysqlMap.get("database")) ? String.valueOf(mysqlMap.get("database")) : "plumbot";
        DbConfig.settings.mysql.user = !Objects.isNull(mysqlMap.get("user")) ? String.valueOf(mysqlMap.get("user")) : "plumbot";
        DbConfig.settings.mysql.password = !Objects.isNull(mysqlMap.get("password")) ? String.valueOf(mysqlMap.get("password")) : "plumbot";
        DbConfig.settings.mysql.parameters = !Objects.isNull(mysqlMap.get("parameters")) ? String.valueOf(mysqlMap.get("parameters")) : "?useSSL=false";
        Map<String, Object> poolMap = !Objects.isNull(dbsettingsMap.get("pool")) ? (Map<String, Object>) dbsettingsMap.get("pool") : new HashMap<>();
        DbConfig.settings.pool.connectionTimeout = !Objects.isNull(poolMap.get("connectionTimeout")) ? Long.parseLong(String.valueOf(poolMap.get("connectionTimeout"))) : 30000;
        DbConfig.settings.pool.idleTimeout = !Objects.isNull(poolMap.get("idleTimeout")) ? Long.parseLong(String.valueOf(poolMap.get("idleTimeout"))) : 600000;
        DbConfig.settings.pool.maxLifetime = !Objects.isNull(poolMap.get("maxLifetime")) ? Long.parseLong(String.valueOf(poolMap.get("maxLifetime"))) : 1800000;
        DbConfig.settings.pool.maximumPoolSize = !Objects.isNull(poolMap.get("maximumPoolSize")) ? Integer.parseInt(String.valueOf(poolMap.get("maximumPoolSize"))) : 15;
        DbConfig.settings.pool.keepaliveTime = !Objects.isNull(poolMap.get("keepaliveTime")) ? Long.parseLong(String.valueOf(poolMap.get("keepaliveTime"))) : 0;
        DbConfig.settings.pool.minimumIdle = !Objects.isNull(poolMap.get("minimumIdle")) ? Integer.parseInt(String.valueOf(poolMap.get("minimumIdle"))) : 5;

        Config.returns.Ver = !Objects.isNull(returnsObj.get("Ver")) ? String.valueOf(returnsObj.get("Ver")) : "1.0";

        if (!"1.3.0".equals(Config.bot.Ver)){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + botFile.getName())) {
                botIs.close();
                assert is != null;
                Files.copy(is, botFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Instance.loadConfig();
            }
        }
        if (!Config.config.Ver.equals("1.2.2")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + configFile.getName())) {
                configIs.close();
                assert is != null;
                Files.copy(is, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Instance.loadConfig();
            }
        }
        if (!Config.returns.Ver.equals("1.2")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + returnsFile.getName())) {
                returnsIs.close();
                assert is != null;
                Files.copy(is, returnsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Instance.loadConfig();
            }
        }
    }

    public Map<String, Object> getReturnsObj() {
        return returnsObj;
    }

    public static void reloadConfig() throws IOException {
        Instance.loadConfig();
    }

/*    public static YamlConfiguration getBotYaml(){
        return bot;
    }

    public static YamlConfiguration getConfigYaml() {
        return config;
    }

    public static YamlConfiguration getReturnsYaml() {return returns;}

    public static YamlConfiguration getCommandsYaml() {return commands;}*/

}
