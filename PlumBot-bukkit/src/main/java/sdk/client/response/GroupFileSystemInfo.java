package sdk.client.response;

/**
 * 群文件系统信息
 */
public class GroupFileSystemInfo {
    private Integer fileCount;//文件总数
    private Integer limitCount;//文件上限
    private Long usedSpace;//已使用空间
    private Long totalSpace;//空间上限

    public Integer getFileCount() {
        return fileCount;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public Integer getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(Integer limitCount) {
        this.limitCount = limitCount;
    }

    public Long getUsedSpace() {
        return usedSpace;
    }

    public void setUsedSpace(Long usedSpace) {
        this.usedSpace = usedSpace;
    }

    public Long getTotalSpace() {
        return totalSpace;
    }

    public void setTotalSpace(Long totalSpace) {
        this.totalSpace = totalSpace;
    }

    @Override
    public String toString() {
        return "GroupFileSystemInfo{" +
                "fileCount=" + fileCount +
                ", limitCount=" + limitCount +
                ", usedSpace=" + usedSpace +
                ", totalSpace=" + totalSpace +
                '}';
    }
}
