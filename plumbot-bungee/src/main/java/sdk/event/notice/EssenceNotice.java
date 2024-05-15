package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 精华消息
 */
public class EssenceNotice extends Notice {
    private String  subType;//add,delete	添加为add,移出为delete
    private Long  senderId	;//消息发送者ID
    private Long operatorId;//操作者ID
    private Long messageId	;//消息ID

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

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        return "EssenceNotice{" +
                "subType='" + subType + '\'' +
                ", senderId=" + senderId +
                ", operatorId=" + operatorId +
                ", messageId=" + messageId +
                "} " + super.toString();
    }
}
