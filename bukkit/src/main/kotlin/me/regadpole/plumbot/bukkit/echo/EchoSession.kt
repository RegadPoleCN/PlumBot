// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.utils.ComponentProjector
import net.kyori.adventure.text.Component

/**
 * 一次远程执行的捕获缓冲。
 *
 * 线程安全：日志 appender / handler 与主线程 proxy sender 可能并发写入。
 * 投影延迟：写入方只入队原始对象，序列化/去重/截断统一在 [snapshot]（finalizer 线程）完成。
 * 去重语义：只抑制同一行内容来自不同来源的重复；同一来源内部的重复必须保留（真实回显可能重复）。
 */
internal class EchoSession(
    val startMillis: Long,
    val includeLogPrefix: Boolean,
    private val maxLines: Int = 5000,
    private val maxChars: Int = 100_000,
    private val maxPending: Int = 20_000
) {
    /**
     * 待投影条目。
     *
     * 写入方一律是主线程，而投影（MiniMessage/Legacy 序列化、en_us 首次解析、逐行去重）很贵：
     * spark 实测一次冷派发主线程 72ms，其中 48ms 落在 ComponentProjector/Language 上。
     * 所以只入队原始对象，投影推迟到 [snapshot]（finalizer 线程，不在 tick 内）。
     */
    private sealed interface Pending {
        val source: String

        class Log(override val source: String, val raw: String) : Pending
        class Text(override val source: String, val raw: String) : Pending
        class ComponentText(override val source: String, val component: Component) : Pending
    }

    private val lock = Any()
    private val pending = ArrayList<Pending>()
    private val lines = ArrayList<String>()
    private val firstSourceByKey = HashMap<String, String>()
    private val sourcesSeen = LinkedHashSet<String>()
    private var chars = 0

    @Volatile
    private var closed = false

    val isClosed: Boolean get() = closed

    /**
     * 仅供测试：已投影完成的行数，不触发投影。用来钉死 [component] 的语义——投影必须发生在
     * [snapshot] 里，否则主线程派发时又要承担序列化与 en_us 首次解析（实测 48ms）。
     */
    internal fun projectedLineCountForTest(): Int = synchronized(lock) { lines.size }

    /** 日志通道入口：只入队，剥时间戳/前缀与审计过滤都推迟到投影阶段。 */
    fun logLine(source: String, raw: String) {
        enqueue(Pending.Log(source, raw))
    }

    /** 发送器通道入口：字符串已经是用户可见文本，不做日志前缀剥离。 */
    fun line(source: String, raw: String) {
        enqueue(Pending.Text(source, raw))
    }

    fun component(source: String, component: Component) {
        enqueue(Pending.ComponentText(source, component))
    }

    /** 结果快照，会执行投影（O(待投影条目 × 行数)），只允许在 finalizer 线程调用。 */
    fun snapshot(): List<String> {
        drain()
        return synchronized(lock) { ArrayList(lines) }
    }

    /** 按首次出现的顺序返回实际贡献过回显的来源；用于把 console 通道细化成 log4j2/jul/latest_log。 */
    fun sources(): List<String> {
        drain()
        return synchronized(lock) { ArrayList(sourcesSeen) }
    }

    fun close() {
        // 先投影再置关闭：finalizeLocal 按 finish -> close -> snapshot，关闭检查若先于投影会静默丢弃整段回显。
        drain()
        closed = true
    }

    private fun enqueue(item: Pending) {
        if (closed) return
        synchronized(lock) {
            if (closed) return
            if (pending.size >= maxPending) return
            pending += item
        }
    }

    /** 把待投影队列一次性转成结果行；可重复调用（队列已空时立即返回）。 */
    private fun drain() {
        val batch = synchronized(lock) {
            if (pending.isEmpty()) return
            val copy = ArrayList(pending)
            pending.clear()
            copy
        }
        batch.forEach { item ->
            when (item) {
                is Pending.Log -> EchoLogLine.normalize(item.raw, includeLogPrefix)?.let { add(item.source, it) }
                is Pending.Text -> projectText(item.source, item.raw)
                is Pending.ComponentText ->
                    projectText(item.source, ComponentProjector.project(item.component))
            }
        }
    }

    /** 发送器/组件通道的文本化 + 分行 + 审计过滤。 */
    private fun projectText(source: String, raw: String) {
        val converted = ComponentProjector.ansiToSection(raw).replace("\r", "")
        converted.split('\n').forEach { part ->
            val text = part.trim()
            if (text.isNotEmpty() && !EchoLogLine.containsAudit(text)) {
                add(source, text)
            }
        }
    }

    private fun add(source: String, text: String) {
        synchronized(lock) {
            if (closed) return
            val key = ComponentProjector.stripSectionCodes(text).trim()
            if (key.isEmpty()) return
            val firstSource = firstSourceByKey[key]
            if (firstSource != null && firstSource != source) return
            if (firstSource == null) {
                firstSourceByKey[key] = source
                sourcesSeen += source
            }
            if (lines.size >= maxLines) return
            if (chars + text.length > maxChars) return
            lines += text
            chars += text.length
        }
    }
}
