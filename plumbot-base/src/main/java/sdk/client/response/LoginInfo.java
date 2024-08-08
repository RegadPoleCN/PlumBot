package sdk.client.response;


/**
 * 机器人信息
 */
public class LoginInfo {
    private Integer userId;//QQ号
    private String nickname;//QQ昵称

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    @Override
    public String toString() {
        return "LoginInfo{" +
                "userId=" + userId +
                ", nickname='" + nickname + '\'' +
                '}';
    }
}
