/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.bukkit

import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.adapter.miraimc.MiraiMCFactory
import me.regadpole.plumbot.adapter.onebot.OneBotFactory
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.bukkit.listener.MiraiMCListener
import me.regadpole.plumbot.bukkit.listener.PluginListener
import me.regadpole.plumbot.bukkit.listener.ServerListener
import me.regadpole.plumbot.bukkit.platform.*
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.LogLevel
import net.kyori.adventure.text.Component
import org.bstats.bukkit.Metrics
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Path
import kotlin.io.path.pathString

class PlumBotBukkit: JavaPlugin(), PlumBot{
    private val dependencyLoader: BukkitDependencyLoader by lazy {
        BukkitDependencyLoader(this) { if (::config.isInitialized) config else null }
    }
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
            loadDependencies()
        } catch (e: Exception) {
            logger.severe("[PlumBot] 依赖加载失败: ${e.message}")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        try {
            datasource = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")
                ?: error("Failed to load datasource.yml")
            config = YamlConfigurator.createConfig(dataDirectory, "config.yml")
                ?: error("Failed to load config.yml")
        } catch (e: Exception) {
            logger.severe("[PlumBot] 基础配置加载失败: ${e.message}")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        val metrics = Metrics(this, 19427)

        val botType = config.getString("bot", "type") ?: "onebot"
        val useMirai = botType.equals("miraimc", ignoreCase = true)
        val miraiAvailable = Bukkit.getPluginManager().isPluginEnabled("MiraiMC")

        if (useMirai && !miraiAvailable) {
            logger.severe("MiraiMC is not enabled! Please install MiraiMC to use Mirai bot.")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }

        BotProvider.clearFactories()
        BotProvider.registerFactory(OneBotFactory)
        if (useMirai && miraiAvailable) {
            BotProvider.registerFactory(MiraiMCFactory)
        }

        val apiImpl = me.regadpole.plumbot.internal.createPlumBotApi(this)
        me.regadpole.plumbot.api.PlumBotApiProvider.register(apiImpl)
        me.regadpole.plumbot.bot.BotEventDispatcher.attachedPlugin = this

        server.servicesManager.register(BotProvider::class.java, BotProvider, this, ServicePriority.Normal)
        server.servicesManager.register(DatabaseProvider::class.java, DatabaseProvider, this, ServicePriority.Normal)
        server.servicesManager.register(PlumBotAPI::class.java, apiImpl, this, ServicePriority.Normal)

        enable()

        val filterThesaurus = me.regadpole.plumbot.filter.FilterThesaurusManager(platformContext)
        me.regadpole.plumbot.filter.FilterManagerHolder.manager = filterThesaurus
        platformScheduler.runAsync {
            kotlinx.coroutines.runBlocking {
                filterThesaurus.reload()
            }
        }

        server.pluginManager.registerEvents(ServerListener(this), this)
        server.pluginManager.registerEvents(PluginListener(), this)
        if (useMirai) {
            server.pluginManager.registerEvents(MiraiMCListener(this), this)
        }

        val mainCommand = me.regadpole.plumbot.bukkit.command.PlumBotCommand(this)
        getCommand("plumbot")?.apply {
            setExecutor(mainCommand)
            tabCompleter = mainCommand
        }

        logger.info("PlumBot has been enabled!")
    }

    fun reloadPlugin() {
        loadConfig()
        debugProvider.reload()
        val fontPath = config.getString("feature", "img", "file")?.replace("%plugin_folder%", dataDirectory.pathString)
        if (fontPath != null) {
            me.regadpole.plumbot.utils.TextToImg.ttfFile = java.io.File(fontPath)
            me.regadpole.plumbot.utils.TextToImg.reset()
        }
        platformScheduler.runAsync {
            kotlinx.coroutines.runBlocking {
                me.regadpole.plumbot.filter.FilterManagerHolder.manager?.reload()
            }
        }
        logger.info("[PlumBot] Configuration and resources reloaded.")
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
        me.regadpole.plumbot.filter.FilterManagerHolder.manager = null
        me.regadpole.plumbot.api.PlumBotApiProvider.unregister()
        me.regadpole.plumbot.bot.BotEventDispatcher.attachedPlugin = null
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

    override fun loadDependencies() {
        dependencyLoader.loadDependencies()
    }
}
