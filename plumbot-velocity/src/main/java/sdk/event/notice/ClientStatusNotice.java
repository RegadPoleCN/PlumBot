package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 其他客户端在线状态变更
 */
public class ClientStatusNotice extends Notice {
    private String client;//Device类型 客户端信息
    private Boolean online;//当前是否在线

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    @Override
    public String toString() {
        return "ClientStatusNotice{" +
                "client='" + client + '\'' +
                ", online=" + online +
                "} " + super.toString();
    }
}
