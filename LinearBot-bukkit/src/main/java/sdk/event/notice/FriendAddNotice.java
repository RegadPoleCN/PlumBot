package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 新增好友
 */
public class FriendAddNotice extends Notice {
    private Long userId;//新添加好友 QQ 号

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "FriendAddNotice{" +
                "userId=" + userId +
                "} " + super.toString();
    }
}
