package me.regadpole.plumbot;

import lombok.Getter;

public class LifeCycle {
    @Getter
    private static Plugin plugin;

    public LifeCycle(Plugin plugin) {
        LifeCycle.plugin = plugin;
    }

}
