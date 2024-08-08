package sdk.client.response;

/**
 * go-cqhttp状态
 */
public class CQStatus {
    private Boolean appInitialized;// true
    private Boolean appEnabled;//原 CQHTTP 字段, 恒定为 true
    private Boolean pluginsGood;//原 CQHTTP 字段, 恒定为 true
    private Boolean appGood;//原 CQHTTP 字段, 恒定为 true
    private Boolean online;//表示BOT是否在线
    private Boolean good;//online
    private Statistics stat;//运行统计

    public Boolean getAppInitialized() {
        return appInitialized;
    }

    public void setAppInitialized(Boolean appInitialized) {
        this.appInitialized = appInitialized;
    }

    public Boolean getAppEnabled() {
        return appEnabled;
    }

    public void setAppEnabled(Boolean appEnabled) {
        this.appEnabled = appEnabled;
    }

    public Boolean getPluginsGood() {
        return pluginsGood;
    }

    public void setPluginsGood(Boolean pluginsGood) {
        this.pluginsGood = pluginsGood;
    }

    public Boolean getAppGood() {
        return appGood;
    }

    public void setAppGood(Boolean appGood) {
        this.appGood = appGood;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public Boolean getGood() {
        return good;
    }

    public void setGood(Boolean good) {
        this.good = good;
    }

    public Statistics getStat() {
        return stat;
    }

    public void setStat(Statistics stat) {
        this.stat = stat;
    }

    @Override
    public String toString() {
        return "CQStatus{" +
                "appInitialized=" + appInitialized +
                ", appEnabled=" + appEnabled +
                ", pluginsGood=" + pluginsGood +
                ", appGood=" + appGood +
                ", online=" + online +
                ", good=" + good +
                ", stat=" + stat +
                '}';
    }

    public class Statistics {
        private Long packetReceived;//	收到的数据包总数
        private Long packetSent;//	发送的数据包总数
        private Long packetLost;//	数据包丢失总数
        private Long messageReceived;//	接受信息总数
        private Long messageSent;//	发送信息总数
        private Long disconnectTimes;//	TCP 链接断开次数
        private Long lostTimes;//账号掉线次数

        public Long getPacketReceived() {
            return packetReceived;
        }

        public void setPacketReceived(Long packetReceived) {
            this.packetReceived = packetReceived;
        }

        public Long getPacketSent() {
            return packetSent;
        }

        public void setPacketSent(Long packetSent) {
            this.packetSent = packetSent;
        }

        public Long getPacketLost() {
            return packetLost;
        }

        public void setPacketLost(Long packetLost) {
            this.packetLost = packetLost;
        }

        public Long getMessageReceived() {
            return messageReceived;
        }

        public void setMessageReceived(Long messageReceived) {
            this.messageReceived = messageReceived;
        }

        public Long getMessageSent() {
            return messageSent;
        }

        public void setMessageSent(Long messageSent) {
            this.messageSent = messageSent;
        }

        public Long getDisconnectTimes() {
            return disconnectTimes;
        }

        public void setDisconnectTimes(Long disconnectTimes) {
            this.disconnectTimes = disconnectTimes;
        }

        public Long getLostTimes() {
            return lostTimes;
        }

        public void setLostTimes(Long lostTimes) {
            this.lostTimes = lostTimes;
        }

        @Override
        public String toString() {
            return "Statistics{" +
                    "packetReceived=" + packetReceived +
                    ", packetSent=" + packetSent +
                    ", packetLost=" + packetLost +
                    ", messageReceived=" + messageReceived +
                    ", messageSent=" + messageSent +
                    ", disconnectTimes=" + disconnectTimes +
                    ", lostTimes=" + lostTimes +
                    '}';
        }
    }
}
