package sdk.event.message;

import sdk.event.global.RecvMessage;

/**
 * 私聊消息
 */
public class PrivateMessage extends RecvMessage {
    private Integer tempSource;//临时会话来源
    private Sender sender;

    public Integer getTempSource() {
        return tempSource;
    }

    public void setTempSource(Integer tempSource) {
        this.tempSource = tempSource;
    }

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    @Override
    public String toString() {
        return "PrivateMessage{" +
                "tempSource=" + tempSource +
                ", sender=" + sender +
                "} " + super.toString();
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
