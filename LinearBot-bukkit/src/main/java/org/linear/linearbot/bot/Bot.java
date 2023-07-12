package org.linear.linearbot.bot;

import sdk.client.ClientFactory;
import sdk.client.impl.GroupClient;
import sdk.client.impl.MessageClient;
import sdk.client.response.CQFile;
import sdk.client.response.ForwardMessage;
import sdk.client.response.GroupInfo;
import sdk.client.response.Message;
import org.junit.Test;
import org.linear.linearbot.LinearBot;

import java.util.List;

public class Bot {

    private GroupClient client =  new ClientFactory(LinearBot.getHttp_config()).createGroupClient();
    private MessageClient messageClient =  new ClientFactory(LinearBot.getHttp_config()).createMessageClient();

    /**
     * 发送私聊消息
     */
    public void sendPrivateMsg(String msg, long userID){
        messageClient.sendPrivateMsg(userID, msg);
    }

    /**
     * 发送群聊消息
     */
    public void sendGroupMsg(String msg, long groupId) {
        messageClient.sendGroupMsg(groupId, msg);
    }

//    /**
//     * 发送群聊消息 失败
//     */
//    public void sendGrouForwardMsg() {
//        ForwardNode forwardNode = new ForwardNode();
//        ForwardNode.Data data = forwardNode.getData();
//        data.setName("slcower");
//        data.setContent("消息");
//        data.setSeq("消息");
//        data.setId(1720939235);
//        data.setUin(1452683658L);
//        ArrayList<ForwardNode> nodes = new ArrayList();
//        nodes.add(forwardNode);
//        nodes.add(forwardNode);
//        client.sendGroupForwardMsg(934145943L, nodes);
//    }


    /**
     * 获取消息
     */
    public void getMsg(Integer msgId) {
        Message msg = messageClient.getMsg(msgId);
        System.out.println(msg);
    }

    /**
     * 获取转发消息
     */
    public void getForwardMsg(String msgId) {
        List<ForwardMessage> msg = messageClient.getForwardMsg(msgId);
        System.out.println(msg);
    }

    /**
     * 获取图片缓存
     */
    public void getImage(String file) {
        CQFile msg = messageClient.getImage(file);
        System.out.println(msg);
    }

    /**
     * 撤回消息
     */
    public void deleteMsg(String msg, long groupId) {
        messageClient.deleteMsg( messageClient.sendGroupMsg(groupId, msg));
    }

    /**
     * 获取群信息
     */
    @Test
    public GroupInfo getGroupInfo(long groupId) {
        GroupInfo groupInfo = client.getGroupInfo(groupId);
        return groupInfo;
    }
}
