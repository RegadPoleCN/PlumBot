package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 好友消息撤回
 */
public class FriendRecallNotice extends Notice {
    private Long userId;//好友 QQ 号
    private Long messageId;//被撤回的消息 ID

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        return "FriendRecallNotice{" +
                "userId=" + userId +
                ", messageId=" + messageId +
                "} " + super.toString();
    }
}
