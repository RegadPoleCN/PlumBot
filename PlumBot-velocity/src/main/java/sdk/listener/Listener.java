package sdk.listener;

/**
 * 监听器
 *
 * @param <T>
 */
public interface Listener<T> extends VailderListener<T> {

    /**
     * 监听到消息
     *
     * @param t 消息实体
     */
    void onMessage(T t);

}
