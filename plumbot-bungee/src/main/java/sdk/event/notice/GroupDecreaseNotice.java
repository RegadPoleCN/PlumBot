package sdk.event.notice;

import sdk.event.global.GroupNotice;

/**
 * 群成员减少
 */
public class GroupDecreaseNotice extends GroupNotice {
    private String subType;//leave主动退群、kick成员被踢、kick_me登录号被踢
    private Long operatorId;//操作者 QQ 号 如果是主动退群, 则和 user_id 相同

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    @Override
    public String toString() {
        return "GroupDecreaseNotice{" +
                "subType='" + subType + '\'' +
                ", operatorId=" + operatorId +
                "} " + super.toString();
    }
}


