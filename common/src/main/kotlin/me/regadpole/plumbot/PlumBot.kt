// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.config.MessagesBinder
import me.regadpole.plumbot.config.RenderTextStyleReader
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.database.MySQL
import me.regadpole.plumbot.database.SQLite
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.internal.ShutdownRegistry
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.task.TaskProviderImpl
import me.regadpole.plumbot.render.RenderConfig
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.utils.HttpConstants
import net.kyori.adventure.text.Component
import taboolib.module.database.Database
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

@StableApi
interface PlumBot {

    var datasource: YamlConfigurator
    var config: YamlConfigurator
    var dataDirectory: Path
    var debugProvider: DebugProvider

    fun log(level: LogLevel, log: String)
    fun sendMessage(message: Component)
    fun kickPlayer(name: String)
    fun listPlayers(): List<String>
    fun listPlayerString(): String
    fun loadDependencies()

    val platform: PlatformContext

    fun enable() {
        loadConfig()
        log(LogLevel.INFO, "Config loaded!")
        debugProvider.load()
        log(LogLevel.INFO, "Debugging loaded!")
        // 装配渲染门户（唯一实例 + 唯一生命周期），并把字体与下载 UA 作为配置注入，
        // 取代旧形态：四个模块各自去写渲染引擎的全局可变状态。
        applyRenderConfig()
        loadDatabase()
        log(LogLevel.INFO, "Database initialized!")
        loadBot()
        log(LogLevel.INFO, "Bot started!")
    }

    fun disable() {
        BotProvider.unloadBot()
        log(LogLevel.INFO, "Bot stopped!")
        // 停服路径必须**等待**引擎拆除：shutdown() 是 fire-and-forget，若 disable() 立刻返回，
        // 平台随即关闭插件类加载器，而回收线程仍在执行 PlaywrightImpl.close() —— 线上实测抛
        // "The plugin classloader for PlumBot has thrown a zip file error."。
        val drained = RenderProvider.shutdownAndAwait()
        if (drained) {
            log(LogLevel.INFO, "Render portal shutdown!")
        } else {
            log(LogLevel.WARN, "Render portal shutdown timed out; Chromium driver will be reaped with the JVM.")
        }
        DatabaseProvider.shutdown()
        log(LogLevel.INFO, "Database closed!")
        TaskProviderImpl.shutdown()
        log(LogLevel.INFO, "TaskProvider shutdown!")
        debugProvider.unload()
        log(LogLevel.INFO, "Debugging unloaded!")

        // 兜底：关闭所有自行登记的资源（消息广播、词库重载等作用域）。
        // 放在最后执行，确保即使上面某一环提前失败，这些作用域也不会随 reload 泄漏。
        val failed = ShutdownRegistry.runAll()
        if (failed > 0) {
            log(LogLevel.WARN, "有 $failed 个组件释放失败，详见控制台日志。")
        }
    }

    fun loadBot() {
        TaskProviderImpl.submitAsync {
            BotProvider.loadBot(platform, config.getString("bot", "type"))
        }
    }

    fun loadDatabase() {
        Database.settingsFile = DatabaseSource(datasource.getNode())
        val mode = config.getString("database", "mode")
        val database = when (mode) {
            "sqlite" -> SQLite(config.getString("database", "sqlite", "path")!!.replace("%plugin_folder%", dataDirectory.pathString))
            "mysql" -> MySQL(config.getNode("database", "mysql"))
            else -> {
                log(LogLevel.ERROR, "Unknown database type! Using SQLite...")
                SQLite(config.getString("database", "sqlite", "path")!!.replace("%plugin_folder%", dataDirectory.pathString))
            }
        }
        DatabaseProvider.start(database)
    }

    /**
     * 装配/刷新渲染门户配置。
     *
     * 由本方法集中读取 `render.font` 与文本可读性规则并注入门户，
     * 替代原先各模块各自写渲染引擎全局状态的做法。
     */
    fun applyRenderConfig() {
        val fontPath = config.getString("render", "font")
            ?.replace("%plugin_folder%", dataDirectory.pathString)

        // 文本可读性规则：配置 → 门户 → AWT 引擎。
        // 读取语义（缺失/非法/0）集中在 RenderTextStyleReader，由单测覆盖；
        // 此处不得再就地解析 render.text，否则新增键仍会静默漏读。
        val textStyle = RenderTextStyleReader.read(config)

        // 主线程只做廉价配置注入：门户的 configure() 会把 TTF 读取、字体 CSS 构建、
        // 主题目录扫描与引擎重建调度到平台异步线程，不得在此处同步读取字体/扫目录。
        //
        // **已装配则复用**：bootstrap() 会先同步关闭旧门户，而 Playwright 的
        // Browser.close()/PlaywrightImpl.close() 会阻塞等待 node 驱动进程退出（实测秒级，
        // 单 tick MSPT 最高 1817ms）。reload 只需要用新配置重新 configure()，
        // 引擎重建由紧随其后的 RenderProvider.getOrNull()?.reload() 在异步线程完成。
        val portal = RenderProvider.getOrNull() ?: RenderProvider.bootstrap(platform)
        portal.configure(
            RenderConfig(
                fontFile = fontPath?.takeIf { it.isNotBlank() }?.let { File(it) },
                userAgent = HttpConstants.BROWSER_USER_AGENT,
                textStyle = textStyle
            )
        )
    }

    fun loadConfig() {
        val configFile = dataDirectory.resolve("config.yml").toFile()
        if (configFile.exists()) {
            val updated = me.regadpole.plumbot.internal.config.ConfigMigrator.completeMissingDefaults(configFile, "/config.yml")
            if (updated) {
                log(LogLevel.INFO, "检测到 config.yml 需要更新，已自动迁移/补齐默认项并写回；注释以插件内最新模板为准。")
            }
        }
        val messagesFile = dataDirectory.resolve("messages.yml").toFile()
        if (messagesFile.exists()) {
            val updated = me.regadpole.plumbot.internal.config.ConfigMigrator.completeMissingDefaults(messagesFile, "/messages.yml")
            if (updated) {
                log(LogLevel.INFO, "检测到 messages.yml 存在新增消息模板，已自动补齐默认项！")
            }
        }

        config = YamlConfigurator.createConfig(dataDirectory, "config.yml")!!
        datasource = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")!!
        val messagesConf = YamlConfigurator.createConfig(dataDirectory, "messages.yml")
        if (messagesConf == null) {
            log(LogLevel.WARN, "messages.yml 加载失败，使用默认消息配置")
            return
        }
        MessagesBinder.apply(messagesConf)
    }
}
