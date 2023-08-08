package sdk.config;

import sdk.event.global.Message;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.event.notice.*;
import sdk.event.request.FriendRequest;
import sdk.event.request.GroupRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 上报消息与其对应的实体类
 */
public class MessageConfig {
    public static Map<String, Class<? extends Message>> MessageMap = new HashMap<>();

    static {
        MessageMap.put("groupMessage", GroupMessage.class);
        MessageMap.put("privateMessage", PrivateMessage.class);

        MessageMap.put("friend", FriendRequest.class);
        MessageMap.put("group", GroupRequest.class);

        MessageMap.put("group_upload", GroupFileUploadNotice.class);
        MessageMap.put("group_admin", GroupAdminNotice.class);
        MessageMap.put("group_decrease", GroupDecreaseNotice.class);
        MessageMap.put("group_increase", GroupIncreaseNotice.class);
        MessageMap.put("group_ban", GroupBanNotice.class);
        MessageMap.put("group_recall", GroupRecallNotice.class);
        MessageMap.put("group_poke", GroupPokeNotice.class);
        MessageMap.put("lucky_king", GroupLuckyKingNotice.class);
        MessageMap.put("honor", GroupHonorNotice.class);
        MessageMap.put("group_card", GroupCardNotice.class);

        MessageMap.put("friend_add", FriendAddNotice.class);
        MessageMap.put("friend_recall", FriendRecallNotice.class);
        MessageMap.put("friend_poke", FriendPokeNotice.class);
        MessageMap.put("offline_file", OfflineFileNotice.class);
        MessageMap.put("client_status", ClientStatusNotice.class);
        MessageMap.put("essence", EssenceNotice.class);
    }
}
