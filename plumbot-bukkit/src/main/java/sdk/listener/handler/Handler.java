package sdk.listener.handler;

/**
 * 处理器
 *
 * @param <T>
 */
public interface Handler<T> {
    void handle(T t);
}
