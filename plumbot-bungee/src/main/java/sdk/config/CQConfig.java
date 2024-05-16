package sdk.config;

/**
 * go-cqhttp基本配置
 */
public class CQConfig {
    private String url;//websocket地址
    private String token;//token鉴权
    private Boolean isAccessToken;//是否开启鉴权

    public CQConfig(String url) {
        this(url, null);
    }

    public CQConfig(String url, String token) {
        this(url, token, false);
    }


    public CQConfig(String url, String token, Boolean isAccessToken) {
        this.url = url;
        this.token = token;
        this.isAccessToken = isAccessToken;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getIsAccessToken() {
        return isAccessToken;
    }

    public void setIsAccessToken(Boolean isAccessToken) {
        this.isAccessToken = isAccessToken;
    }

    public void addTask(String message) {
    }
}
