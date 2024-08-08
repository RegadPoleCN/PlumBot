package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 接收到离线文件
 */
public class OfflineFileNotice extends Notice {
    private String userId;//发送者id
    private File file;//文件数据

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "OfflineFileNotice{" +
                "userId='" + userId + '\'' +
                ", file=" + file +
                "} " + super.toString();
    }

    public class File {
        private String name;//文件名
        private Long size;//文件大小
        private String url;//下载链接

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @Override
        public String toString() {
            return "File{" +
                    "name='" + name + '\'' +
                    ", size=" + size +
                    ", url='" + url + '\'' +
                    '}';
        }
    }
}
