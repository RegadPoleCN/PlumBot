package sdk.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import sdk.anno.Param;
import sdk.client.Client;
import sdk.client.response.GroupHonorInfo;
import sdk.client.response.GroupInfo;
import sdk.client.response.GroupMemberInfo;
import sdk.client.response.GroupSystemMsg;
import sdk.config.CQConfig;
import sdk.event.message.GroupMessage;
import sdk.type.HonorType;

import java.util.List;

/**
 * 群聊相关接口
 */
public final class GroupClient extends Client {
    public GroupClient(CQConfig config) {
        super(config);
    }

    /**
     * 退出群
     *
     * @param groupId 群号
     */
    public void setGroupLeave(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl());
        request.form("group_id", groupId);
        request.execute();
    }

    /**
     * 获取群信息
     *
     * @param groupId
     * @return
     */
    public GroupInfo getGroupInfo(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId);
        String body = request.execute().body();
        return this.toBeanByData(body, GroupInfo.class);
    }

    /**
     * 获取群列表
     *
     * @return
     */
    public List<GroupInfo> getGroupList() {
        HttpRequest request = this.createPostRequest(this.getUrl());
        HttpResponse response = request.execute();
        return this.toListByData(response.body(), GroupInfo.class);
    }


    /**
     * 获取群成员信息
     *
     * @param groupId 群号
     * @param userId  qq号
     * @param noCache 是否缓存
     * @return
     */
    public GroupMemberInfo getGroupMemberInfo(@Param("group_id") Long groupId,
                                              @Param("user_id") Long userId,
                                              @Param("no_cache") Boolean noCache) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, userId, noCache);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupMemberInfo.class);
    }

    /**
     * 获取群成员列表
     *
     * @param groupId 群号
     * @return
     */
    public List<GroupMemberInfo> getGroupMemberList(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId);
        HttpResponse response = request.execute();
        return this.toListByData(response.body(), GroupMemberInfo.class);
    }


    /**
     * 获取群荣誉
     *
     * @param groupId   群号
     * @param honorType 获取类型
     * @return
     */
    public GroupHonorInfo getGroupHonorInfo(@Param("group_id") Long groupId, @Param("honor_type") HonorType honorType) {
        return getGroupHonorInfo(groupId, honorType.getValue());
    }

    /**
     * 获取群荣誉
     *
     * @param groupId 群号
     * @param type    获取类型
     * @return
     */
    public GroupHonorInfo getGroupHonorInfo(@Param("group_id") Long groupId, @Param("type") String type) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, type);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupHonorInfo.class);
    }

    /**
     * 获取群系统消息
     *
     * @return
     */
    public GroupSystemMsg getGroupSystemMsg() {
        HttpRequest request = this.createPostRequest(this.getUrl());
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupSystemMsg.class);
    }

    /**
     * 获取群消息历史记录
     *
     * @param groupId    群号
     * @return 最近19条消息
     */
    public List<GroupMessage> getGroupMsgHistory(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, 0L);
        HttpResponse response = request.execute();
        return this.getListValue("messages", response.body(), GroupMessage.class);
    }


    /**
     * 获取群消息历史记录
     *
     * @param groupId    群号
     * @param messageSeq 消息序号
     * @return 最近19条消息
     */
    public List<GroupMessage> getGroupMsgHistory(@Param("group_id") Long groupId, @Param("message_seq") Long messageSeq) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, messageSeq);
        HttpResponse response = request.execute();
        return this.getListValue("messages", response.body(), GroupMessage.class);
    }

}
