package sdk.client.response;

import java.util.List;

/**
 * 群文件信息
 */
public class GroupFiles {

    private List<File> files;//	文件列表
    private List<Folder> folders;//文件夹列表

    public List<File> getFiles() {
        return files;
    }

    public void setFiles(List<File> files) {
        this.files = files;
    }

    public List<Folder> getFolders() {
        return folders;
    }

    public void setFolders(List<Folder> folders) {
        this.folders = folders;
    }

    @Override
    public String toString() {
        return "GroupFiles{" +
                "files=" + files +
                ", folders=" + folders +
                '}';
    }

    public class File {
        private String fileId;//文件ID
        private String fileName;//文件名
        private Integer busid;//文件类型
        private Long fileSize;//文件大小
        private Long uploadTime;//	上传时间
        private Long deadTime;//过期时间,永久文件恒为0
        private Long modifyTime;//最后修改时间
        private Integer downloadTimes;//下载次数
        private Long uploader;//上传者ID
        private String uploaderName;//上传者名字

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Integer getBusid() {
            return busid;
        }

        public void setBusid(Integer busid) {
            this.busid = busid;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        public Long getUploadTime() {
            return uploadTime;
        }

        public void setUploadTime(Long uploadTime) {
            this.uploadTime = uploadTime;
        }

        public Long getDeadTime() {
            return deadTime;
        }

        public void setDeadTime(Long deadTime) {
            this.deadTime = deadTime;
        }

        public Long getModifyTime() {
            return modifyTime;
        }

        public void setModifyTime(Long modifyTime) {
            this.modifyTime = modifyTime;
        }

        public Integer getDownloadTimes() {
            return downloadTimes;
        }

        public void setDownloadTimes(Integer downloadTimes) {
            this.downloadTimes = downloadTimes;
        }

        public Long getUploader() {
            return uploader;
        }

        public void setUploader(Long uploader) {
            this.uploader = uploader;
        }

        public String getUploaderName() {
            return uploaderName;
        }

        public void setUploaderName(String uploaderName) {
            this.uploaderName = uploaderName;
        }

        @Override
        public String toString() {
            return "File{" +
                    "fileId='" + fileId + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", busid=" + busid +
                    ", fileSize=" + fileSize +
                    ", uploadTime=" + uploadTime +
                    ", deadTime=" + deadTime +
                    ", modifyTime=" + modifyTime +
                    ", downloadTimes=" + downloadTimes +
                    ", uploader=" + uploader +
                    ", uploaderName='" + uploaderName + '\'' +
                    '}';
        }
    }

    public class Folder {
        private String folderId;//	文件夹ID
        private String folderName;//	文件名
        private Long createTime;//	创建时间
        private Long creator;//	创建者
        private String creatorName;//	创建者名字
        private Integer totalFileCount;//	子文件数量

        public String getFolderId() {
            return folderId;
        }

        public void setFolderId(String folderId) {
            this.folderId = folderId;
        }

        public String getFolderName() {
            return folderName;
        }

        public void setFolderName(String folderName) {
            this.folderName = folderName;
        }

        public Long getCreateTime() {
            return createTime;
        }

        public void setCreateTime(Long createTime) {
            this.createTime = createTime;
        }

        public Long getCreator() {
            return creator;
        }

        public void setCreator(Long creator) {
            this.creator = creator;
        }

        public String getCreatorName() {
            return creatorName;
        }

        public void setCreatorName(String creatorName) {
            this.creatorName = creatorName;
        }

        public Integer getTotalFileCount() {
            return totalFileCount;
        }

        public void setTotalFileCount(Integer totalFileCount) {
            this.totalFileCount = totalFileCount;
        }

        @Override
        public String toString() {
            return "Folder{" +
                    "folderId='" + folderId + '\'' +
                    ", folderName='" + folderName + '\'' +
                    ", createTime=" + createTime +
                    ", creator=" + creator +
                    ", creatorName='" + creatorName + '\'' +
                    ", totalFileCount=" + totalFileCount +
                    '}';
        }
    }
}
