package me.regadpole.plumbot.bot;

import snw.jkook.message.component.BaseComponent;

public interface Bot {
    void start();
    void shutdown();
    void sendMsg(boolean isGroup, String message, long id);
    default void sendMsg(boolean isGroup, BaseComponent message, long id) {}
    default void reply(){}
    String getGroupName(long groupId);
}
