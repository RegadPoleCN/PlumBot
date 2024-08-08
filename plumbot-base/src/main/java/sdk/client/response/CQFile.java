package sdk.client.response;

/**
 * 文件
 */
public class CQFile {
    private Long size;
    private String filename;
    private String url;

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "CQFile{" +
                "size=" + size +
                ", filename='" + filename + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
