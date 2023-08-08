package sdk.event.global;

/**
 * 所有通知适用
 */
public class Message {
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

}
