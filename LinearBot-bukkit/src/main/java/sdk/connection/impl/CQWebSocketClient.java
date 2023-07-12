package sdk.connection.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import sdk.connection.Connection;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;

/**
 * 正向websocket
 */
public class CQWebSocketClient extends WebSocketClient implements Connection {

    private BlockingQueue<String> queue;

    private static final Log log = LogFactory.get();

    public CQWebSocketClient(URI serverUri, BlockingQueue<String> queue) {
        super(serverUri);
        this.queue = queue;
    }

    /**
     * 建立连接
     *
     * @param serverHandshake
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("已连接到服务器：{}，开始监听事件", this.uri);
    }

    /**
     * 收到消息
     *
     * @param message
     */
    @Override
    public void onMessage(String message) {
        if (message != null && !message.contains("heartbeat") && !message.contains("lifecycle")) {//过滤心跳
            queue.add(message);
        }
    }

    @Override
    public void onClose(int i, String s, boolean b) {
    }

    /**
     * 出现异常
     *
     * @param e
     */
    @Override
    public void onError(Exception e) {
        log.warn(e);
    }

    @Override
    public void create() {
        super.connect();
    }
}
