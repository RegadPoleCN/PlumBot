package sdk.client.response;

/**
 * 群用户信息
 */
public class GroupMemberInfo {
    private Long groupId;//	群号
    private Long userId;//	QQ 号
    private String nickname;//	昵称
    private String card;//	群名片／备注
    private String sex;//	性别, male 或 female 或 unknown
    private Integer age;//	年龄
    private String area;//	地区
    private Integer joinTime;//加群时间戳
    private Integer lastSentTime;//最后发言时间戳
    private String level;//成员等级
    private String role;//owner 或 admin 或 member
    private Boolean unfriendly;//是否不良记录成员
    private String title;//专属头衔
    private Long titleExpireTime;//专属头衔过期时间戳
    private Boolean cardChangeable;//是否允许修改群名

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

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

    public Integer getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(Integer joinTime) {
        this.joinTime = joinTime;
    }

    public Integer getLastSentTime() {
        return lastSentTime;
    }

    public void setLastSentTime(Integer lastSentTime) {
        this.lastSentTime = lastSentTime;
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

    public Boolean getUnfriendly() {
        return unfriendly;
    }

    public void setUnfriendly(Boolean unfriendly) {
        this.unfriendly = unfriendly;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getTitleExpireTime() {
        return titleExpireTime;
    }

    public void setTitleExpireTime(Long titleExpireTime) {
        this.titleExpireTime = titleExpireTime;
    }

    public Boolean getCardChangeable() {
        return cardChangeable;
    }

    public void setCardChangeable(Boolean cardChangeable) {
        this.cardChangeable = cardChangeable;
    }

    @Override
    public String toString() {
        return "GroupMemberInfo{" +
                "groupId=" + groupId +
                ", userId=" + userId +
                ", nickname='" + nickname + '\'' +
                ", card='" + card + '\'' +
                ", sex='" + sex + '\'' +
                ", age=" + age +
                ", area='" + area + '\'' +
                ", joinTime=" + joinTime +
                ", lastSentTime=" + lastSentTime +
                ", level='" + level + '\'' +
                ", role='" + role + '\'' +
                ", unfriendly=" + unfriendly +
                ", title='" + title + '\'' +
                ", titleExpireTime=" + titleExpireTime +
                ", cardChangeable=" + cardChangeable +
                '}';
    }
}
