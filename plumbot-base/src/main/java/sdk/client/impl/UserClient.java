package sdk.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import sdk.anno.Param;
import sdk.client.Client;
import sdk.client.response.Friend;
import sdk.client.response.StrangerInfo;
import sdk.client.response.VipInfo;
import sdk.config.CQConfig;

import java.util.List;

/**
 * 用户相关接口
 */
public final class UserClient extends Client {
    public UserClient(CQConfig config) {
        super(config);
    }

    /**
     * 处理好友申请
     *
     * @param flag    加好友请求的 flag（需从上报的数据中获得）
     * @param approve true同意，false拒绝
     */
    public void setFriendAddRequest(String flag, Boolean approve) {
        setFriendAddRequest(flag, approve, "");
    }

    /**
     * 处理好友申请
     *
     * @param flag    加好友请求的 flag（需从上报的数据中获得）
     * @param approve true同意，false拒绝
     * @param reason  添加后的好友备注
     */
    public void setFriendAddRequest(@Param("flag") String flag,
                                    @Param("approve") Boolean approve,
                                    @Param("reason") String reason) {
        HttpRequest request = this.createPostRequest(this.getUrl(), flag, approve, reason);
        request.execute();
    }

    /**
     * 处理申请/邀请群聊
     *
     * @param flag    加群请求的 flag（需从上报的数据中获得）
     * @param subType add 或 invite, 请求类型（需要和上报消息中的 sub_type 字段相符）
     * @param approve true同意，false拒绝
     */
    public void setGroupAddRequest(String flag, String subType, Boolean approve) {
        setGroupAddRequest(flag, subType, approve, "");
    }

    /**
     * 处理申请/邀请群聊
     *
     * @param flag    加群请求的 flag（需从上报的数据中获得）
     * @param subType add 或 invite, 请求类型（需要和上报消息中的 sub_type 字段相符）
     * @param approve true同意，false拒绝
     * @param reason  拒绝理由
     */
    public void setGroupAddRequest(@Param("flag") String flag,
                                   @Param("sub_type") String subType,
                                   @Param("approve") Boolean approve,
                                   @Param("reason") String reason) {
        HttpRequest request = this.createPostRequest(this.getUrl(), flag, subType, approve, reason);
        request.execute();
    }


    /**
     * 获取陌生人信息
     *
     * @param userId qq号
     * @return
     */
    public StrangerInfo getStrangerInfo(@Param("user_id") Long userId) {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url, userId);
        String body = request.execute().body();
        return this.toBeanByData(body, StrangerInfo.class);
    }

    /**
     * 获取好友列表
     *
     * @return
     */
    public List<Friend> getFriendList() {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url);
        String body = request.execute().body();
        return this.toListByData(body, Friend.class);
    }

    /**
     * 获取vip信息
     *
     * @param userId qq号
     * @return
     */
    public VipInfo getVipInfo(@Param("user_id") Long userId) {
        HttpRequest request = this.createPostRequest(this.getUrl("_get_vip_info"),userId);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), VipInfo.class);
    }

}
