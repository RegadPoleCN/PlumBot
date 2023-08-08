package sdk.listener.impl;

import sdk.event.message.GroupMessage;
import sdk.listener.DefaultHandlerListener;
import sdk.listener.handler.Handler;

public class GroupMessageListener extends DefaultHandlerListener<GroupMessage> {

    @Override
    public void onMessage(GroupMessage groupMessage) {
        //处理逻辑
        String message = groupMessage.getMessage();
        long id = groupMessage.getGroupId();
        String[] split = message.split(" ");
        String key = split[0];
        Handler<GroupMessage> handler = getHandler(key);
        if (handler != null && id == 4130625468L) {
            handler.handle(groupMessage);
        }
    }

}
