package sdk.client.response;


/**
 * 陌生人信息
 */
public class StrangerInfo {
    private Long userId;//QQ 号
    private String nickname;//昵称
    private String sex;//性别, male 或 female 或 unknown
    private Integer age;//年龄
    private String qid;//qid ID身份卡

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

    public String getQid() {
        return qid;
    }

    public void setQid(String qid) {
        this.qid = qid;
    }

    @Override
    public String toString() {
        return "StrangerInfo{" +
                "userId=" + userId +
                ", nickname='" + nickname + '\'' +
                ", sex='" + sex + '\'' +
                ", age=" + age +
                ", qid='" + qid + '\'' +
                '}';
    }
}
