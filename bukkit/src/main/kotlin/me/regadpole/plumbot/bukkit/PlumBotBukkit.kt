package me.regadpole.plumbot.bukkit

import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.adapter.miraimc.MiraiMCFactory
import me.regadpole.plumbot.adapter.onebot.OneBotFactory
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.bukkit.listener.MiraiMCListener
import me.regadpole.plumbot.bukkit.listener.PluginListener
import me.regadpole.plumbot.bukkit.listener.ServerListener
import me.regadpole.plumbot.bukkit.platform.BukkitDependencyLoader
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformContext
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformLogger
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformMessenger
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformScheduler
import me.regadpole.plumbot.bukkit.platform.BukkitPlayerService
import me.regadpole.plumbot.bukkit.platform.BukkitTaskHandle
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.platform.PlatformTaskHandle
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class PlumBotBukkit: JavaPlugin(), PlumBot{
    private val dependencyLoader: BukkitDependencyLoader by lazy { BukkitDependencyLoader(this) }
    private val platformLogger: BukkitPlatformLogger by lazy { BukkitPlatformLogger(logger, debugProvider) }
    private val platformScheduler: BukkitPlatformScheduler by lazy { BukkitPlatformScheduler(this) }
    private val playerService: BukkitPlayerService by lazy { BukkitPlayerService(server) { config } }
    private val platformMessenger: BukkitPlatformMessenger by lazy { BukkitPlatformMessenger(this) }
    private val platformContext: BukkitPlatformContext by lazy {
        BukkitPlatformContext(
            configProvider = { config },
            datasourceProvider = { datasource },
            dataDirectory = dataDirectory,
            logger = platformLogger,
            scheduler = platformScheduler,
            playerService = playerService,
            messenger = platformMessenger,
        )
    }

    override var dataDirectory: Path = dataFolder.toPath()
    override lateinit var datasource: YamlConfigurator
    override lateinit var config: YamlConfigurator
    override var debugProvider = DebugProvider(this)

    override val platform: PlatformContext
        get() = platformContext

    override fun onEnable() {
        try {
            datasource = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")
                ?: error("Failed to load datasource.yml")
            config = YamlConfigurator.createConfig(dataDirectory, "config.yml")
                ?: error("Failed to load config.yml")
        } catch (e: Exception) {
            logger.severe("Failed to load configuration: ${e.message}")
            e.printStackTrace()
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        val botType = config.getString("bot", "type")
        val useMirai = botType.equals("mirai", ignoreCase = true)
        val miraiAvailable = Bukkit.getPluginManager().isPluginEnabled("MiraiMC")

        if (!miraiAvailable && useMirai) {
            logger.severe("MiraiMC is not enabled! Please install MiraiMC to use Mirai bot.")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        BotProvider.clearFactories()
        BotProvider.registerFactory(OneBotFactory)
        if (useMirai && miraiAvailable) {
            BotProvider.registerFactory(MiraiMCFactory)
        }

        // 注入公开 API 与服务（BREAKING：PlumBotAPI 由 `class` 改为 `object`，
        // 不再通过构造传参；迁移路径为调用 `PlumBotAPI.attach(this)`）。
        PlumBotAPI.attach(this)

        // ServicesManager 注册 key 一律使用 `::class.java`，避免混用 `object.javaClass`。
        // 第三方插件通过 PlumBotAPI::class.java 取到 PlumBotAPI 单例，
        // 不再单独暴露宿主 plugin 引用（PluginProvider）。
        server.servicesManager.register(BotProvider::class.java, BotProvider, this, ServicePriority.Normal)
        server.servicesManager.register(DatabaseProvider::class.java, DatabaseProvider, this, ServicePriority.Normal)
        server.servicesManager.register(PlumBotAPI::class.java, PlumBotAPI, this, ServicePriority.Normal)

        enable()
        server.pluginManager.registerEvents(ServerListener(this), this)
        server.pluginManager.registerEvents(PluginListener(), this)
        if(useMirai) {
            // MiraiMC 监听器
            server.pluginManager.registerEvents(MiraiMCListener(this), this)
        }
        logger.info("PlumBot has been enabled!")
    }

    override fun onDisable() {
        // 插件禁用时的逻辑
        disable()
        // 清空注册表，避免 /reload 后残留外部 adapter。
        runCatching {
            for (type in BotProvider.availableAdapters().map { it.type }) {
                BotProvider.unregisterFactory(type)
            }
        }
        PlumBotAPI.detach()
        logger.info("PlumBot has been disabled!")
    }

    override fun log(level: LogLevel, log: String) {
        platformLogger.log(level, log)
    }

    override fun sendMessage(message: Component) {
        platformMessenger.sendMessage(message)
    }

    override fun kickPlayer(name: String) {
        playerService.kickPlayer(name)
    }

    override fun listPlayers(): List<String> {
        return playerService.listPlayers()
    }

    override fun listPlayerString(): String {
        return playerService.listPlayerString()
    }

    override fun submit(task: Runnable): Future<*> {
        return platformScheduler.run(task).asFuture()
    }

    override fun submitAsync(task: Runnable): Future<*> {
        return platformScheduler.runAsync(task).asFuture()
    }

    override fun submitLater(delay: Long, task: Runnable): Future<*> {
        return platformScheduler.runLater(delay, task).asFuture()
    }

    override fun submitLaterAsync(delay: Long, task: Runnable): Future<*> {
        return platformScheduler.runLaterAsync(delay, task).asFuture()
    }

    override fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*> {
        return platformScheduler.runTimer(delay, period, task).asFuture()
    }

    override fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*> {
        return platformScheduler.runTimerAsync(delay, period, task).asFuture()
    }

    override fun loadDependencies() {
        dependencyLoader.loadDependencies()
    }

    private fun PlatformTaskHandle.asFuture(): Future<*> {
        val bukkitTask = (this as? BukkitTaskHandle)?.task
        return object : CompletableFuture<Void>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                val cancelled = this@asFuture.cancel()
                super.cancel(mayInterruptIfRunning)
                return cancelled
            }

            override fun isDone(): Boolean = bukkitTask == null || super.isDone()

            override fun isCancelled(): Boolean = bukkitTask?.isCancelled == true || super.isCancelled()

            override fun get(): Void? = null

            override fun get(timeout: Long, unit: TimeUnit): Void? = null
        }
    }
}
