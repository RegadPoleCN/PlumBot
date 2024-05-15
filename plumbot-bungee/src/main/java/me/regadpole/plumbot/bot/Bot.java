package me.regadpole.plumbot.bot;

public interface Bot {
    void start();
    void shutdown();
    void sendMsg(boolean isGroup, String message, long id);
    String getGroupName(long groupId);
    boolean checkUserInGroup(long userId, long groupId);
}
