package sdk.client.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import sdk.anno.Param;
import sdk.client.Client;
import sdk.client.response.GroupFileSystemInfo;
import sdk.client.response.GroupFiles;
import sdk.config.CQConfig;

/**
 * 文件相关接口
 */
public final class FileClient extends Client {
    public FileClient(CQConfig config) {
        super(config);
    }

    /**
     * 下载文件到缓存目录
     *
     * @param url         资源地址
     * @return 绝对路径
     */
    public String downloadFile(@Param("url") String url) {
        return this.downloadFile(url, 0, new String[]{});
    }

    /**
     * 下载文件到缓存目录
     *
     * @param url         资源地址
     * @param threadCount 下载线程数
     * @param headers     自定义请求头
     * @return 绝对路径
     */
    public String downloadFile(@Param("url") String url,
                               @Param("thread_count") Integer threadCount,
                               @Param("headers") String[] headers) {
        HttpRequest request = this.createPostRequest(this.getUrl(), url, threadCount, headers);
        String body = request.execute().body();
        JSONObject object = JSONUtil.parseObj(body);
        if (object.getInt("retcode") == 0) {
            return object.getJSONObject("data").getStr("file");
        }
        return null;
    }

    /**
     * 上传图片
     *
     * @param groupId 群号
     * @param file    文件路径 全路径
     * @param name    上传后的名称
     */
    public void uploadGroupFile(@Param("group_id") Long groupId,
                                @Param("file") String file,
                                @Param("name") String name) {
        this.uploadGroupFile(groupId, file, name, "");
    }

    /**
     * 上传图片
     *
     * @param groupId 群号
     * @param file    文件路径 全路径
     * @param name    上传后的名称
     * @param folder  父目录id 不传上传到根目录
     */
    public void uploadGroupFile(@Param("group_id") Long groupId,
                                @Param("file") String file,
                                @Param("name") String name,
                                @Param("folder") String folder) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, file, name, folder);
        request.execute();
    }

    /**
     * 获取群文件系统信息
     *
     * @param groupId
     * @return
     */
    public GroupFileSystemInfo getGroupFileSystemInfo(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupFileSystemInfo.class);
    }

    /**
     * 获取群根目录文件列表
     *
     * @param groupId 群号
     * @return
     */
    public GroupFiles getGroupRootFiles(@Param("group_id") Long groupId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupFiles.class);
    }

    /**
     * 获取群目录文件列表
     *
     * @param groupId  群号
     * @param folderId 文件夹id
     * @return
     */
    public GroupFiles getGroupFilesByFolder(@Param("group_id") Long groupId, @Param("folder_id") String folderId) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, folderId);
        HttpResponse response = request.execute();
        return this.toBeanByData(response.body(), GroupFiles.class);
    }

    /**
     * 获取群文件资源链接
     *
     * @param groupId 群号
     * @param fileId  文件id
     * @param busid   文件类型
     */
    public String getGroupFileUrl(@Param("group_id") Long groupId,
                                  @Param("file_id") String fileId,
                                  @Param("busid") Integer busid) {
        HttpRequest request = this.createPostRequest(this.getUrl(), groupId, fileId, busid);
        HttpResponse response = request.execute();
        return this.getDataValue("url",response.body(),String.class);
    }
}
