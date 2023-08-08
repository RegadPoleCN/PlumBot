package sdk.client.response;

/**
 * @全体 信息
 */
public class GroupAtAllRemain {
    private Boolean canAtAll;//	是否可以 @全体成员
    private Integer remainAtAllCountForGroup;//	群内所有管理当天剩余 @全体成员 次数
    private Integer remainAtAllCountForUin;//	Bot 当天剩余 @全体成员 次数

    public Boolean getCanAtAll() {
        return canAtAll;
    }

    public void setCanAtAll(Boolean canAtAll) {
        this.canAtAll = canAtAll;
    }

    public Integer getRemainAtAllCountForGroup() {
        return remainAtAllCountForGroup;
    }

    public void setRemainAtAllCountForGroup(Integer remainAtAllCountForGroup) {
        this.remainAtAllCountForGroup = remainAtAllCountForGroup;
    }

    public Integer getRemainAtAllCountForUin() {
        return remainAtAllCountForUin;
    }

    public void setRemainAtAllCountForUin(Integer remainAtAllCountForUin) {
        this.remainAtAllCountForUin = remainAtAllCountForUin;
    }

    @Override
    public String toString() {
        return "GroupAtAllRemain{" +
                "canAtAll=" + canAtAll +
                ", remainAtAllCountForGroup=" + remainAtAllCountForGroup +
                ", remainAtAllCountForUin=" + remainAtAllCountForUin +
                '}';
    }
}
