package sdk.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import sdk.anno.Param;
import sdk.client.Client;
import sdk.config.CQConfig;
import sdk.client.response.*;

import java.util.List;

/**
 * CQHTTP管理相关的封装
 */
public final class AdminClient extends Client {
    public AdminClient(CQConfig config) {
        super(config);
    }

    /**
     * 获取登录号信息
     *
     * @return
     */
    public LoginInfo getLoginInfo() {
        HttpRequest request = this.createGetRequest(this.getUrl());
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), LoginInfo.class);
    }

    /**
     * 检查是否可以发送图片
     *
     * @return
     */
    public Boolean canSendImage() {
        HttpRequest request = this.createGetRequest(this.getUrl());
        String body = request.execute().body();
        Boolean bool = JSONUtil.parseObj(body).getJSONObject("data").getBool("yes");
        return bool;
    }

    /**
     * 检查是否可以发送语音
     *
     * @return
     */
    public Boolean canSendRecord() {
        HttpRequest request = this.createGetRequest(this.getUrl());
        String body = request.execute().body();
        Boolean bool = JSONUtil.parseObj(body).getJSONObject("data").getBool("yes");
        return bool;
    }


    /**
     * 获取版本信息
     *
     * @return
     */
    public CQVersionInfo getVersionInfo() {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url);
        String body = request.execute().body();
        return this.toBeanByData(body, CQVersionInfo.class);
    }

    /**
     * 重启cqhttp
     *
     * @param delay 延迟毫秒数
     */
    public void setRestart(@Param("delay") Long delay) {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url, delay);
        request.execute();
    }

    /**
     * 中文分词
     *
     * @param content 内容
     * @return
     */
    public List<String> getWordSlices(@Param("content") String content) {
        HttpRequest request = this.createPostRequest(this.getUrl(".get_word_slices"), content);
        String body = request.execute().body();
        return this.getListValue("slices", body, String.class);
    }

    /**
     * 图片转文字
     *
     * @param image
     * @return
     */
    public OcrImage ocrImage(@Param("image") String image) {
        HttpRequest request = this.createPostRequest(this.getUrl(), image);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), OcrImage.class);
    }

    /**
     * 获取运行状态
     *
     * @return
     */
    public CQStatus getStatus() {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), CQStatus.class);
    }

    /**
     * 重载事件过滤器
     */
    public void reloadEventFilter() {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url);
        request.execute();
    }

    /**
     * 获取在线列表
     *
     * @param noCache 是否使用缓存
     * @return
     */
    public List<OnlineClient> getOnlineClients(@Param("no_cache") Boolean noCache) {
        String url = this.getUrl();
        HttpRequest request = this.createPostRequest(url, noCache);
        String body = request.execute().body();
        JSONObject object = JSONUtil.parseObj(body);
        if (object.getInt("retcode") == 0) {
            return JSONUtil.toList(object.getJSONObject("data").getJSONArray("clients"), OnlineClient.class);
        }
        return null;
    }

    /**
     * 检查链接安全性
     *
     * @param url 需要检查的链接
     * @return 安全等级, 1: 安全 2: 未知 3: 危险
     */
    public Integer checkUrlSafely(@Param("url") String url) {
        HttpRequest request = this.createPostRequest(this.getUrl(), url);
        HttpResponse response = request.execute();
        return this.getDataValue("level", response.body(), Integer.class);
    }

}
