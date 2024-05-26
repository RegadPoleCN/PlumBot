package me.regadpole.plumbot.event.server;

import me.regadpole.plumbot.PlumBot;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class ServerManager {

    public static String listOnlinePlayer() {
        List<String> onlinePlayer = new LinkedList<>();
        for (ProxiedPlayer p : PlumBot.INSTANCE.getProxy().getPlayers()) {
            onlinePlayer.add(p.getName());
        }
        return Arrays.toString(onlinePlayer.toArray()).replace("\\[|\\]", "");
    }

    public static ProxiedPlayer getServerPlayer(InetSocketAddress address) {
        LinkedHashMap<InetSocketAddress, ProxiedPlayer> playerConnectionMap = new LinkedHashMap<>();
        for (ProxiedPlayer p : PlumBot.INSTANCE.getProxy().getPlayers()) {
            playerConnectionMap.put(p.getAddress(), p);
        }
        return playerConnectionMap.get(address);
    }
}
