package sdk.listener.impl;

import sdk.event.message.PrivateMessage;
import sdk.listener.DefaultHandlerListener;
import sdk.listener.handler.Handler;

public class PrivateMessageListener extends DefaultHandlerListener<PrivateMessage> {

    @Override
    public void onMessage(PrivateMessage privateMessage) {
        //处理逻辑
        String message = privateMessage.getMessage();
        String[] split = message.split(" ");
        String key = split[0];
        Handler<PrivateMessage> handler = getHandler(key);
        if (handler != null) {
            handler.handle(privateMessage);
        }
    }
}
