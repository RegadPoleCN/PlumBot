package sdk.event.global;

/**
 * 抽取群通知
 */
public class GroupNotice extends Notice {
    private Long groupId;//群号
    private Long userId;//QQ号

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "GroupNotice{" +
                "groupId=" + groupId +
                ", userId=" + userId +
                "} " + super.toString();
    }
}
