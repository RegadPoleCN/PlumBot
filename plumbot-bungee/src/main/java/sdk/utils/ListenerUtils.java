package sdk.utils;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import sdk.config.MessageConfig;
import sdk.event.global.Message;
import sdk.type.SubType;

public class ListenerUtils {


    public static Class<? extends Message> getMessageType(String message) {
        return getMessageType(JSONUtil.parseObj(message));
    }

    /**
     * 获取消息对应的实体类型
     *
     * @param object
     * @return
     */
    public static Class<? extends Message> getMessageType(JSONObject object) {
        String type = null;
        String postType = object.getStr("post_type");
        if ("message".equals(postType)) {
            //消息类型
            String messageType = object.getStr("message_type");
            if ("group".equals(messageType)) {
                //群聊消息类型
                type = "groupMessage";
            } else if ("private".equals(messageType)) {
                //私聊消息类型
                type = "privateMessage";
            }
        } else if ("request".equals(postType)) {
            //请求类型
            String requestType = object.getStr("request_type");
            type = requestType;
        } else if ("notice".equals(postType)) {
            //通知类型
            String noticeType = object.getStr("notice_type");
            if ("notify".equals(noticeType) && SubType.POKE.getValue().equals(object.getStr("sub_type"))) {
                //戳一戳单独处理
                Long groupId = object.getLong("group_id");
                if (!(groupId == null) && groupId > 0) {
                    type = "group_poke";//群聊戳一戳
                } else {
                    type = "friend_poke";//私聊戳一戳
                }
            } else {
                type = noticeType;
            }
        }
        return MessageConfig.MessageMap.get(type);
    }
}
