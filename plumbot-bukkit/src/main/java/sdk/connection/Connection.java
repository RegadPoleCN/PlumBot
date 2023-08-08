package sdk.connection;

/**
 * 封装启动方法
 * 实现类对各自不同的实现调用启动
 */
public interface Connection {
    /**
     * 启动方法
     */
    void create();
}
