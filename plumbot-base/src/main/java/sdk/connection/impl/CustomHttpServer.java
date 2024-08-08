package sdk.connection.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.regadpole.plumbot.PlumBot;
import sdk.connection.Connection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

/**
 * 基于内置http服务器实现自定义服务
 */
public class CustomHttpServer implements Connection {

    private Integer port;
    private String path;
    private BlockingQueue<String> queue;

//    private static Log log = LogFactory.get();

    private HttpServer server;

    public CustomHttpServer(Integer port, String path, BlockingQueue<String> queue) {
        try {
            this.port = port;
            this.path = path;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, new CustomHttpHandler());
        } catch (IOException e) {
//            log.error(e);
            PlumBot.INSTANCE.getSLF4JLogger().error(Arrays.toString(e.getStackTrace()));
        }
        this.queue = queue;
    }

    @Override
    public void create() {
        server.start();
//        log.info("HTTP服务器启动，正在监听端口：{}", port);
        PlumBot.INSTANCE.getLogger().info("HTTP服务器启动，正在监听端口："+port);
    }

    class CustomHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuffer stringBuffer = new StringBuffer();
            String oneLine = "";
            while ((oneLine = reader.readLine()) != null) {
                stringBuffer.append(oneLine);
            }
            queue.add(stringBuffer.toString());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }
    }

    @Override
    public void stop() {
        server.stop(0);
        PlumBot.INSTANCE.getLogger().info("HTTP服务器已关闭");
    }
}
