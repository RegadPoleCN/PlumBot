package me.regadpole.plumbot.event.server;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class ConsoleSender implements ConsoleCommandSender {

    private final Server server = Bukkit.getServer();

    public Server getServer() {
        return this.server;
    }

    public String getName() {
        return "CONSOLE";
    }

    public void sendMessage(String message) {
        ServerManager.msgList.add(message);
    }

    public void sendMessage(String[] messages) {
        for (String msg : messages)
            sendMessage(msg);
    }

    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        ServerManager.msgList.add(message);
    }

    public void sendMessage(@Nullable UUID sender, @NotNull String[] messages) {
        for (String msg : messages)
            sendMessage(null, messages);
    }

    public boolean isPermissionSet(String s) {
        return false;
    }

    public boolean isPermissionSet(Permission permission) {
        return false;
    }

    public boolean hasPermission(String s) {
        return true;
    }

    public boolean hasPermission(Permission permission) {
        return true;
    }

    public boolean isOp() {
        return true;
    }

    public void setOp(boolean b) {
        throw new UnsupportedOperationException();
    }

    public CommandSender.Spigot spigot() {
        throw new UnsupportedOperationException();
    }

    public boolean isConversing() {
        throw new UnsupportedOperationException();
    }

    public void acceptConversationInput(String s) {
        ServerManager.msgList.add(s);
        throw new UnsupportedOperationException();
    }

    public boolean beginConversation(Conversation conversation) {
        throw new UnsupportedOperationException();
    }

    public void abandonConversation(Conversation conversation) {
        throw new UnsupportedOperationException();
    }

    public void abandonConversation(Conversation conversation, ConversationAbandonedEvent conversationAbandonedEvent) {
        throw new UnsupportedOperationException();
    }

    public void sendRawMessage(String s) {
        ServerManager.msgList.add(s);
    }

    public void sendRawMessage(@Nullable UUID sender, @NotNull String message) {
        ServerManager.msgList.add(message);
    }

    public PermissionAttachment addAttachment(Plugin plugin, String s, boolean b) {
        ServerManager.msgList.add(s);
        throw new UnsupportedOperationException();
    }

    public PermissionAttachment addAttachment(Plugin plugin) {
        throw new UnsupportedOperationException();
    }

    public PermissionAttachment addAttachment(Plugin plugin, String s, boolean b, int i) {
        ServerManager.msgList.add(s);
        throw new UnsupportedOperationException();
    }

    public PermissionAttachment addAttachment(Plugin plugin, int i) {
        throw new UnsupportedOperationException();
    }

    public void removeAttachment(PermissionAttachment permissionAttachment) {
        throw new UnsupportedOperationException();
    }

    public void recalculatePermissions() {
        throw new UnsupportedOperationException();
    }

    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        throw new UnsupportedOperationException();
    }

}
