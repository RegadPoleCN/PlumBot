package sdk.event.notice;

import sdk.event.global.Notice;

/**
 * 文件上传
 */
public class GroupFileUploadNotice extends Notice {
    private Long groupId;//群号
    private Long userId;//发送者 QQ 号
    private File file;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
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
        return "GroupFileUploadNotice{" +
                "groupId=" + groupId +
                ", userId=" + userId +
                ", file=" + file +
                "} " + super.toString();
    }

    public class File {
        private String id;//文件 ID
        private String name;//文件名
        private Long size;//文件大小
        private Long busid;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

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

        public Long getBusid() {
            return busid;
        }

        public void setBusid(Long busid) {
            this.busid = busid;
        }

        @Override
        public String toString() {
            return "File{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", size=" + size +
                    ", busid=" + busid +
                    '}';
        }
    }
}


