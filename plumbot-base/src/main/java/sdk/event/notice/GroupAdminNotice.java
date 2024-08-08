package sdk.event.notice;

import sdk.event.global.GroupNotice;

/**
 * 群管理员变动
 */
public class GroupAdminNotice extends GroupNotice {
    private String subType;//set、unset	事件子类型, 分别表示设置和取消管理员

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    @Override
    public String toString() {
        return "GroupAdminNotice{" +
                "subType='" + subType + '\'' +
                "} " + super.toString();
    }
}


