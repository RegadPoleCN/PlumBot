package me.regadpole.plumbot.internal;

import me.regadpole.plumbot.PlumBot;

public final class Environment {
    private final PlumBot plugin = PlumBot.INSTANCE;
    public final String name = plugin.getDescription().getName();
    public final String version = plugin.getDescription().getVersion();
    public final String author = plugin.getDescription().getAuthor();
}
