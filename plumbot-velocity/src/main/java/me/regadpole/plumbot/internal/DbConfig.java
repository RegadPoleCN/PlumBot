package me.regadpole.plumbot.internal;

public class DbConfig {
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
        }
    }
}
