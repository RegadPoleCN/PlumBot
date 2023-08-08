package sdk.client.response;

/**
 * 客户端信息
 */
public class OnlineClient {
    private Long appId;//	客户端ID
    private String deviceName;//	设备名称
    private String deviceKind;//	设备类型

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceKind() {
        return deviceKind;
    }

    public void setDeviceKind(String deviceKind) {
        this.deviceKind = deviceKind;
    }

    @Override
    public String toString() {
        return "OnlineClient{" +
                "appId=" + appId +
                ", deviceName='" + deviceName + '\'' +
                ", deviceKind='" + deviceKind + '\'' +
                '}';
    }
}
