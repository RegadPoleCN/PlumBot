package sdk.client.response;

/**
 * 精华消息
 */
public class EssenceMsg {
    private Long senderId;//	发送者QQ 号
    private String senderNick;//	发送者昵称
    private Long senderTime;//	消息发送时间
    private Long operatorId;//	操作者QQ 号
    private String operatorNick;//	操作者昵称
    private Long operatorTime;//	精华设置时间
    private Integer messageId;//	消息ID

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderNick() {
        return senderNick;
    }

    public void setSenderNick(String senderNick) {
        this.senderNick = senderNick;
    }

    public Long getSenderTime() {
        return senderTime;
    }

    public void setSenderTime(Long senderTime) {
        this.senderTime = senderTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorNick() {
        return operatorNick;
    }

    public void setOperatorNick(String operatorNick) {
        this.operatorNick = operatorNick;
    }

    public Long getOperatorTime() {
        return operatorTime;
    }

    public void setOperatorTime(Long operatorTime) {
        this.operatorTime = operatorTime;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        return "EssenceMsg{" +
                "senderId=" + senderId +
                ", senderNick='" + senderNick + '\'' +
                ", senderTime=" + senderTime +
                ", operatorId=" + operatorId +
                ", operatorNick='" + operatorNick + '\'' +
                ", operatorTime=" + operatorTime +
                ", messageId=" + messageId +
                '}';
    }
}
