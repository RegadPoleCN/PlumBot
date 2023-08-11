package me.regadpole.plumbot.config;

public class Args {

    public static int ForwardingMode(){
        return Config.getConfigYaml().getInt("Forwarding.mode");
    }

    public static String ForwardingPrefix(){
        return Config.getConfigYaml().getString("Forwarding.prefix");
    }

    public static String Prefix(){
        return Config.getConfigYaml().getString("Prefix");
    }

    public static String Maven() {return Config.getConfigYaml().getString("Maven");}

}
