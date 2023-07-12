package sdk.event.notice;

import sdk.event.global.GroupNotice;

/**
 * 群消息撤回
 */
public class GroupRecallNotice extends GroupNotice {
    private Long operatorId;//操作者 QQ 号
    private Long messageId;//被撤回的消息 ID

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
        return "GroupRecallNotice{" +
                "operatorId=" + operatorId +
                ", messageId=" + messageId +
                "} " + super.toString();
    }
}
