package org.linear.linearbot.config;

import com.alibaba.fastjson.JSONArray;
import org.linear.linearbot.LinearBot;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.linear.linearbot.internal.Config.*;

public class VelocityConfig {
    private static VelocityConfig Instance;
    private final LinearBot plugin;
    private Map<String, Object> returnsObj;

    public VelocityConfig(LinearBot plugin){
        Instance = this;
        this.plugin = plugin;
        PluginDir = plugin.getDataFolder();
    }

    public void loadConfig() throws IOException {
        File botFile = new File(plugin.getDataFolder(), "bot.yml");
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File returnsFile = new File(plugin.getDataFolder(), "returns.yml");

        if(!PluginDir.exists() && !PluginDir.mkdirs()) throw new RuntimeException("Failed to create data folder!");
        File[] allFile = {botFile,configFile,returnsFile};
        for (File file : allFile) {
            if (!file.exists()) {
                try (InputStream is = plugin.getClass().getResourceAsStream("/" + file.getName())) {
                    assert is != null;
                    Files.copy(is, file.toPath());
                }
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

        bot.Ver = !Objects.isNull(botObj.get("Ver")) ? String.valueOf(botObj.get("Ver")) : "1.0";
        Map<String, Object> botMap = !Objects.isNull(botObj.get("Bot")) ? (Map<String, Object>) botObj.get("Bot") : new HashMap<>();
        bot.Bot.QQ = !Objects.isNull(botMap.get("QQ")) ? Long.parseLong(String.valueOf(botMap.get("QQ"))) : 111;
        bot.Groups = !Objects.isNull(botObj.get("Groups")) ? JSONArray.parseArray(botObj.get("Groups").toString(), Long.class) : new ArrayList<>();
        bot.Admins = !Objects.isNull(botObj.get("Admins")) ? JSONArray.parseArray(botObj.get("Admins").toString(), Long.class) : new ArrayList<>();

        config.Ver = !Objects.isNull(configObj.get("Ver")) ? String.valueOf(configObj.get("Ver")) : "1.0";
        config.Forwarding = !Objects.isNull(configObj.get("Forwarding")) ? Boolean.parseBoolean(String.valueOf(configObj.get("Forwarding"))) : false;
        config.JoinAndLeave = !Objects.isNull(configObj.get("JoinAndLeave")) ? Boolean.parseBoolean(String.valueOf(configObj.get("JoinAndLeave"))) : false;
        config.Online = !Objects.isNull(configObj.get("Online")) ? Boolean.parseBoolean(String.valueOf(configObj.get("Online"))) : false;
        config.SDR = !Objects.isNull(configObj.get("SDR")) ? Boolean.parseBoolean(String.valueOf(configObj.get("SDR"))) : false;

        returns.Ver = !Objects.isNull(returnsObj.get("Ver")) ? String.valueOf(returnsObj.get("Ver")) : "1.0";

        if (!"1.1".equals(bot.Ver)){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + botFile.getName())) {
                botIs.close();
                assert is != null;
                Files.copy(is, botFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Instance.loadConfig();
            }
        }
        if (!config.Ver.equals("1.2")){
            try (InputStream is = plugin.getClass().getResourceAsStream("/" + configFile.getName())) {
                configIs.close();
                assert is != null;
                Files.copy(is, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Instance.loadConfig();
            }
        }
        if (!returns.Ver.equals("1.2")){
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
