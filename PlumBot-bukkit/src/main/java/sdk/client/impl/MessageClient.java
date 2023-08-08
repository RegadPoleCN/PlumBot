package sdk.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import sdk.anno.Param;
import sdk.client.Client;
import sdk.client.response.CQFile;
import sdk.client.response.ForwardMessage;
import sdk.client.response.ForwardNode;
import sdk.client.response.Message;
import sdk.config.CQConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息相关接口
 */
public final class MessageClient extends Client {

    public MessageClient(CQConfig config) {
        super(config);
    }

    /**
     * 获取消息
     *
     * @param messageId
     * @return
     */
    public Message getMsg(@Param("message_id") Integer messageId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), messageId);
        HttpResponse response = request.execute();
        String body = response.body();
        return this.toBeanByData(body, Message.class);
    }

    /**
     * 获取合并转发内容
     *
     * @param messageId
     */
    public List<ForwardMessage> getForwardMsg(@Param("message_id") String messageId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), messageId);
        String body = request.execute().body();
        JSONObject parseObj = JSONUtil.parseObj(body);
        return this.toList(parseObj.getJSONObject("data").getJSONArray("messages"), ForwardMessage.class);
    }

    /**
     * 发送合并消息 群
     *
     * @param groupId
     * @param messages
     * @return
     */
    public void sendGroupForwardMsg(@Param("group_id") Long groupId, @Param("messages") ArrayList<ForwardNode> messages) {
        HttpRequest request = this.createPostRequest(this.getUrl());
        Map<String,Object> map = new HashMap<>();
        map.put("group_id",groupId);
        map.put("messages",messages);
        request.body(JSONUtil.toJsonStr(map));
        request.execute();
    }

    /**
     * 撤回消息
     *
     * @param messageId
     */
    public void deleteMsg(@Param("message_id") Integer messageId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), messageId);
        request.execute();
    }

    /**
     * 发送私聊消息
     *
     * @param groupId 群号
     * @param message 要发送的内容
     * @return 消息id
     */
    public Integer sendGroupMsg(Long groupId, String message) {
        return sendGroupMsg(groupId, message, true);
    }

    /**
     * 发送私聊消息
     *
     * @param groupId    群号
     * @param message    要发送的内容
     * @param autoEscape 消息内容是否作为纯文本发送 ( 即不解析 CQ 码 ) , 只在 message 字段是字符串时有效
     * @return 消息id
     */
    public Integer sendGroupMsg(@Param("group_id") Long groupId, @Param("message") String message, @Param("auto_escape") Boolean autoEscape) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, message, autoEscape);
        HttpResponse response = request.execute();

        return this.getDataValue("message_id", response.body(), Integer.class);
    }

    /**
     * 发送私聊消息
     *
     * @param userId  对方 QQ 号
     * @param message 要发送的内容
     * @return 消息id
     */
    public Integer sendPrivateMsg(Long userId, String message) {
        return sendPrivateMsg(userId, message, true);
    }

    /**
     * 发送私聊消息
     *
     * @param userId     对方 QQ 号
     * @param message    要发送的内容
     * @param autoEscape 消息内容是否作为纯文本发送 ( 即不解析 CQ 码 ) , 只在 message 字段是字符串时有效
     * @return 消息id
     */
    public Integer sendPrivateMsg(Long userId, String message, Boolean autoEscape) {
        return sendPrivateMsg(userId, 0L, message, autoEscape);
    }

    /**
     * 发送私聊消息
     *
     * @param userId     对方 QQ 号
     * @param groupId    主动发起临时会话群号(机器人本身必须是管理员/群主)
     * @param message    要发送的内容
     * @param autoEscape 消息内容是否作为纯文本发送 ( 即不解析 CQ 码 ) , 只在 message 字段是字符串时有效
     * @return 消息id
     */
    public Integer sendPrivateMsg(@Param("user_id") Long userId,
                                  @Param("group_id") Long groupId,
                                  @Param("message") String message,
                                  @Param("auto_escape") Boolean autoEscape) {
        HttpRequest request = this.createPostRequest(this.getUrl(), userId, groupId, message, autoEscape);
        HttpResponse response = request.execute();
        return this.getDataValue("message_id", response.body(), Integer.class);
    }

    /**
     * 获取图片
     *
     * @param file
     * @return
     */
    public CQFile getImage(@Param("file") String file) {
        HttpRequest request = this.createPostRequest(this.getUrl(), file);
        HttpResponse response = request.execute();
        String body = response.body();
        return this.toBeanByData(body, CQFile.class);
    }
}
