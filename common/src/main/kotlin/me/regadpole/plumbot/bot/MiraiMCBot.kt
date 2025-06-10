package me.regadpole.plumbot.bot

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.refreshAfterWrite
import me.dreamvoid.miraimc.api.MiraiBot
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.listener.BotHandler
import me.regadpole.plumbot.listener.MiraiMCHandler
import me.regadpole.plumbot.utils.TextToImg
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.time.Duration.Companion.minutes

class MiraiMCBot(val plugin: PlumBot): BotImpl {

    val botId = plugin.config.getLong("bot", "miraimc", "botId")
    private val bot = MiraiBot.getBot(botId)

    override var handler: BotHandler? = null
    lateinit var cache: AsyncLoadingCache<Long, LinkedHashMap<String, String>>
    lateinit var idCache: AsyncCache<Long, Long>

    /**
     * Start a bot
     */
    override fun start(): IBot {
        handler = MiraiMCHandler(plugin, this)

        if (plugin.config.getBoolean("feature", "load", "enable")) plugin.config.getLongList("groups").forEach {
            sendMsg(true, it,
                Messages.load
                , plugin.config.getBoolean("feature", "load", "pic"))
        }

        idCache = Caffeine.newBuilder()
            .recordStats()
            .buildAsync()
        loadAllIdCache()
        cache = Caffeine.newBuilder()
            .recordStats()
            .refreshAfterWrite(10.minutes)
            .buildAsync{key -> idCache.getIfPresent(key)?.get(10, TimeUnit.SECONDS)?.let { refreshCache(it, key) } }
        loadAllCache()

        return this
    }

    /**
     * Stop a bot
     */
    override fun shutdown() {
        if (plugin.config.getBoolean("feature", "load", "enable")) plugin.config.getLongList("groups").forEach {
            if (plugin.config.getBoolean("feature", "load", "pic")) sendGroupPicWithText(it, Messages.unload)
            else sendGroupMsg(it, Messages.unload)
        }
        handler = null
        idCache.synchronous().invalidateAll()
        cache.synchronous().invalidateAll()
    }

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    override fun getGroupName(groupId: Long): String {
        return bot.getGroup(groupId).name
    }

    /**
     * check if the user is in group
     * @param userId the id of user
     * @param groupId the id of group
     * @return true if the user is in group, false otherwise
     */
    override fun checkUserInGroup(userId: Long, groupId: Long): Boolean {
        return userId in bot.getGroup(groupId).members.map { member -> member.id }.stream().toList()
    }

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendGroupMsg(targetId: Long, message: String) {
        bot.getGroup(targetId).sendMessage(message)
    }

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendUserMsg(targetId: Long, message: String) {
        bot.getFriend(targetId).sendMessage(message)
    }

    /**
     * Send a picture to group
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendGroupPicWithText(targetId: Long, message: String) {
        val group = bot.getGroup(targetId)
        val imageId = group.uploadImage(TextToImg.toFile(message))
        group.sendMessageMirai("[mirai:image:$imageId]")
    }

    /**
     * Send a picture to user
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendUserPicWithText(targetId: Long, message: String) {
        val friend = bot.getFriend(targetId)
        val imageId = friend.uploadImage(TextToImg.toFile(message))
        friend.sendMessageMirai("[mirai:image:$imageId]")
    }

    /**
     * Get user's name from group list
     * @param groupId group's id
     * @param targetId user's id
     * @return the name of user
     */
    override fun getGroupUserName(groupId: Long, targetId: Long): String {
        val name: String
        try {
            val map = cache.get(targetId, Function {
                refreshCache(groupId, targetId)
            }).get(10, TimeUnit.SECONDS)
            name = map.getOrDefault("name", targetId.toString())
            return name
        } catch (_: Exception) {
            return targetId.toString()
        }
    }

    /**
     * Get user's card from group list
     * @param groupId group's id
     * @param targetId user's id
     * @return the card of user in group
     */
    override fun getGroupUserCard(groupId: Long, targetId: Long): String {
        val name: String
        try {
            val map = cache.get(targetId, Function{
                refreshCache(groupId, targetId)
            }).get(10, TimeUnit.SECONDS)
            name = if (map["card"].isNullOrBlank()) getGroupUserName(groupId, targetId)
            else map.getOrDefault("card", targetId.toString())
            return name
        } catch (_: Exception) {
            return targetId.toString()
        }
    }

    private fun loadAllCache(){
        plugin.config.getLongList("groups").forEach { groupId ->
            bot.getGroup(groupId).members.forEach{ it ->
                cache.put(it.id, CompletableFuture.supplyAsync {
                    linkedMapOf("card" to it.nameCard, "name" to it.nick)
                })
            }
        }
    }

    private fun loadAllIdCache(){
        plugin.config.getLongList("groups").forEach { groupId ->
            bot.getGroup(groupId).members.forEach{ it ->
                idCache.put(it.id, CompletableFuture.supplyAsync { groupId })
            }
        }
    }

    private fun refreshCache(groupId: Long, userId: Long): LinkedHashMap<String, String>{
        val lock = Object()
        var map = linkedMapOf("card" to userId.toString(), "name" to userId.toString())
        synchronized(lock) {
            val member = bot.getGroup(groupId).getMember(userId)
            map = linkedMapOf("card" to member.nameCard, "name" to member.nick)
            lock.notifyAll()
        }
        synchronized(lock) {
            lock.wait()
            return map
        }
    }
}