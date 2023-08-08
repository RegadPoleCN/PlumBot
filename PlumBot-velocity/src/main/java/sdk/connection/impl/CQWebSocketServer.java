package sdk.connection.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import sdk.connection.Connection;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

/**
 * 反向websocket
 */
public class CQWebSocketServer extends WebSocketServer implements Connection {

    private BlockingQueue<String> queue;

    private static final Log log = LogFactory.get();

    public CQWebSocketServer(Integer port, BlockingQueue<String> queue) {
        super(new InetSocketAddress(port));
        this.queue = queue;
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        log.info("接收到来自客户端的连接");
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        log.error("客户端下线");
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        queue.add(s);
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        log.warn(e);
    }

    @Override
    public void onStart() {
        log.info("WebSocket服务器启动，正在监听端口：{}", getPort());
    }

    @Override
    public void create() {
        super.start();
    }
}
