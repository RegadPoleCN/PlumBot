// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot

import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.testing.FakePlatformContext
import net.kyori.adventure.text.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 配置加载的可读失败路径：畸形 YAML 与缺失的 sqlite path 都必须给出可定位的错误，
 * 而不是裸抛 IOException 或让空指针砸掉启动。
 */
class PlumBotConfigLoadTest {

    private lateinit var tempDir: Path
    private lateinit var bot: FakePlumBot

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("plumbot-config-load-test")
        bot = FakePlumBot(tempDir)
    }

    @AfterTest
    fun cleanup() {
        runCatching { DatabaseProvider.shutdown() }
        runCatching {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `a malformed config file fails startup with a readable message`() {
        tempDir.resolve("config.yml").toFile().writeText("key: [unclosed")

        val ex = assertFailsWith<IllegalStateException> { bot.loadConfig() }
        assertTrue(ex.message!!.contains("config.yml"), "异常信息应指明出错的文件：" + ex.message)
        assertTrue(
            bot.logs.any { it.contains("config.yml") && (it.contains("解析失败") || it.contains("无法")) },
            "应有一条带原因的 ERROR 日志，实际日志：" + bot.logs
        )
    }

    @Test
    fun `missing sqlite path falls back to the data directory`() {
        bot.datasource = YamlConfigurator.createConfig(tempDir, "datasource.yml")!!
        // 只写 mode 不写 path：文件已存在，createConfig 不会触发迁移补齐，path 保持缺失。
        tempDir.resolve("config.yml").toFile().writeText("database:")
        tempDir.resolve("config.yml").toFile().appendText("\n    mode: \"sqlite\"")
        bot.config = YamlConfigurator.createConfig(tempDir, "config.yml")!!

        bot.loadDatabase()

        assertNotNull(DatabaseProvider.getDatabase(), "回落路径也应完成数据库初始化")
        assertTrue(Files.exists(tempDir.resolve("plumbot.db")), "应在数据目录创建默认数据库文件 plumbot.db")
        assertTrue(bot.logs.any { it.contains("plumbot.db") }, "应有使用默认数据库文件的 WARN 日志：" + bot.logs)
    }
}

/** PlumBot 的最小替身：只收集日志，其余成员按接口要求空实现。 */
private class FakePlumBot(private val dir: Path) : PlumBot {
    override lateinit var datasource: YamlConfigurator
    override lateinit var config: YamlConfigurator
    override var dataDirectory: Path = dir
    override var debugProvider: DebugProvider = DebugProvider(this)
    override val platform: PlatformContext = FakePlatformContext()

    val logs = mutableListOf<String>()

    override fun log(level: LogLevel, log: String) {
        logs += "[" + level + "] " + log
    }

    override fun sendMessage(message: Component) {}
    override fun kickPlayer(name: String) {}
    override fun listPlayers(): List<String> = emptyList()
    override fun listPlayerString(): String = ""
    override fun loadDependencies() {}
}