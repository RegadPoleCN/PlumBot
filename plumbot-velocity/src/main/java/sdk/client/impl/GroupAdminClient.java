package sdk.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import sdk.anno.Param;
import sdk.client.Client;
import sdk.client.response.EssenceMsg;
import sdk.client.response.GroupAtAllRemain;
import sdk.config.CQConfig;
import sdk.event.message.GroupMessage;

import java.util.List;

/**
 * 群聊管理相关接口
 */
public final class GroupAdminClient extends Client {

    public GroupAdminClient(CQConfig config) {
        super(config);
    }

    /**
     * 将用户移出群聊
     *
     * @param groupId 群号
     * @param userId  要移出的qq号
     */
    public void setGroupKick(Long groupId, Long userId) {
        setGroupKick(groupId, userId, false);
    }

    /**
     * 将用户移出群聊
     *
     * @param groupId          群号
     * @param userId           要移出的qq号
     * @param rejectAddRequest 是否接收该用户申请
     */
    public void setGroupKick(@Param("group_id") Long groupId,
                             @Param("user_id") Long userId,
                             @Param("reject_add_request") Boolean rejectAddRequest) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, userId, rejectAddRequest);
        request.execute();
    }

    /**
     * 群组单人禁言
     *
     * @param groupId  群号
     * @param userId   要禁言的qq号
     * @param duration 禁言时长, 单位秒, 0 表示取消禁言
     */
    public HttpResponse setGroupBan(@Param("group_id") Long groupId,
                                    @Param("user_id") Long userId,
                                    @Param("duration") Long duration) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, userId, duration);
        return request.execute();
    }

    /**
     * 取消禁言
     *
     * @param groupId 群号
     * @param userId  要取消禁言的qq号
     */
    public HttpResponse removeGroupBan(Long groupId, Long userId) {
        return setGroupBan(groupId, userId, 0L);
    }

    /**
     * 群组匿名用户禁言
     *
     * @param groupId  群号
     * @param flag     消息上报的flag
     * @param duration 禁言时长, 单位秒,
     */
    public void setGroupAnonymousBan(@Param("group_id") Long groupId,
                                     @Param("anonymous_flag") String flag,
                                     @Param("duration") Long duration) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, flag, duration);
        request.execute();
    }

    /**
     * 群组匿名用户禁言
     *
     * @param groupId   群号
     * @param anonymous 消息上报的anonymous对象
     * @param duration  禁言时长, 单位秒,
     */
    public void setGroupAnonymousBan(@Param("group_id") Long groupId,
                                     @Param("anonymous") GroupMessage.Anonymous anonymous,
                                     @Param("duration") Long duration) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, anonymous, duration);
        request.execute();
    }

    /**
     * 关闭全体禁言
     *
     * @param groupId 群号
     */
    public void closeGroupWholeBan(Long groupId) {
        setGroupWholeBan(groupId, false);
    }

    /**
     * 开启全体禁言
     *
     * @param groupId 群号
     */
    public void startGroupWholeBan(Long groupId) {
        setGroupWholeBan(groupId, true);
    }

    /**
     * 群聊全体禁言
     *
     * @param groupId 群号
     * @param enable  true开启 false关闭
     */
    public void setGroupWholeBan(@Param("group_id") Long groupId, @Param("enable") Boolean enable) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, enable);
        request.execute();
    }

    /**
     * 取消群管理员
     *
     * @param groupId 群号
     * @param userId  要取消管理员的qq号
     */
    public void removeGroupAdmin(Long groupId, Long userId) {
        setGroupAdmin(groupId, userId, false);
    }

    /**
     * 添加群管理员
     *
     * @param groupId 群号
     * @param userId  要添加管理员的qq号
     */
    public void addGroupAdmin(Long groupId, Long userId) {
        setGroupAdmin(groupId, userId, true);
    }

    /**
     * 设置群管理员
     *
     * @param groupId 群号
     * @param userId  要设置管理员的qq号
     * @param enable  true设置 false取消
     */
    public void setGroupAdmin(@Param("group_id") Long groupId,
                              @Param("user_id") Long userId,
                              @Param("enable") Boolean enable) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, userId, enable);
        request.execute();
    }

    /**
     * 删除群名片 ( 群备注 )
     *
     * @param groupId 群号
     * @param userId  QQ号
     */
    public void removeGroupCard(Long groupId, Long userId) {
        setGroupCard(groupId, userId, "");
    }

    /**
     * 设置群名片 ( 群备注 )
     *
     * @param groupId 群号
     * @param userId  QQ号
     * @param card    备注 不填时删除
     */
    public void setGroupCard(@Param("group_id") Long groupId,
                             @Param("user_id") Long userId,
                             @Param("card") String card) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, userId, card);
        request.execute();
    }

    /**
     * 设置群名
     *
     * @param groupId   群号
     * @param groupName 群名
     */
    public void setGroupName(@Param("group_id") Long groupId, @Param("group_name") String groupName) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, groupName);
        request.execute();
    }

    /**
     * 解散群组 群主才能操作
     *
     * @param groupId 群号
     */
    public void setGroupLeave(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl());
        request.form("group_id", groupId);
        request.form("is_dismiss", true);
        request.execute();
    }

    /**
     * 删除专属头衔
     *
     * @param groupId 群号
     * @param userId  qq号
     */
    public void removeGroupSpecialTitle(Long groupId, Long userId) {
        setGroupSpecialTitle(groupId, userId, "");
    }

    /**
     * 设置专属头衔
     *
     * @param groupId      群号
     * @param userId       qq号
     * @param specialTitle 专属头衔, 不填或空字符串表示删除专属头衔
     */
    public void setGroupSpecialTitle(@Param("group_id") Long groupId,
                                     @Param("user_id") Long userId,
                                     @Param("special_title") String specialTitle) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, userId, specialTitle);
        request.execute();
    }

    /**
     * 设置群头像
     *
     * @param groupId 群号
     * @param file    文件 绝对路径，网络路径，base64
     * @param cache   通过网络 URL 发送时有效, 1表示使用缓存, 0关闭关闭缓存, 默认 为1
     */
    public void setGroupPortrait(@Param("group_id") Long groupId,
                                 @Param("file") String file,
                                 @Param("cache") Integer cache) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, file, cache);
        request.execute();
    }

    /**
     * 获取群 @全体成员 剩余次数
     *
     * @param groupId 群号
     * @return
     */
    public GroupAtAllRemain getGroupAtAllRemain(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupAtAllRemain.class);
    }

    /**
     * 发送群公告
     *
     * @param groupId 群号
     * @param content 公告内容
     */
    public void sendGroupNotice(@Param("group_id") Long groupId, @Param("content") String content) {
        HttpRequest request = this.createPostRequest(this.getUrl("/_send_group_notice"), groupId, content);
        request.execute();
    }

    /**
     * 设置精华消息
     * 需要管理员权限
     *
     * @param messageId 消息id
     */
    public void setEssenceMsg(@Param("message_id") Integer messageId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), messageId);
        request.execute();
    }

    /**
     * 移除精华消息
     *
     * @param messageId 消息id
     */
    public void deleteEssenceMsg(@Param("message_id") Integer messageId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), messageId);
        request.execute();
    }

    /**
     * 获取精华消息列表
     *
     * @param groupId
     * @return
     */
    public List<EssenceMsg> getEssenceMsgList(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId);
        String body = request.execute().body();
        return this.toListByData(body, EssenceMsg.class);
    }
}

