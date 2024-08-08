package sdk.client.response;

/**
 * 好友信息
 */
public class Friend {
    private Long userId;//QQ 号
    private String nickname;//昵称
    private String  remark;//备注名

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "Friend{" +
                "userId=" + userId +
                ", nickname='" + nickname + '\'' +
                ", remark='" + remark + '\'' +
                '}';
    }
}
