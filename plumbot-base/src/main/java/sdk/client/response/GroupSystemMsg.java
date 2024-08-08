package sdk.client.response;


import java.util.List;

/**
 * 群通知信息
 */
public class GroupSystemMsg {

    private List<InvitedRequest> invitedRequests;//邀请消息列表
    private List<JoinRequest> joinRequests;//进群消息列表

    public List<InvitedRequest> getInvitedRequests() {
        return invitedRequests;
    }

    public void setInvitedRequests(List<InvitedRequest> invitedRequests) {
        this.invitedRequests = invitedRequests;
    }

    public List<JoinRequest> getJoinRequests() {
        return joinRequests;
    }

    public void setJoinRequests(List<JoinRequest> joinRequests) {
        this.joinRequests = joinRequests;
    }

    @Override
    public String toString() {
        return "GroupSystemMsg{" +
                "invitedRequests=" + invitedRequests +
                ", joinRequests=" + joinRequests +
                '}';
    }

    public class InvitedRequest {
        private Long requestId;//请求ID
        private Long invitorUin;//邀请者
        private String invitorNick;//邀请者昵称
        private Long groupId;//群号
        private String groupName;//群名
        private Boolean checked;//是否已被处理
        private Long actor;//处理者, 未处理为0

        public Long getRequestId() {
            return requestId;
        }

        public void setRequestId(Long requestId) {
            this.requestId = requestId;
        }

        public Long getInvitorUin() {
            return invitorUin;
        }

        public void setInvitorUin(Long invitorUin) {
            this.invitorUin = invitorUin;
        }

        public String getInvitorNick() {
            return invitorNick;
        }

        public void setInvitorNick(String invitorNick) {
            this.invitorNick = invitorNick;
        }

        public Long getGroupId() {
            return groupId;
        }

        public void setGroupId(Long groupId) {
            this.groupId = groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public Boolean getChecked() {
            return checked;
        }

        public void setChecked(Boolean checked) {
            this.checked = checked;
        }

        public Long getActor() {
            return actor;
        }

        public void setActor(Long actor) {
            this.actor = actor;
        }

        @Override
        public String toString() {
            return "InvitedRequest{" +
                    "requestId=" + requestId +
                    ", invitorUin=" + invitorUin +
                    ", invitorNick='" + invitorNick + '\'' +
                    ", groupId=" + groupId +
                    ", groupName='" + groupName + '\'' +
                    ", checked=" + checked +
                    ", actor=" + actor +
                    '}';
        }
    }

    public class JoinRequest {
        private Long requestId;//请求ID
        private Long requesterUin;//请求者ID
        private String requesterNick;//请求者昵称
        private String message;//验证消息
        private Long groupId;//群号
        private String groupName;//群名
        private Boolean checked;//是否已被处理
        private Long actor;//处理者, 未处理为0

        public Long getRequestId() {
            return requestId;
        }

        public void setRequestId(Long requestId) {
            this.requestId = requestId;
        }

        public Long getRequesterUin() {
            return requesterUin;
        }

        public void setRequesterUin(Long requesterUin) {
            this.requesterUin = requesterUin;
        }

        public String getRequesterNick() {
            return requesterNick;
        }

        public void setRequesterNick(String requesterNick) {
            this.requesterNick = requesterNick;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getGroupId() {
            return groupId;
        }

        public void setGroupId(Long groupId) {
            this.groupId = groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public Boolean getChecked() {
            return checked;
        }

        public void setChecked(Boolean checked) {
            this.checked = checked;
        }

        public Long getActor() {
            return actor;
        }

        public void setActor(Long actor) {
            this.actor = actor;
        }

        @Override
        public String toString() {
            return "JoinRequest{" +
                    "requestId=" + requestId +
                    ", requesterUin=" + requesterUin +
                    ", requesterNick='" + requesterNick + '\'' +
                    ", message='" + message + '\'' +
                    ", groupId=" + groupId +
                    ", groupName='" + groupName + '\'' +
                    ", checked=" + checked +
                    ", actor=" + actor +
                    '}';
        }
    }
}
