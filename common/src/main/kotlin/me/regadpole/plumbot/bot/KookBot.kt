package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.listener.kook.KookListener
import me.regadpole.plumbot.tool.TextToImg
import snw.jkook.JKook
import snw.jkook.config.ConfigurationSection
import snw.jkook.config.file.YamlConfiguration
import snw.jkook.entity.User
import snw.jkook.entity.channel.Channel
import snw.jkook.entity.channel.TextChannel
import snw.jkook.event.channel.ChannelMessageEvent
import snw.jkook.event.pm.PrivateMessageReceivedEvent
import snw.jkook.message.component.BaseComponent
import snw.jkook.message.component.card.CardBuilder
import snw.jkook.message.component.card.MultipleCardComponent
import snw.jkook.message.component.card.Size
import snw.jkook.message.component.card.Theme
import snw.jkook.message.component.card.element.ImageElement
import snw.jkook.message.component.card.module.ContainerModule
import snw.jkook.util.PageIterator
import snw.kookbc.impl.CoreImpl
import snw.kookbc.impl.KBCClient
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.submitAsync
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class KookBot(private val plugin: PlumBot): Bot {

    private lateinit var kookClient: KBCClient
    private lateinit var kookBot: KookBot
    private var kookEnabled: Boolean = false

    /**
     * Start a bot
     */
    override fun start(): Bot {
        val kookFolder = File(getDataFolder(), "kook")
        val kook: KBCClient
        val kookCore = CoreImpl()
        if (JKook.getCore() == null) JKook.setCore(kookCore)
        val config: ConfigurationSection = YamlConfiguration.loadConfiguration(File(kookFolder, "kbc.yml"))
        val kookPlugins = File(kookFolder, "plugins")
        kook = me.regadpole.plumbot.internal.KookClient(
            kookCore,
            config,
            kookPlugins,
            plugin.getConfig().getConfig().bot.kook.token!!,
            "websocket"
        )
        kook.start()
        kookClient = kook
        kookBot = this
        kook.core.eventManager.registerHandlers(kook.internalPlugin, KookListener())
        val groups: List<String> = plugin.getConfig().getConfig().enableGroups
        for (groupID in groups) {
            PlumBot.getBot().sendMsg(true, "PlumBot已启动", groupID)
        }
        info("已启动kook服务")
        return this
    }

    /**
     * Stop a bot
     */
    override fun shutdown() {
        val groups: List<String> = plugin.getConfig().getConfig().enableGroups
        for (groupID in groups) {
            sendMsg(true, "PlumBot已关闭", groupID)
        }
        kookClient.shutdown()
        info("已关闭kook服务")
    }

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    override fun getGroupName(groupId: String): String {
        val channel: Channel = getChannel(groupId)
        return channel.name
    }

    /**
     * check if the user is in group
     * @param userId the id of user
     * @param groupId the id of group
     * @return true if the user is in group, false otherwise
     */
    override fun checkUserInGroup(userId: String, groupId: String): Boolean {
        val iterator: PageIterator<Set<User>> = getChannel(groupId).guild.users
        while (iterator.hasNext()) {
            for (user in iterator.next()) {
                if (user.id.equals(java.lang.String.valueOf(userId), ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendGroupMsg(targetId: String, message: String) {
        submitAsync(now = true) {
            sendChannelMessage(message, getChannel(targetId))
        }
    }

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendUserMsg(targetId: String, message: String) {
        submitAsync(now = true) {
            sendPrivateMessage(message, getUser(targetId))
        }
    }


    /**
     * Send message to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    override fun sendMsg(isGroup: Boolean, targetId: String, message: String?) {
        if (message == null || "" == message) {
            return
        }
        submitAsync(now = true) {
            if (isGroup) {
                sendChannelMessage(message, getChannel(targetId))
            } else {
                sendPrivateMessage(message, getUser(targetId))
            }
        }
    }

    private fun getUser(id: String): User {
        return kookClient.core.httpAPI.getUser(id)
    }

    fun getKookClient(): KBCClient {
        return kookClient
    }

    fun setKookEnabled(kook: Boolean) {
        kookEnabled = kook
    }

    fun isKookEnabled(): Boolean {
        return kookEnabled
    }

    fun getKookBot(): KookBot {
        return kookBot
    }

    private fun getChannel(groupId: String): TextChannel {
        return kookClient.core.httpAPI.getChannel(groupId) as TextChannel
    }

    private fun sendPrivateMessage(s: String, user: User) {
        user.sendPrivateMessage(s)
    }

    private fun sendPrivateMessage(s: BaseComponent, user: User) {
        user.sendPrivateMessage(s)
    }

    private fun createFile(s: String): String? {
        try {
            val file = File.createTempFile("PlumBot-", ".png")
            val fos = FileOutputStream(file)
            fos.write(TextToImg.toImgBinArray(s))
            fos.close()
            return kookClient.core.httpAPI.uploadFile(file)
        } catch (e: IOException) {
            severe(e.message)
        }
        return null
    }

    fun sendPrivateFileReply(e: PrivateMessageReceivedEvent, s: String) {
        val list: MutableList<ImageElement> = ArrayList()
        list.add(ImageElement(createFile(s), "", false))
        val card: MultipleCardComponent = CardBuilder()
            .setTheme(Theme.PRIMARY)
            .setSize(Size.LG)
            .addModule(ContainerModule(list))
            .build()
        submitAsync(now = true) {
            e.message.reply(card)
        }
    }

    fun sendChannelFileReply(e: ChannelMessageEvent, s: String) {
        val list: MutableList<ImageElement> = ArrayList()
        list.add(ImageElement(createFile(s), "", false))
        val card: MultipleCardComponent = CardBuilder()
            .setTheme(Theme.PRIMARY)
            .setSize(Size.LG)
            .addModule(ContainerModule(list))
            .build()
        submitAsync(now = true) {
            e.message.reply(card)
        }
    }

    private fun sendChannelMessage(s: String, channel: TextChannel) {
        channel.sendComponent(s)
    }

    private fun sendChannelMessage(s: BaseComponent, channel: TextChannel) {
        channel.sendComponent(s)
    }

    private fun sendMsg(isGroup: Boolean, message: BaseComponent, id: String) {
        if (message.toString().isEmpty()) {
            return
        }
        submitAsync(now = true) {
            if (isGroup) {
                sendChannelMessage(message, getChannel(id))
            } else {
                sendPrivateMessage(message, getUser(id))
            }
        }
    }

    fun sendChannelReply(e: ChannelMessageEvent, s: String) {
        submitAsync(now = true) {
            e.message.reply(s)
        }
    }

    fun sendPrivateReply(e: PrivateMessageReceivedEvent, s: String) {
        submitAsync(now = true) {
            e.message.reply(s)
        }
    }

}