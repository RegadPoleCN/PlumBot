package sdk.client.response;

/**
 * 获取消息返回的信息
 */
public class Message {

    private Integer messageId;
    private Integer realId;
    private Integer time;
    private String message;
    private String rawMessage;

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public Integer getRealId() {
        return realId;
    }

    public void setRealId(Integer realId) {
        this.realId = realId;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
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

    @Override
    public String toString() {
        return "Message{" +
                "messageId=" + messageId +
                ", realId=" + realId +
                ", time=" + time +
                ", message='" + message + '\'' +
                ", rawMessage='" + rawMessage + '\'' +
                '}';
    }

    public class Sender{
        private Long userId;//QQ号
        private String nickname;//昵称/备注
        private String sex;//性别
        private Integer age;//年龄

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getSex() {
            return sex;
        }

        public void setSex(String sex) {
            this.sex = sex;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        @Override
        public String toString() {
            return "Sender{" +
                    "userId=" + userId +
                    ", nickname='" + nickname + '\'' +
                    ", sex='" + sex + '\'' +
                    ", age=" + age +
                    '}';
        }
    }
}
