package sdk.client.response;

/**
 * 合并转发
 */
public class ForwardNode {

    private String type = "node";
    private Data data = new Data();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ForwardNode{" +
                "type='" + type + '\'' +
                ", data=" + data +
                '}';
    }

    public class Data{
        private String name;
        private Integer id;
        private Long uin;
        private Object content;
        private String seq;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Long getUin() {
            return uin;
        }

        public void setUin(Long uin) {
            this.uin = uin;
        }

        public Object getContent() {
            return content;
        }

        public void setContent(Object content) {
            this.content = content;
        }

        public String getSeq() {
            return seq;
        }

        public void setSeq(String seq) {
            this.seq = seq;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "name='" + name + '\'' +
                    ", id=" + id +
                    ", uin=" + uin +
                    ", content=" + content +
                    ", seq='" + seq + '\'' +
                    '}';
        }
    }
}
