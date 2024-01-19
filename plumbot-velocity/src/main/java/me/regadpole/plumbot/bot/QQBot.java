package me.regadpole.plumbot.bot;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.event.qq.QQEvent;
import me.regadpole.plumbot.internal.Config;
import sdk.client.ClientFactory;
import sdk.client.impl.GroupClient;
import sdk.client.impl.MessageClient;
import sdk.client.response.CQFile;
import sdk.client.response.ForwardMessage;
import sdk.client.response.GroupInfo;
import sdk.client.response.Message;
import sdk.config.CQConfig;
import sdk.connection.Connection;
import sdk.connection.ConnectionFactory;
import sdk.event.EventDispatchers;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.event.notice.GroupDecreaseNotice;
import sdk.listener.SimpleListener;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class QQBot implements Bot {

    public QQBot(PlumBot plugin) {
        this.plugin = plugin;
    }

    private PlumBot plugin;
    private static CQConfig http_config;
    private GroupClient client =  new ClientFactory(http_config).createGroupClient();
    private MessageClient messageClient =  new ClientFactory(http_config).createMessageClient();
    private static QQEvent qqEvent;

    @Override
    public void start() {
        qqEvent = new QQEvent(plugin);
        plugin.getLogger().info("QQ事件监听器注册完毕");
        plugin.getServer().getScheduler().buildTask(this, () -> {
            http_config = new CQConfig(Config.bot.Bot.gocqhttp.HTTP, Config.bot.Bot.gocqhttp.Token, Config.bot.Bot.gocqhttp.IsAccessToken);
            LinkedBlockingQueue<String> blockingQueue = new LinkedBlockingQueue();//使用队列传输数据
            Connection connection = null;
            try {
                connection = ConnectionFactory.createHttpServer(Config.bot.Bot.gocqhttp.ListenPort,"/",blockingQueue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            connection.create();
            EventDispatchers dispatchers = new EventDispatchers(blockingQueue);//创建事件分发器
            dispatchers.addListener(new SimpleListener<PrivateMessage>() {//私聊监听
                @Override
                public void onMessage(PrivateMessage privateMessage) {
                    qqEvent.onFriendMessageReceive(privateMessage);
                }
            });
            dispatchers.addListener(new SimpleListener<GroupMessage>() {//群聊消息监听
                @Override
                public void onMessage(GroupMessage groupMessage) {
                    List<Long> groups = Config.bot.Groups;
                    for (long groupID : groups) {
                        if (groupID == groupMessage.getGroupId()) {
                            qqEvent.onGroupMessageReceive(groupMessage);
                        }
                    }
                }
            });
            dispatchers.addListener(new SimpleListener<GroupDecreaseNotice>() {//群聊人数减少监听
                @Override
                public void onMessage(GroupDecreaseNotice groupDecreaseNotice) {
                    List<Long> groups = Config.bot.Groups;
                    for (long groupID : groups) {
                        if (groupID == groupDecreaseNotice.getGroupId()) {
                            qqEvent.onGroupDecreaseNotice(groupDecreaseNotice);
                        }
                    }
                }
            });
            dispatchers.start(10);//线程组处理任务

            List<Long> groups = Config.bot.Groups;
            for (long groupID : groups) {
                sendGroupMsg("PlumBot已启动", groupID);
            }
        }).schedule();
    }

    @Override
    public void shutdown() {
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups) {
            sendMsg(true, "PlumBot已关闭", groupID);
        }
    }

    @Override
    public void sendMsg(boolean isGroup, String msg, long id) {
        if (id == 0L) {return;}
        if ("".equals(msg)) {return;}
        plugin.getServer().getScheduler().buildTask(this, () -> {
            if (isGroup) {
                this.sendGroupMsg(msg, id);
            } else {
                this.sendPrivateMsg(msg, id);
            }
        }).schedule();
    }

    public void sendCQMsg(boolean isGroup, String msg, long id) {
        if (id == 0L) {return;}
        if ("".equals(msg)) {return;}
        plugin.getServer().getScheduler().buildTask(this, () -> {
            if (isGroup) {
                this.sendGroupCQMsg(msg, id);
            } else {
                this.sendPrivateCQMsg(msg, id);
            }
        }).schedule();
    }

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

    /**
     * 发送私聊消息
     */
    public void sendPrivateCQMsg(String msg, long userID){
        messageClient.sendPrivateMsg(userID, msg, false);
    }

    /**
     * 发送群聊消息
     */
    public void sendGroupCQMsg(String msg, long groupId) {
        messageClient.sendGroupMsg(groupId, msg, false);
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
    public GroupInfo getGroupInfo(long groupId) {
        GroupInfo groupInfo = client.getGroupInfo(groupId);
        return groupInfo;
    }

    @Override
    public String getGroupName(long groupId) {
        return getGroupInfo(groupId).getGroupName();
    }
}
