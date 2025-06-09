package me.regadpole.plumbot.bot

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.refreshAfterWrite
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.listener.BotHandler
import me.regadpole.plumbot.listener.OnebotListener
import me.regadpole.plumbot.utils.TextToImg
import top.alazeprt.aonebot.action.*
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import top.alazeprt.aonebot.result.Group
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.time.Duration.Companion.minutes

class Onebot(val plugin: PlumBot, override val client: WebsocketBotClient): BotImpl {

    var handler: BotHandler? = null
        private set
    lateinit var cache: AsyncLoadingCache<Long, LinkedHashMap<String, String>>
    lateinit var idCache: AsyncCache<Long, Long>


    override fun start(): BotImpl {
        handler = BotHandler(plugin, this)
        client.connect()
        client.registerEvent(OnebotListener(this))

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

    override fun shutdown() {
        if (plugin.config.getBoolean("feature", "load", "enable")) plugin.config.getLongList("groups").forEach {
            if (plugin.config.getBoolean("feature", "load", "pic")) sendGroupPicWithText(it, Messages.unload)
            else sendGroupMsg(it, Messages.unload)
        }
        handler = null
        idCache.synchronous().invalidateAll()
        cache.synchronous().invalidateAll()
        client.disconnect()
    }

    override fun getGroupName(groupId: Long): String {
        val lock = Object()
        var name = "Null"
        client.action(GetGroupInfo(groupId)) { group: Group ->
            synchronized(lock) {
                name = group.groupName
                lock.notifyAll()
            }
        }
        synchronized(lock) {
            lock.wait()
            return name
        }
    }

    override fun checkUserInGroup(userId: Long, groupId: Long): Boolean {
        val lock = Object()
        var isHere = false
        client.action(GetGroupMemberList(groupId)) { memberList -> memberList.forEach{
            synchronized(lock) {
                if (it.member.userId == userId) isHere = true
                lock.notifyAll()
            }
        }}
        synchronized(lock) {
            lock.wait()
//            synchronized(suplock) {if (canNotify) suplock.notifyAll()}
            return isHere
        }
    }

    override fun sendGroupMsg(targetId: Long, message: String) {
        client.action(SendGroupMessage(targetId, message, false))
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        client.action(SendPrivateMessage(targetId, message, false))
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        val msg = TextToImg.toImgCQCode(message)
        client.action(SendGroupMessage(targetId, msg, false))
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        val msg = TextToImg.toImgCQCode(message)
        client.action(SendPrivateMessage(targetId, msg, false))
    }
    override fun getGroupUserName(groupId: Long, targetId: Long): String {
//        val lock = Object()
        val name: String
        try {
//            client.action(GetGroupMemberInfo(groupId, targetId)) {
//                synchronized(lock) {
//                    try {
//                        name = it.member.nickname
//                    } catch (_: Exception) {}
//                    lock.notifyAll()
//                }
//            }
//            synchronized(lock) {
//                lock.wait()
//                return name?:targetId.toString()
//            }
            val map = cache.get(targetId, Function{
                refreshCache(groupId, targetId)
            }).get(10, TimeUnit.SECONDS)
            name = map.getOrDefault("name", targetId.toString())
            return name
        } catch (_: Exception) {
            return targetId.toString()
        }
    }

    override fun getGroupUserCard(groupId: Long, targetId: Long): String {
//        val lock = Object()
        val name: String
        try {
//            client.action(GetGroupMemberInfo(groupId, targetId)) {
//                synchronized(lock) {
//                    try {
//                        name = it.card
//                    } catch (_: Exception) {}
//                    lock.notifyAll()
//                }
//            }
//            synchronized(lock) {
//                lock.wait()
//                return name?:targetId.toString()
//            }
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
            client.action(GetGroupMemberList(groupId)) { users ->
                users.forEach {
                    cache.put(it.member.userId, CompletableFuture.supplyAsync {
                        linkedMapOf("card" to it.card, "name" to it.member.nickname)
                    })
                }
            }
        }
    }

    private fun loadAllIdCache(){
        plugin.config.getLongList("groups").forEach { groupId ->
            client.action(GetGroupMemberList(groupId)) { users ->
                users.forEach {
                    idCache.put(it.member.userId, CompletableFuture.supplyAsync { groupId })
                }
            }
        }
    }

    private fun refreshCache(groupId: Long, userId: Long): LinkedHashMap<String, String>{
        val lock = Object()
        var map = linkedMapOf("card" to userId.toString(), "name" to userId.toString())
        client.action(GetGroupMemberInfo(groupId, userId)) {
            synchronized(lock) {
                map = linkedMapOf("card" to it.card, "name" to it.member.nickname)
                lock.notifyAll()
            }
        }
        synchronized(lock) {
            lock.wait()
            return map
        }
    }

}