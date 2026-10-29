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

package me.regadpole.plumbot.render

import java.util.concurrent.atomic.AtomicInteger

/**
 * 协议清单里 payload.data 字段的**运行时**自检。
 *
 * 为什么需要它：协议一致性原本由 Gradle 的 checkRenderProtocol 在前端源码被本地检出时
 * 逐字段比对，但那要求插件仓能读到 web/ 目录 —— 前端迁出为独立仓库后这条构建期通路
 * 即被删除。能保持的只有「发布版本号 + 运行时 manifest 握手」：
 * 协议号与 route 集合不匹配会被 verifyManifest 判为 PW_TEMPLATE_INVALID，
 * **而字段级漂移不会** —— 插件多送一个键、模板少读一个键，浏览器只会静默渲染空白。
 * 本对象把这一类漂移变成一条可诊断的 WARN，并计入 doctor 的计数。
 *
 * 约束：
 * - :render 不引入 gson（见 [JsonEncoder] 的说明），因此复用 [JsonParser] 读自身资源；
 * - 清单随 jar 分发（render/src/main/resources/render-protocol.json），无网络与文件系统依赖；
 * - 声明为空的 data: {}（load/unload/text）表示**无约束**，不比对；
 * - 类型以 `?` 结尾的字段是**可选**字段：载荷缺它不算漂移（例如 whitelist 的
 *   bindTime/avatarUrl、console 的 code/durationMs、hytaleAvatar 的 fallbackQq），
 *   但载荷出现协议里根本没有的键仍算漂移；
 * - 只校验 [RenderCard]：通用文本走 [TextPayload]，与模板字段无关。
 */
internal object RenderProtocolRules {

    private const val RESOURCE = "/render-protocol.json"
    private const val LOG_PREFIX = "[PlumBot-Render] 渲染协议字段漂移："

    private val mismatches = AtomicInteger(0)
    private val reported = HashSet<String>()

    @Volatile
    private var loadFailure: String? = null

    /** 单个 route 的字段声明：`required` 必填、`all` 含可选（用于识别多余键）。 */
    private class Declared(val required: Set<String>, val all: Set<String>)

    /** route → 协议声明的字段（空集合 = 该 route 不声明字段）。 */
    private val declared: Map<String, Declared> by lazy { loadDeclared() }

    /** 清单里声明的 route 数量；资源缺失或解析失败时为 0。 */
    val routeCount: Int
        get() = declared.size

    /** 已上报的（去重后的）漂移数量，供 /plumbot render doctor 展示。 */
    fun mismatchCount(): Int = mismatches.get()

    /**
     * 计算本次载荷与协议声明的差异；一致或无需校验时返回 null。
     *
     * 纯函数，不产生日志、不改计数器 —— 便于单测直接断言。
     */
    fun diff(route: String, payload: Any?): String? {
        if (payload !is RenderCard) return null
        val expected = declared[route] ?: return null
        if (expected.all.isEmpty()) return null

        val actual = payload.data.keys
        val missing = expected.required - actual
        val extra = actual - expected.all
        if (missing.isEmpty() && extra.isEmpty()) return null

        val parts = buildList {
            if (missing.isNotEmpty()) add("协议声明但载荷缺失 " + missing.sorted())
            if (extra.isNotEmpty()) add("载荷多出协议未声明 " + extra.sorted())
        }
        return "route '" + route + "' " + parts.joinToString("；")
    }

    /**
     * 上报一次漂移：同一 route 的同一差异只记一次（避免高频 route 刷屏）。
     *
     * @return 本次是否为新差异（true 表示已调用 [log]）
     */
    fun report(route: String, diff: String, log: (String) -> Unit): Boolean {
        val signature = route + "|" + diff
        val first = synchronized(reported) { reported.add(signature) }
        if (!first) return false
        mismatches.incrementAndGet()
        log(LOG_PREFIX + diff)
        return true
    }

    /** doctor 展示用的一行摘要。 */
    fun describe(): String {
        val failure = loadFailure
        return if (failure != null) {
            "protocol fields: 不可用（" + failure + "）"
        } else {
            "protocol fields: " + routeCount + " routes, mismatches: " + mismatchCount()
        }
    }

    /** 单测用：清空去重表与计数（清单缓存保留）。 */
    fun resetForTest() {
        synchronized(reported) { reported.clear() }
        mismatches.set(0)
    }

    private fun loadDeclared(): Map<String, Declared> {
        val text = runCatching {
            RenderProtocolRules::class.java.getResourceAsStream(RESOURCE)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text == null) {
            loadFailure = "缺少资源 " + RESOURCE
            return emptyMap()
        }

        val root = runCatching { JsonParser.parse(text) }.getOrElse { error ->
            loadFailure = "解析失败：" + (error.message ?: error.javaClass.simpleName)
            return emptyMap()
        }

        val routes = JsonParser.path(root, "routes") as? Map<*, *> ?: run {
            loadFailure = "缺少 routes 节点"
            return emptyMap()
        }

        return routes.mapNotNull { (rawRoute, rawEntry) ->
            val route = rawRoute as? String ?: return@mapNotNull null
            val data = JsonParser.path(rawEntry, "payload", "data") as? Map<*, *>
                ?: return@mapNotNull null
            val required = LinkedHashSet<String>()
            val all = LinkedHashSet<String>()
            data.forEach { (rawKey, rawType) ->
                val key = rawKey as? String ?: return@forEach
                all += key
                // 类型以 ? 结尾表示值可为 null（见清单里的 "string?"），属于可选字段。
                if (!rawType.toString().trim().endsWith("?")) required += key
            }
            route to Declared(required, all)
        }.toMap()
    }
}
