package me.regadpole.plumbot.config;

import me.regadpole.plumbot.PlumBot;

public class DataBase {

    public static String type() {return Config.getConfigYaml().getString("database.type");}
    public static String sqlite_path() {return Config.getConfigYaml().getString("database.settings.sqlite.path").replace("%plugin_folder%", PlumBot.INSTANCE.getDataFolder().toPath().toString());}
    public static String mysql_host() {return Config.getConfigYaml().getString("database.settings.mysql.host");}
    public static String mysql_port() {return Config.getConfigYaml().getString("database.settings.mysql.port");}
    public static String mysql_database() {return Config.getConfigYaml().getString("database.settings.mysql.database");}
    public static String mysql_user() {return Config.getConfigYaml().getString("database.settings.mysql.user");}
    public static String mysql_password() {return Config.getConfigYaml().getString("database.settings.mysql.password");}
    public static String mysql_parameters() {return Config.getConfigYaml().getString("database.settings.mysql.parameters");}
    public static long pool_connectionTimeout() {return Config.getConfigYaml().getLong("database.settings.pool.connectionTimeout");}
    public static long pool_idleTimeout() {return Config.getConfigYaml().getLong("database.settings.pool.idleTimeout");}
    public static long pool_maxLifetime() {return Config.getConfigYaml().getLong("database.settings.pool.maxLifetime");}
    public static int pool_maximumPoolSize() {return Config.getConfigYaml().getInt("database.settings.pool.maximumPoolSize");}
    public static long pool_keepaliveTime() {return Config.getConfigYaml().getLong("database.settings.pool.keepaliveTime");}
    public static int pool_minimumIdle() {return Config.getConfigYaml().getInt("database.settings.pool.minimumIdle");}
    public static String pool_connectionTestQuery() {return Config.getConfigYaml().getString("database.settings.pool.connectionTestQuery", "");}

}
