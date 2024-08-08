package me.regadpole.plumbot.config;

import me.regadpole.plumbot.LifeCycle;

import java.io.File;
import java.util.List;

public class Configuration {
    public static File PluginDir = LifeCycle.getPlugin().getDataFolder();

    public static class bot{ // bot
        public static String Ver; // version

        public static class Bot{
            public static String Mode; // bot's mode
            public static class gocqhttp{
                public static String HTTP; // bot's HTTP
                public static String Token; // bot's Token
                public static boolean IsAccessToken; // bot's IsAccessToken
                public static int ListenPort; // bot's ListenPort
            }
            public static class kook{
                public static String Token; // bot's Token
            }
        }
        public static List<Long> Groups; // group's number
        public static List<Long> Admins; // admin's QQ
    }

    public static class config{ // bot
        public static String Ver; // version
        public static String Prefix; // Command prefix
        public static boolean DieReport; // Die Reports
        public static boolean TPS;
        public static class Forwarding {
            public static boolean enable;
            public static int mode;
            public static String prefix;
        }
        public static class WhiteList{
            public static boolean enable;
            public static String kickMsg;
            public static int maxCount;
        }
        public static boolean JoinAndLeave; // 进出游戏
        public static boolean Online; // 在线人数
        public static boolean CMD;
        public static boolean SDC;
        public static boolean SDR; // 自定义回复
        public static String Maven; // Maven仓库地址

    }

    public static class returns{ // returns
        public static String Ver; // version
    }
    public static class commands{ // commands
        public static String Ver; // version

    }
    public static class Database{
        public static String type;
        public static class settings{
            public static class sqlite{
                public static String path;
            }
            public static class mysql{
                public static String host;
                public static String port;
                public static String database;
                public static String user;
                public static String password;
                public static String parameters;
            }
            public static class pool{
                public static long connectionTimeout;
                public static long idleTimeout;
                public static long maxLifetime;
                public static int maximumPoolSize;
                public static long keepaliveTime;
                public static int minimumIdle;
                public static String connectionTestQuery;
            }
        }
    }

}
