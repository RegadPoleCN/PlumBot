package sdk.client.response;

/**
 * 群信息
 */
public class GroupInfo {
    private Long groupId;//群号
    private String groupName;//群名称
    private String groupMemo;//群备注
    private String groupCreateTime;//群创建时间
    private Long groupLevel;//群等级
    private Integer memberCount;//成员数
    private Integer maxMemberCount;//最大成员数（群容量）

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupMemo() {
        return groupMemo;
    }

    public void setGroupMemo(String groupMemo) {
        this.groupMemo = groupMemo;
    }

    public String getGroupCreateTime() {
        return groupCreateTime;
    }

    public void setGroupCreateTime(String groupCreateTime) {
        this.groupCreateTime = groupCreateTime;
    }

    public Long getGroupLevel() {
        return groupLevel;
    }

    public void setGroupLevel(Long groupLevel) {
        this.groupLevel = groupLevel;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public Integer getMaxMemberCount() {
        return maxMemberCount;
    }

    public void setMaxMemberCount(Integer maxMemberCount) {
        this.maxMemberCount = maxMemberCount;
    }

    @Override
    public String toString() {
        return "GroupInfo{" +
                "groupId=" + groupId +
                ", groupName='" + groupName + '\'' +
                ", groupMemo='" + groupMemo + '\'' +
                ", groupCreateTime='" + groupCreateTime + '\'' +
                ", groupLevel=" + groupLevel +
                ", memberCount=" + memberCount +
                ", maxMemberCount=" + maxMemberCount +
                '}';
    }
}
