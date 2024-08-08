package sdk.event.global;


/**
 * 通知类型适用
 */
public class Notice extends Message {
    private String  noticeType;//通知类型

    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
        this.noticeType = noticeType;
    }

    @Override
    public String toString() {
        return "Notice{" +
                "noticeType='" + noticeType + '\'' +
                "} " + super.toString();
    }
}
