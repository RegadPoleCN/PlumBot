package sdk.event.notice;

import sdk.event.global.GroupNotice;

/**
 * 群红包运气王提示
 */
public class GroupLuckyKingNotice extends GroupNotice {
    private String subType;//lucky_king	提示类型
    private Long targetId;//运气王id

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    @Override
    public String toString() {
        return "GroupLuckyKingNotice{" +
                "subType='" + subType + '\'' +
                ", targetId=" + targetId +
                "} " + super.toString();
    }
}
