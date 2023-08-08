package sdk.event.request;

import sdk.event.global.Request;

/**
 * 添加好友请求
 */
public class FriendRequest  extends Request {
    private String flag;
    private Long userId;
    private String comment;

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "FriendRequest{" +
                "flag='" + flag + '\'' +
                ", userId=" + userId +
                ", comment='" + comment + '\'' +
                "} " + super.toString();
    }
}
