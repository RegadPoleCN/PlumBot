package sdk.event.global;


/**
 * 请求类型适用
 */
public class Request extends Message {
    private String requestType;//请求类型

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    @Override
    public String toString() {
        return "Request{" +
                "requestType='" + requestType + '\'' +
                "} " + super.toString();
    }
}
