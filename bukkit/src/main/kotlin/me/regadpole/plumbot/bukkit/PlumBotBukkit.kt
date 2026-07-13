package me.regadpole.plumbot.bukkit

import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.adapter.miraimc.MiraiMCFactory
import me.regadpole.plumbot.adapter.onebot.OneBotFactory
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.bukkit.listener.MiraiMCListener
import me.regadpole.plumbot.bukkit.listener.ServerListener
import me.regadpole.plumbot.bukkit.platform.BukkitDependencyLoader
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformContext
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformLogger
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformMessenger
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformScheduler
import me.regadpole.plumbot.bukkit.platform.BukkitPlayerService
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

        // 插件启用时的逻辑
        server.servicesManager.register(BotProvider.javaClass, BotProvider, this, ServicePriority.Normal)
        server.servicesManager.register(DatabaseProvider.javaClass, DatabaseProvider, this, ServicePriority.Normal)
        server.servicesManager.register(PlumBotAPI::class.java, PlumBotAPI(this), this, ServicePriority.Normal)

        enable()
        server.pluginManager.registerEvents(ServerListener(this), this)
        if(useMirai) {
            // MiraiMC 监听器
            server.pluginManager.registerEvents(MiraiMCListener(this), this)
        }
        logger.info("PlumBot has been enabled!")
    }

    override fun onDisable() {
        // 插件禁用时的逻辑
        disable()
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
        return object : CompletableFuture<Void>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                val cancelled = this@asFuture.cancel()
                super.cancel(mayInterruptIfRunning)
                return cancelled
            }

            override fun isDone(): Boolean = this@asFuture.job.isCompleted

            override fun isCancelled(): Boolean = this@asFuture.job.isCancelled

            override fun get(): Void? = null

            override fun get(timeout: Long, unit: TimeUnit): Void? = null
        }
    }
}
