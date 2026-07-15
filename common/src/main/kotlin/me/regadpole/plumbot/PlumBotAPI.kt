package me.regadpole.plumbot

import me.regadpole.plumbot.api.ListenerHandle
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.event.UserDecreaseEvent
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.bot.BotExtensionRegistry
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider

/**
 * PlumBot's public API entry-point for third-party plugins.
 *
 * Lifecycle:
 *  - **Internal**: [PlumBotBukkit.onEnable] MUST call [attach] before any
 *    third-party plugin queries the API.
 *  - **External**: third-party plugins MUST obtain the API via Bukkit
 *    `ServicesManager.getRegistration(PlumBotAPI::class.java)` or by calling
 *    [getInstance] from the onEnable path (after `softdepend` ordering).
 *
 * Behavior compatibility:
 *  - The four legacy getters ([getDatabase] / [getBotProvider] / [getMessages]
 *    / [getConfig]) keep their original signatures so existing source
 *    compatibility is preserved.
 *  - The new instance-state makes [PlumBotAPI] a singleton (`object`).
 *
 * Stability: see `@StableApi` / `@PublicApi` on individual members.
 */
@StableApi
object PlumBotAPI {

    @Volatile
    private var attached: PlumBot? = null

    private val lock = Any()

    private var extensionRegistry: BotExtensionRegistry? = null

    /**
     * Attach the API to a running [PlumBot]. Called from
     * `PlumBotBukkit.onEnable`. Idempotent: later calls overwrite the previous
     * instance and emit a warn log.
     */
    @PublicApi
    fun attach(plugin: PlumBot) {
        synchronized(lock) {
            if (attached != null && attached !== plugin) {
                plugin.log(me.regadpole.plumbot.internal.LogLevel.WARN,
                    "PlumBotAPI already attached to ${attached?.javaClass?.name}; replacing with ${plugin.javaClass.name}")
            }
            attached = plugin
            if (extensionRegistry == null) {
                extensionRegistry = BotExtensionRegistry(BotProvider)
            }
        }
    }

    /**
     * Detach the API. Called from `PlumBotBukkit.onDisable`. After
     * [detach], [getInstance] will throw until the next [attach].
     */
    @PublicApi
    fun detach() {
        synchronized(lock) {
            attached = null
            extensionRegistry = null
        }
    }

    /**
     * Returns the attached [PlumBotAPI] singleton. Third-party plugins should
     * usually obtain the API via Bukkit `ServicesManager`, but this static
     * accessor is also provided as a convenience for non-Bukkit contexts
     * (e.g. unit tests) and reload scenarios where the registry might not
     * yet contain the registration.
     *
     * @throws IllegalStateException when [attach] has not yet been called.
     */
    @StableApi
    fun getInstance(): PlumBotAPI = this

    /**
     * Returns the currently attached [PlumBot] instance, or `null` if the
     * API hasn't been attached yet.
     */
    @PublicApi
    fun getAttachedPlugin(): PlumBot? = attached

    // --------------------------------------------------------------------
    // Legacy getters — signatures unchanged from the previous `class` API.
    // --------------------------------------------------------------------

    /**
     * Get the database provider.
     * @return the database provider (singleton).
     */
    @StableApi
    fun getDatabase(): DatabaseProvider = DatabaseProvider

    /**
     * Get the bot provider. From here third-party plugins can fetch the
     * currently loaded bot, list all registered adapter factories, etc.
     * @return the bot provider (singleton).
     */
    @StableApi
    fun getBotProvider(): BotProvider = BotProvider

    /**
     * Get the global messages template. Note: this object is mutable and
     * **must not** be modified by third-party plugins. It is exposed read-only
     * via this contract.
     * @return the messages singleton.
     */
    @StableApi
    fun getMessages(): Messages = Messages

    /**
     * Get the runtime YAML configuration for the running PlumBot instance.
     * @return the configuration object held by the [attached] plugin.
     * @throws IllegalStateException when the API has not been attached.
     */
    @StableApi
    fun getConfig(): YamlConfigurator {
        val plugin = attached
            ?: error("PlumBotAPI 未初始化，请检查 PlumBot 是否已启用 (getConfig)")
        return plugin.config
    }

    // --------------------------------------------------------------------
    // New convenience entry-points.
    // --------------------------------------------------------------------

    /**
     * Get the current active bot, or `null` if no bot is loaded yet.
     */
    @PublicApi
    fun getBotOrNull(): IBot? = BotProvider.getBot()

    /**
     * Get the current active bot.
     *
     * @throws IllegalStateException when no bot is currently loaded.
     */
    @PublicApi
    fun getBot(): IBot = getBotOrNull()
        ?: error("PlumBotAPI 当前没有活跃的 bot 实例，请稍后再试或检查配置 (getBot)")

    /**
     * Send a plain text message to a group. Wraps [IBot.sendGroupMsg].
     *
     * @return `true` if a bot handled the call; `false` when no bot is loaded.
     *         **Never** throws to the caller so the host plugin's flow is not
     *         interrupted.
     */
    @PublicApi
    fun sendGroupMessage(groupId: Long, message: String): Boolean {
        val bot = BotProvider.getBot() ?: run {
            debugNoBot("sendGroupMessage(groupId=$groupId)")
            return false
        }
        return runCatching { bot.sendGroupMsg(groupId, message); true }
            .onFailure { logIt("sendGroupMessage failed", it) }
            .getOrDefault(false)
    }

    /**
     * Send a plain text message to a user. Wraps [IBot.sendUserMsg].
     */
    @PublicApi
    fun sendUserMessage(userId: Long, message: String): Boolean {
        val bot = BotProvider.getBot() ?: run {
            debugNoBot("sendUserMessage(userId=$userId)")
            return false
        }
        return runCatching { bot.sendUserMsg(userId, message); true }
            .onFailure { logIt("sendUserMessage failed", it) }
            .getOrDefault(false)
    }

    /**
     * Send a picture-with-text message to a group. Wraps
     * [IBot.sendGroupPicWithText].
     */
    @PublicApi
    fun sendGroupMessageWithImage(groupId: Long, message: String): Boolean {
        val bot = BotProvider.getBot() ?: run {
            debugNoBot("sendGroupMessageWithImage(groupId=$groupId)")
            return false
        }
        return runCatching { bot.sendGroupPicWithText(groupId, message); true }
            .onFailure { logIt("sendGroupMessageWithImage failed", it) }
            .getOrDefault(false)
    }

    /**
     * Send a picture-with-text message to a user. Wraps
     * [IBot.sendUserPicWithText].
     */
    @PublicApi
    fun sendUserMessageWithImage(userId: Long, message: String): Boolean {
        val bot = BotProvider.getBot() ?: run {
            debugNoBot("sendUserMessageWithImage(userId=$userId)")
            return false
        }
        return runCatching { bot.sendUserPicWithText(userId, message); true }
            .onFailure { logIt("sendUserMessageWithImage failed", it) }
            .getOrDefault(false)
    }

    /**
     * Trigger a graceful bot shutdown. Wraps [BotProvider.unloadBot].
     */
    @PublicApi
    fun shutdown() {
        BotProvider.unloadBot()
    }

    /**
     * Subscribe to group messages.
     *
     * The returned [ListenerHandle] can be used to deregister. Listeners are
     * called from the bot's network thread; implementations **should not**
     * block. Exceptions thrown by [handler] are caught and logged but do not
     * affect other listeners.
     */
    @PublicApi
    fun subscribeGroupMessages(handler: (GroupMessageEvent) -> Unit): ListenerHandle {
        return BotEventDispatcher.registerGroupMessageHandler(handler)
    }

    /**
     * Subscribe to user decrease events.
     *
     * @see subscribeGroupMessages for threading and exception semantics.
     */
    @PublicApi
    fun subscribeUserDecrease(handler: (UserDecreaseEvent) -> Unit): ListenerHandle {
        return BotEventDispatcher.registerUserDecreaseHandler(handler)
    }

    /**
     * Returns the [BotExtensionRegistry] used to register external
     * [me.regadpole.plumbot.bot.BotFactory] at runtime. Third-party plugins
     * SHOULD use this entry point to integrate with PlumBot's adapter
     * discovery pipeline.
     */
    @PublicApi
    fun getExtensionRegistry(): BotExtensionRegistry {
        return extensionRegistry
            ?: error("PlumBotAPI 未初始化，请检查 PlumBot 是否已启用 (getExtensionRegistry)")
    }

    // --------------------------------------------------------------------
    // Internal helpers.
    // --------------------------------------------------------------------

    private fun debugNoBot(op: String) {
        val plugin = attached ?: return
        plugin.log(me.regadpole.plumbot.internal.LogLevel.DEBUG,
            "$op ignored: no bot currently loaded")
    }

    private fun logIt(op: String, e: Throwable) {
        val plugin = attached ?: return
        plugin.log(me.regadpole.plumbot.internal.LogLevel.WARN,
            "$op failed: ${e.message ?: e.javaClass.simpleName}")
    }
}
