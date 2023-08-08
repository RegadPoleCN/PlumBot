package sdk.client.response;

/**
 * go-cqhttp版本
 */
public class CQVersionInfo {
    private String appName;//应用标识, 如 go-cqhttp 固定值
    private String app_Version;//应用版本, 如 v0.9.40-fix4
    private String appFullName;//应用完整名称
    private String protocolVersion;//v11	OneBot 标准版本 固定值
    private String coolqEdition;//pro	原Coolq版本 固定值
    private String coolDirectory;
    private Boolean goCqhttp;//是否为go-cqhttp 固定值
    private String pluginVersion;//	4.15.0	固定值
    private Integer pluginBuildNumber;//固定值
    private String pluginBuildConfiguration;//release	固定值
    private String runtimeVersion;//
    private String runtimeOs;//
    private String version;//应用版本, 如 v0.9.40-fix4
    private Integer protocol;//	0/1/2/3/-1	当前登陆使用协议类型

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getApp_Version() {
        return app_Version;
    }

    public void setApp_Version(String app_Version) {
        this.app_Version = app_Version;
    }

    public String getAppFullName() {
        return appFullName;
    }

    public void setAppFullName(String appFullName) {
        this.appFullName = appFullName;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getCoolqEdition() {
        return coolqEdition;
    }

    public void setCoolqEdition(String coolqEdition) {
        this.coolqEdition = coolqEdition;
    }

    public String getCoolDirectory() {
        return coolDirectory;
    }

    public void setCoolDirectory(String coolDirectory) {
        this.coolDirectory = coolDirectory;
    }

    public Boolean getGoCqhttp() {
        return goCqhttp;
    }

    public void setGoCqhttp(Boolean goCqhttp) {
        this.goCqhttp = goCqhttp;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public void setPluginVersion(String pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    public Integer getPluginBuildNumber() {
        return pluginBuildNumber;
    }

    public void setPluginBuildNumber(Integer pluginBuildNumber) {
        this.pluginBuildNumber = pluginBuildNumber;
    }

    public String getPluginBuildConfiguration() {
        return pluginBuildConfiguration;
    }

    public void setPluginBuildConfiguration(String pluginBuildConfiguration) {
        this.pluginBuildConfiguration = pluginBuildConfiguration;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getRuntimeOs() {
        return runtimeOs;
    }

    public void setRuntimeOs(String runtimeOs) {
        this.runtimeOs = runtimeOs;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getProtocol() {
        return protocol;
    }

    public void setProtocol(Integer protocol) {
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return "CQVersionInfo{" +
                "appName='" + appName + '\'' +
                ", app_Version='" + app_Version + '\'' +
                ", appFullName='" + appFullName + '\'' +
                ", protocolVersion='" + protocolVersion + '\'' +
                ", coolqEdition='" + coolqEdition + '\'' +
                ", coolDirectory='" + coolDirectory + '\'' +
                ", goCqhttp=" + goCqhttp +
                ", pluginVersion='" + pluginVersion + '\'' +
                ", pluginBuildNumber=" + pluginBuildNumber +
                ", pluginBuildConfiguration='" + pluginBuildConfiguration + '\'' +
                ", runtimeVersion='" + runtimeVersion + '\'' +
                ", runtimeOs='" + runtimeOs + '\'' +
                ", version='" + version + '\'' +
                ", protocol=" + protocol +
                '}';
    }
}
