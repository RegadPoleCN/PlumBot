package sdk.client.response;

/**
 * 会员信息
 */
public class VipInfo {
    private Long userId;//	QQ 号
    private String nickname;//	用户昵称
    private Long level;//	QQ 等级
    private Double leveSpeed;//	等级加速度
    private String vipLevel;//	会员等级
    private Long vipGrowthSpeed;//	会员成长速度
    private Long vipGrowthTotal;//	会员成长总值

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

    public Long getLevel() {
        return level;
    }

    public void setLevel(Long level) {
        this.level = level;
    }

    public Double getLeveSpeed() {
        return leveSpeed;
    }

    public void setLeveSpeed(Double leveSpeed) {
        this.leveSpeed = leveSpeed;
    }

    public String getVipLevel() {
        return vipLevel;
    }

    public void setVipLevel(String vipLevel) {
        this.vipLevel = vipLevel;
    }

    public Long getVipGrowthSpeed() {
        return vipGrowthSpeed;
    }

    public void setVipGrowthSpeed(Long vipGrowthSpeed) {
        this.vipGrowthSpeed = vipGrowthSpeed;
    }

    public Long getVipGrowthTotal() {
        return vipGrowthTotal;
    }

    public void setVipGrowthTotal(Long vipGrowthTotal) {
        this.vipGrowthTotal = vipGrowthTotal;
    }

    @Override
    public String toString() {
        return "VipInfo{" +
                "userId=" + userId +
                ", nickname='" + nickname + '\'' +
                ", level=" + level +
                ", leveSpeed=" + leveSpeed +
                ", vipLevel='" + vipLevel + '\'' +
                ", vipGrowthSpeed=" + vipGrowthSpeed +
                ", vipGrowthTotal=" + vipGrowthTotal +
                '}';
    }
}
