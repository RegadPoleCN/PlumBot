package org.linear.linearbot.event.server;

import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Supplier;

public class ServerTps {

    /** Number formatter for 2 decimal places */
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.CHINA);

    /** NMS server to get TPS from on spigot */
    private Object server;

    /** TPS field*/
    private Field recentTps;

    /** Detection for presence of Paper's TPS getter */
    private Method paperTps;

    /** Detection for presence of Paper's MSPT getter */
    private Method paperMspt;

    private Supplier<Object> TPS;

    private String MSPT = "none";

    public ServerTps() {
        try {
            server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            recentTps = server.getClass().getField("recentTps");
        } catch (ReflectiveOperationException e) {
            //not spigot
        }
        try { paperTps = Bukkit.class.getMethod("getTPS"); } catch (NoSuchMethodException ignored) {}
        try { paperMspt = Bukkit.class.getMethod("getAverageTickTime"); } catch (NoSuchMethodException ignored) {}
        TPS = () -> {
            if (paperTps != null) {
                return formatTPS(Bukkit.getTPS()[0]);
            } else if (recentTps != null) {
                try {
                    return formatTPS(((double[]) recentTps.get(server))[0]);
                } catch (IllegalAccessException e) {
                    return String.valueOf(-1);
                }
            } else {
                return String.valueOf(-1);
            }
        };
        if (paperMspt != null) {
            MSPT = numberFormat.format(Bukkit.getAverageTickTime());
        }
    }

    private String formatTPS(double tps) {
        return numberFormat.format(Math.min(20, tps));
    }

    public Object getTps() {return TPS.get();}

    public String getMSPT() {return MSPT;}

}
