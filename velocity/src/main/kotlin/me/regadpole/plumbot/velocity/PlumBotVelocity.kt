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

package me.regadpole.plumbot.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyReloadEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.PluginManager
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.adapter.miraimc.MiraiMCFactory
import me.regadpole.plumbot.adapter.onebot.OneBotFactory
import me.regadpole.plumbot.api.PlumBotApiProvider
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.filter.FilterManagerHolder
import me.regadpole.plumbot.filter.FilterThesaurusManager
import me.regadpole.plumbot.internal.BuildConstants
import me.regadpole.plumbot.internal.createPlumBotApi
import me.regadpole.plumbot.server.GameEventBridge
import me.regadpole.plumbot.server.PlayerLoginService
import me.regadpole.plumbot.utils.TextToImg
import me.regadpole.plumbot.velocity.command.PlumBotVelocityCommand
import me.regadpole.plumbot.velocity.listener.VelocityMiraiMCListener
import me.regadpole.plumbot.velocity.listener.VelocityPluginListener
import me.regadpole.plumbot.velocity.listener.VelocityServerListener
import me.regadpole.plumbot.velocity.platform.*
import net.kyori.adventure.text.Component
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

@Plugin(
    id = "plumbot",
    name = BuildConstants.NAME,
    version = BuildConstants.VERSION,
    description = "Minecraft and QQ Bot cross-platform sync bridge",
    authors = ["RegadPole"],
    dependencies = [
        Dependency(id = "miraimc", optional = true)
    ]
)
class PlumBotVelocity @Inject constructor(
    val server: ProxyServer,
    val slf4jLogger: Logger,
    @DataDirectory override var dataDirectory: Path,
    val pluginManager: PluginManager,
    private val metricsFactory: Metrics.Factory
) : PlumBot, me.regadpole.plumbot.api.Plugin {

    override val name: String = BuildConstants.NAME
    override val version: String = BuildConstants.VERSION
    override var isEnabled: Boolean = false

    override lateinit var datasource: YamlConfigurator
    override lateinit var config: YamlConfigurator
    override var debugProvider = DebugProvider(this)

    private val dependencyLoader by lazy {
        VelocityDependencyLoader(this, slf4jLogger, dataDirectory, pluginManager) { if (::config.isInitialized) config else null }
    }

    private val platformScheduler by lazy { VelocityPlatformScheduler(this, server) }
    private val playerService by lazy { VelocityPlayerService(server) }

    private val platformContext: VelocityPlatformContext by lazy {
        VelocityPlatformContext(
            server = server,
            slf4jLogger = slf4jLogger,
            debugProvider = debugProvider,
            dataDirectory = dataDirectory,
            configProvider = { config },
            datasourceProvider = { datasource },
            scheduler = platformScheduler,
            playerService = playerService
        )
    }

    override val platform: PlatformContext
        get() = platformContext

    val pluginListener by lazy { VelocityPluginListener(slf4jLogger) }

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        try {
            dependencyLoader.loadDependencies()
        } catch (e: Exception) {
            slf4jLogger.error("[PlumBot] 依赖库加载失败: ${e.message}")
            return
        }

        try {
            loadConfig()
        } catch (e: Exception) {
            slf4jLogger.error("[PlumBot] 基础配置加载失败: ${e.message}")
            return
        }

        val metrics = metricsFactory.make(this, 19428)

        val botType = config.getString("bot", "type") ?: "onebot"
        val useMirai = botType.equals("miraimc", ignoreCase = true)
        val miraiAvailable = server.pluginManager.isLoaded("miraimc")

        if (useMirai && !miraiAvailable) {
            slf4jLogger.error("[PlumBot] MiraiMC 插件未加载！若要使用 Mirai bot，请在 Velocity 代理端安装 MiraiMC。")
            return
        }

        BotProvider.clearFactories()
        BotProvider.registerFactory(OneBotFactory)
        if (useMirai && miraiAvailable) {
            BotProvider.registerFactory(MiraiMCFactory)
            server.eventManager.register(this, VelocityMiraiMCListener(this))
        }

        val apiImpl = createPlumBotApi(this)
        PlumBotApiProvider.register(apiImpl)
        BotEventDispatcher.attachedPlugin = this

        enable()

        // 装配敏感词过滤系统并异步预热词库
        val filterManager = FilterThesaurusManager(platformContext)
        FilterManagerHolder.manager = filterManager
        platformScheduler.runAsync {
            kotlinx.coroutines.runBlocking {
                filterManager.reload()
            }
        }

        // 注册事件网桥
        val eventBridge = GameEventBridge(platform)
        val loginService = PlayerLoginService(platform)
        server.eventManager.register(this, VelocityServerListener(platform, eventBridge, loginService))

        // 注册管理员主指令 /plumbot
        val commandManager = server.commandManager
        val commandMeta = commandManager.metaBuilder("plumbot")
            .plugin(this)
            .build()
        commandManager.register(commandMeta, PlumBotVelocityCommand(this))

        isEnabled = true
        slf4jLogger.info("[PlumBot] PlumBot Velocity 代理端支持已成功加载！")
    }

    fun reloadPlugin() {
        loadConfig()
        debugProvider.reload()
        val fontPath = config.getString("feature", "img", "file")?.replace("%plugin_folder%", dataDirectory.pathString)
        if (fontPath != null) {
            TextToImg.ttfFile = File(fontPath)
            TextToImg.reset()
        }
        platformScheduler.runAsync {
            kotlinx.coroutines.runBlocking {
                FilterManagerHolder.manager?.reload()
            }
        }
        slf4jLogger.info("[PlumBot] Configuration and resources reloaded.")
    }

    @Subscribe
    fun onProxyReload(event: ProxyReloadEvent) {
        reloadPlugin()
        slf4jLogger.info("[PlumBot] 响应 Velocity /velocity reload 指令，配置已同步重载。")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        isEnabled = false
        disable()
        runCatching {
            for (type in BotProvider.availableAdapters().map { it.type }) {
                BotProvider.unregisterFactory(type)
            }
        }
        for (pluginContainer in server.pluginManager.plugins) {
            pluginListener.onPluginUnload(pluginContainer)
        }
        FilterManagerHolder.manager = null
        PlumBotApiProvider.unregister()
        BotEventDispatcher.attachedPlugin = null
        slf4jLogger.info("[PlumBot] PlumBot Velocity 代理端已停用！")
    }

    override fun log(level: LogLevel, log: String) {
        platformContext.log(level, log)
    }

    override fun sendMessage(message: Component) {
        platformContext.sendMessage(message)
    }

    override fun kickPlayer(name: String) {
        playerService.kickPlayer(name)
    }

    override fun listPlayers(): List<String> =
        playerService.listPlayers()

    override fun listPlayerString(): String =
        playerService.listPlayerString()

    override fun loadDependencies() {
        dependencyLoader.loadDependencies()
    }
}
