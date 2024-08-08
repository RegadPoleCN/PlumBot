package sdk.event.message;

import sdk.event.global.RecvMessage;

/**
 * 群聊消息
 */
public class GroupMessage extends RecvMessage {
    private Long groupId;//群号
    private Anonymous anonymous;//是否匿名 null为匿名
    private Sender sender;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Anonymous getAnonymous() {
        return anonymous;
    }

    public void setAnonymous(Anonymous anonymous) {
        this.anonymous = anonymous;
    }

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    @Override
    public String toString() {
        return "GroupMessage{" +
                "groupId=" + groupId +
                ", anonymous=" + anonymous +
                ", sender=" + sender +
                "} " + super.toString();
    }

    public class Anonymous{
        private String flag;
        private String name;
        private Long id;

        public String getFlag() {
            return flag;
        }

        public void setFlag(String flag) {
            this.flag = flag;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Anonymous{" +
                    "flag='" + flag + '\'' +
                    ", name='" + name + '\'' +
                    ", id=" + id +
                    '}';
        }
    }

    public class Sender{
        private Long userId;//QQ号
        private String nickname;//昵称
        private String card;//群名/备注
        private String sex;//性别
        private Integer age;//年龄
        private String area;//地区
        private String level;//登记
        private String role;//角色
        private String title;//专属头衔

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

        public String getCard() {
            return card;
        }

        public void setCard(String card) {
            this.card = card;
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

        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return "Sender{" +
                    "userId=" + userId +
                    ", nickname='" + nickname + '\'' +
                    ", card='" + card + '\'' +
                    ", sex='" + sex + '\'' +
                    ", age=" + age +
                    ", area='" + area + '\'' +
                    ", level='" + level + '\'' +
                    ", role='" + role + '\'' +
                    ", title='" + title + '\'' +
                    '}';
        }
    }
}
