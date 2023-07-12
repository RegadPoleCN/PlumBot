package sdk.event.global;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 所有通知适用
 */
public class Message extends Event {
    private static final HandlerList handlers = new HandlerList();
    private Long time;//事件发生的时间戳
    private Long  selfId;//收到事件的机器人 QQ 号
    private String  postType;//notice	上报类型

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getSelfId() {
        return selfId;
    }

    public void setSelfId(Long selfId) {
        this.selfId = selfId;
    }

    public String getPostType() {
        return postType;
    }

    public void setPostType(String postType) {
        this.postType = postType;
    }


    @Override
    public String toString() {
        return "Message{" +
                "time=" + time +
                ", selfId=" + selfId +
                ", postType='" + postType + '\'' +
                '}';
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
