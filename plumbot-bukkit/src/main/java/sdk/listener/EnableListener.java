package sdk.listener;

/**
 * 提供是否开启插件
 * @param <T>
 */
public abstract class EnableListener<T> implements Listener<T> {

    private Boolean enable = true;//默认开启

    public void setEnable(Boolean enable) {
        this.enable = enable;
    }

    public Boolean getEnable() {
        return enable;
    }

    public Boolean enable() {
        return enable;
    }

}
