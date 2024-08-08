package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 好友戳一戳
 */
public class FriendPokeNotice extends Notice {
    private String subType;//poke	提示类型
    private Long senderId;//发送者 QQ 号
    private Long userId;//发送者 QQ 号
    private Long targetId;//被戳者 QQ 号

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    @Override
    public String toString() {
        return "FriendPokeNotice{" +
                "subType='" + subType + '\'' +
                ", senderId=" + senderId +
                ", userId=" + userId +
                ", targetId=" + targetId +
                "} " + super.toString();
    }
}
