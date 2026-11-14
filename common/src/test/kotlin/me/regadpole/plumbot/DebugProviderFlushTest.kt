// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot

import me.regadpole.plumbot.config.YamlConfigurator
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 批量落盘的失败恢复契约：写盘异常时批次必须按原序放回队列，下一个周期重试成功；
 * 积压超上限时丢最旧日志，内存不无限膨胀。
 */
class DebugProviderFlushTest {

    /** PlumBot 接口太宽：config 走真实 YamlConfigurator（读 save_interval），其余成员返回默认值。 */
    private fun stubPlugin(debugYaml: String? = null): PlumBot {
        val config: YamlConfigurator? = debugYaml?.let { yaml ->
            val node = YamlConfigurationLoader.builder()
                .source { java.io.StringReader(yaml).buffered() }
                .build()
                .load()
            YamlConfigurator(node)
        }
        return Proxy.newProxyInstance(
            PlumBot::class.java.classLoader,
            arrayOf<Class<*>>(PlumBot::class.java)
        ) { _, method, _ ->
            when {
                method.name == "getConfig" && config != null -> config
                method.returnType == java.lang.Long.TYPE -> 0L
                method.returnType == java.lang.Boolean.TYPE -> false
                method.returnType == java.lang.Integer.TYPE -> 0
                else -> null
            }
        } as PlumBot
    }

    private fun setField(provider: DebugProvider, name: String, value: Any?) {
        val field = DebugProvider::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(provider, value)
    }

    private fun invokeFlush(provider: DebugProvider) {
        val method = DebugProvider::class.java.getDeclaredMethod("flushLogsToDisk")
        method.isAccessible = true
        method.invoke(provider)
    }

    @Test
    fun `a failed disk write puts the batch back in order for the next flush`() {
        val dir = Files.createTempDirectory("plumbot-debug-flush-test")
        try {
            val provider = DebugProvider(stubPlugin())
            // initialized + 目标文件先指向一个目录：appendText 必然失败
            setField(provider, "initialized", true)
            setField(provider, "loggerFile", dir.toFile())

            val queueField = DebugProvider::class.java.getDeclaredField("loggerList")
            queueField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val queue = queueField.get(provider) as java.util.concurrent.ConcurrentLinkedDeque<String>
            queue.addLast("[2026-01-01 00:00:01] first\n")
            queue.addLast("[2026-01-01 00:00:02] second\n")

            invokeFlush(provider)   // 写失败：批次必须原样放回
            assertTrue(queue.size == 2, "写失败后批次不得丢失，实际剩 " + queue.size + " 条")

            // 恢复：改指向真实文件，下一周期落盘成功且顺序正确
            val realFile = dir.resolve("debug.log").toFile()
            setField(provider, "loggerFile", realFile)
            invokeFlush(provider)

            assertTrue(queue.isEmpty(), "重试成功后队列清空")
            val content = realFile.readText()
            assertTrue(content.contains("first") && content.contains("second"), "重试后整批落盘")
            assertTrue(content.indexOf("first") < content.indexOf("second"), "落盘顺序与入队一致")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the backlog is capped so a long outage cannot grow the queue unbounded`() {
        val dir = Files.createTempDirectory("plumbot-debug-cap-test")
        try {
            // debug.save_interval=500 → 走批量缓冲分支
            val provider = DebugProvider(stubPlugin(debugYaml = "debug:\n  save_interval: 500\n"))
            setField(provider, "initialized", true)
            setField(provider, "loggerFile", dir.resolve("debug.log").toFile())

            repeat(50_003) { provider.log("entry") }

            val queueField = DebugProvider::class.java.getDeclaredField("loggerList")
            queueField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val queue = queueField.get(provider) as java.util.concurrent.ConcurrentLinkedDeque<String>
            assertTrue(queue.size == 50_000, "积压应封顶在 50000，实际 " + queue.size)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
