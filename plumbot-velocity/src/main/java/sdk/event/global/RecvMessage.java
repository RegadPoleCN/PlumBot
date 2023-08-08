package sdk.event.global;


/**
 * 消息类型
 */
public class RecvMessage extends Message {
    private String messageType;//消息类型
    private String subType;//消息子类型
    private Integer messageId;//消息ID
    private Long userId;//发送者QQ号
    private String message;//消息内容
    private String rawMessage;//原始消息内容
    private Integer font;//字体

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public Integer getFont() {
        return font;
    }

    public void setFont(Integer font) {
        this.font = font;
    }

    @Override
    public String toString() {
        return "RecvMessage{" +
                "messageType='" + messageType + '\'' +
                ", subType='" + subType + '\'' +
                ", messageId=" + messageId +
                ", userId=" + userId +
                ", message='" + message + '\'' +
                ", rawMessage='" + rawMessage + '\'' +
                ", font=" + font +
                "} " + super.toString();
    }
}
