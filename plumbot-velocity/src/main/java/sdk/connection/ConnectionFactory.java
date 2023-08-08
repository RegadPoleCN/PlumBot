package sdk.connection;

import sdk.config.CQConfig;
import sdk.connection.impl.CQWebSocketClient;
import sdk.connection.impl.CQWebSocketServer;
import sdk.connection.impl.CustomHttpServer;

import java.net.URI;
import java.util.concurrent.BlockingQueue;

/**
 * 封装创建连接方法
 */
public class ConnectionFactory {
    /**
     * 构建正向websocket服务
     *
     * @return
     */
    public static CQWebSocketClient createWebsocketClient(CQConfig config, BlockingQueue<String> queue) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getUrl());
        if (config.getIsAccessToken()) {
            builder.append("?access_token=");
            builder.append(config.getToken());
        }
        String url = builder.toString();
        URI uri = new URI(url);
        return new CQWebSocketClient(uri, queue);
    }

    /**
     * 构建反向websocket服务
     *
     * @return
     */
    public static Connection createWebsocketServer(Integer port, BlockingQueue<String> queue) throws Exception {
        return new CQWebSocketServer(port, queue);
    }

    /**
     * 构建反向http服务
     *
     * @return
     */
    public static Connection createHttpServer(Integer port, String path, BlockingQueue<String> queue) throws Exception {
        return new CustomHttpServer(port, path, queue);
    }
}
