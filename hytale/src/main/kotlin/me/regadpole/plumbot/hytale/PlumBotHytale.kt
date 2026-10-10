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

    private val platformScheduler by lazy { HytalePlatformScheduler() }
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
        // 1. 动态注入 SQLite/MySQL 运行时驱动
        dependencyLoader.loadDependencies()
        // 1.1 把 Libby 装载器交给渲染门户：engine=playwright 且真正出图时才按需下载 Playwright
        RenderProvider.registerLibraryLoader(dependencyLoader)
        // 1.2 装配 Hytale 外观解析器：服主覆盖目录 + 平台日志出口
        HytaleAvatarResolver.overrideRoot = dataDirectory.resolve("assets/characters").toFile()
        HytaleAvatarResolver.logSink = { message -> logger.atWarning().log(message) }
        // 2. 加载与自愈补齐配置文件
        loadConfig()
        metrics
    }

    override fun start() {
        // 3. 注册内置 OneBot 工厂与公开 API 门面
        BotProvider.clearFactories()
        BotProvider.registerFactory(OneBotFactory)

        val apiImpl = createPlumBotApi(this)
        PlumBotApiProvider.register(apiImpl)
        BotEventDispatcher.attachedPlugin = this

        // 4. 初始化持久层与 Bot 客户端
        enable()

        // 5. 初始化敏感词过滤
        val filterThesaurus = FilterThesaurusManager(platformContext)
        FilterManagerHolder.manager = filterThesaurus
        // 异步预热词库：走插件自有 IO 作用域，不再占用平台调度线程
        filterThesaurus.reloadAsync()

        // 6. 注册事件网桥 (最前置白名单鉴权 + 就绪后进服广播)
        val eventBridge = GameEventBridge(platform)
        val loginService = PlayerLoginService(platform)
        HytaleServerListener(this, eventRegistry, eventBridge, loginService).register()

        // 7. 注册 Hytale 原生管理指令 /plumbot
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
 * 注册 bStats「适配器类别」chart（决策：chart 做适配器类别）。
 *
 * 只上报类别名（onebot / miraimc / unknown），不含任何标识信息；
 * 配置未就绪时回退到当前已加载适配器的类型。
 */
private fun registerAdapterChart(metrics: org.bstats.hytale.Metrics) {
    metrics.addCustomChart(SimplePie(AdapterTelemetry.CHART_ID) { AdapterTelemetry.current() })
}
