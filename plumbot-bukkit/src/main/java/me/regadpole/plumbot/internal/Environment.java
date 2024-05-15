package me.regadpole.plumbot.internal;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.hook.AuthMeHook;
import me.regadpole.plumbot.hook.GriefDefenderHook;
import me.regadpole.plumbot.hook.QuickShopHook;
import me.regadpole.plumbot.hook.ResidenceHook;

import java.util.Iterator;

public final class Environment {
    private final PlumBot plugin = PlumBot.INSTANCE;
    public final String name = plugin.getDescription().getName();
    public final String version = plugin.getDescription().getVersion();
    public final String author = getAuthorString();
    public final String authme = getPluginHooked(AuthMeHook.hasAuthMe);
    public final String griefdefender = getPluginHooked(GriefDefenderHook.hasGriefDefender);
    public final String quickshop = getPluginHooked(QuickShopHook.hasQs);
    public final String quickshophikari = getPluginHooked(QuickShopHook.hasQsHikari);
    public final String residence = getPluginHooked(ResidenceHook.hasRes);

    private String getPluginHooked(boolean hooked){
        if (hooked){
            return "§2True";
        } else {
            return "§cFalse";
        }
    }

    private String getAuthorString() {
        Iterator<String> authors = plugin.getDescription().getAuthors().iterator();
        StringBuilder stringBuilder = new StringBuilder();
        while(authors.hasNext()){
            stringBuilder.append(authors.next()).append(", ");
        }
        return stringBuilder.substring(0, stringBuilder.lastIndexOf(",")-1);
    }
}
