package sdk.event.request;

import sdk.event.global.Request;

/**
 * 加群请求
 */
public class GroupRequest extends Request {
    private String subType;//邀请还是申请
    private Long groupId;//群号
    private Long userId;//发送请求的 QQ 号
    private String comment;//验证信息
    private String flag;//请求 flag, 在调用处理请求的 API 时需要传入

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return "GroupRequest{" +
                "subType='" + subType + '\'' +
                ", groupId=" + groupId +
                ", userId=" + userId +
                ", comment='" + comment + '\'' +
                ", flag='" + flag + '\'' +
                "} " + super.toString();
    }
}
