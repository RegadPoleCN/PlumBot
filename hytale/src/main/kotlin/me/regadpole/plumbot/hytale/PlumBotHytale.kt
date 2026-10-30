// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.adapter.onebot.OneBotFactory
import me.regadpole.plumbot.api.PlumBotApiProvider
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.internal.AdapterTelemetry
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.filter.FilterManagerHolder
import me.regadpole.plumbot.filter.FilterThesaurusManager
import me.regadpole.plumbot.hytale.command.HytalePlumBotCommand
import me.regadpole.plumbot.hytale.listener.HytalePluginListener
import me.regadpole.plumbot.hytale.listener.HytaleServerListener
import me.regadpole.plumbot.hytale.platform.*
import me.regadpole.plumbot.internal.createPlumBotApi
import me.regadpole.plumbot.server.GameEventBridge
import me.regadpole.plumbot.server.PlayerLoginService
import me.regadpole.plumbot.hytale.avatar.HytaleAvatarResolver
import me.regadpole.plumbot.render.RenderProvider
import net.kyori.adventure.text.Component
import org.bstats.charts.SimplePie
import org.bstats.hytale.Metrics
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

class PlumBotHytale(init: JavaPluginInit) : JavaPlugin(init), PlumBot {

    val plumBotPlugin: me.regadpole.plumbot.api.Plugin by lazy { HytalePlugin(this) }

    private val metrics: Metrics by lazy { Metrics(this, 34424).also { registerAdapterChart(it) } }

    override lateinit var datasource: YamlConfigurator
    override lateinit var config: YamlConfigurator
    override var debugProvider = DebugProvider(this)

    @get:JvmName("getPlumBotDataDirectory")
    @set:JvmName("setPlumBotDataDirectory")
    override var dataDirectory: Path
        get() = super.getDataDirectory()
        set(_) {}

    private val dependencyLoader by lazy {
        HytaleDependencyLoader(this) { if (::config.isInitialized) config else null }
    }

    // 调度器需要告警出口：拿不到世界 tick 线程时 runSync 会悄悄回退异步，必须留痕。
    private val platformScheduler by lazy { HytalePlatformScheduler(warn = { logger.atWarning().log(it) }) }
    private val playerService by lazy { HytalePlayerService() }

    private val platformContext: HytalePlatformContext by lazy {
        HytalePlatformContext(
            plugin = this,
            dataDirectory = dataDirectory,
            configProvider = { config },
            datasourceProvider = { datasource },
            scheduler = platformScheduler,
            playerService = playerService,
            debugProvider = debugProvider
        )
    }

    override val platform: PlatformContext get() = platformContext

    override fun setup() {
        dependencyLoader.loadDependencies()
        // 交给渲染门户：engine=playwright 且真正出图时才按需下载 Playwright
        RenderProvider.registerLibraryLoader(dependencyLoader)
        // 外观解析器：服主覆盖目录 + 平台日志出口
        HytaleAvatarResolver.overrideRoot = dataDirectory.resolve("assets/characters").toFile()
        HytaleAvatarResolver.logSink = { message -> logger.atWarning().log(message) }
        loadConfig()
        metrics
    }

    override fun start() {
        BotProvider.clearFactories()
        BotProvider.registerFactory(OneBotFactory)

        val apiImpl = createPlumBotApi(this)
        PlumBotApiProvider.register(apiImpl)
        BotEventDispatcher.attachedPlugin = this

        enable()

        val filterThesaurus = FilterThesaurusManager(platformContext)
        FilterManagerHolder.manager = filterThesaurus
        // 异步预热词库：走插件自有 IO 作用域，不再占用平台调度线程
        filterThesaurus.reloadAsync()

        // 事件网桥：最前置的白名单鉴权 + 就绪后进服广播
        val eventBridge = GameEventBridge(platform)
        val loginService = PlayerLoginService(platform)
        HytaleServerListener(this, eventRegistry, eventBridge, loginService).register()

        commandRegistry.registerCommand(HytalePlumBotCommand(this))
    }

    fun reloadPlugin() {
        loadConfig()
        debugProvider.reload()
        // 统一经门户刷新渲染配置（含字体），并把新主题管理器传播给各驱动
        applyRenderConfig()
        RenderProvider.getOrNull()?.reload()
        // 词库重载已在自有 IO 作用域派发，无需再借平台调度线程
        FilterManagerHolder.manager?.reloadAsync()
        logger.atInfo().log("[PlumBot] 配置文件、字体资源与敏感词库已成功重载！")
    }

    override fun shutdown() {
        disable()
        platformScheduler.shutdown()
        runCatching {
            val hytalePluginListener = HytalePluginListener()
            for (p in com.hypixel.hytale.server.core.plugin.PluginManager.get().plugins) {
                hytalePluginListener.onPluginUnload(p)
            }
        }
        FilterManagerHolder.manager = null
        PlumBotApiProvider.unregister()
        BotEventDispatcher.attachedPlugin = null
    }

    override fun log(level: LogLevel, log: String) = platformContext.log(level, log)
    override fun sendMessage(message: Component) = platformContext.sendMessage(message)
    override fun kickPlayer(name: String) = playerService.kickPlayer(name)
    override fun listPlayers(): List<String> = playerService.listPlayers()
    override fun listPlayerString(): String = playerService.listPlayerString()
    override fun loadDependencies() = dependencyLoader.loadDependencies()
}

/**
 * 注册 bStats「适配器类别」chart：只上报类别名（onebot / miraimc / unknown），
 * 不含任何标识信息；配置未就绪时回退到当前已加载适配器的类型。
 */
private fun registerAdapterChart(metrics: org.bstats.hytale.Metrics) {
    metrics.addCustomChart(SimplePie(AdapterTelemetry.CHART_ID) { AdapterTelemetry.current() })
}
