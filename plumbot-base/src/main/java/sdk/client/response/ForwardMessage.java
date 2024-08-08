package sdk.client.response;

/**
 * 获取合并转发信息
 */
public class ForwardMessage {
    private String content;
    private Long time;
    private Sender sender;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    @Override
    public String toString() {
        return "ForwardMessage{" +
                "content='" + content + '\'' +
                ", time=" + time +
                ", sender=" + sender +
                '}';
    }

    public class Sender {
        private String nickname;
        private Long userId;

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        @Override
        public String toString() {
            return "Sender{" +
                    "nickname='" + nickname + '\'' +
                    ", userId=" + userId +
                    '}';
        }
    }
}
