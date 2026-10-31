// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.support

import me.regadpole.plumbot.api.platform.PlatformConfig

/**
 * 渲染管线的**常量真源**。
 *
 * 这些取值散落在门户各处时，"改一个默认值要跟着改三处"是常态；集中到一处后，
 * 容量、超时、载荷上限、头像缓存策略等都只有**一份**定义，任何一处引用都不会漂移。
 *
 * 只放**与具体实例无关**的常量与纯函数：引擎池大小（`PAGE_POOL_SIZE`）属于引擎模块，
 * 不在此列。
 */
internal object RenderLimits {

    // ---------------------------------------------------------------- 引擎取值

    const val ENGINE_NONE = "none"
    const val ENGINE_AWT = "awt"
    const val ENGINE_PLAYWRIGHT = "playwright"

    /** `auto`：未显式选择引擎，交给 `RenderRoute.preferredEngine`（缺省 awt）。 */
    const val ENGINE_AUTO = "auto"

    /** 合法引擎取值；不在其中的一律 WARN 并回落 AWT。 */
    val KNOWN_ENGINES = setOf(ENGINE_AUTO, ENGINE_NONE, ENGINE_AWT, ENGINE_PLAYWRIGHT)

    const val PLAYWRIGHT_CLASS = "com.microsoft.playwright.Playwright"

    // ---------------------------------------------------------------- 容量与超时

    /**
     * 并发渲染数。
     *
     * Playwright 的实际并发是 1：页面池只放 1 个页面（前台页），后台标签页不产合成帧，
     * `page.screenshot` 只会等到超时，多页并发还会互抢前台。保留 2 是为了 AWT 兜底
     * 仍可并行，同时让多出来的请求在信号量上排队而不是直接降级。
     */
    const val CONCURRENCY = 2

    /** 允许排队的深度；超出立即降级纯文本，**不排队、不丢弃**。 */
    const val QUEUE_DEPTH = 6

    /** 同一目标锁表的水位：超过后清理空闲条目，防止大量目标键长期驻留。 */
    const val MAX_TRACKED_TARGETS = 512

    const val DEFAULT_TIMEOUT_MS = 3500L

    /** 停服/重载时等待引擎拆除完成的默认上限（毫秒）。 */
    const val DEFAULT_TEARDOWN_TIMEOUT_MS = 3_000L

    // ---------------------------------------------------------------- 装载与提示

    /** Playwright 装载失败的冷却时长：避免离线环境中每条消息都触发下载尝试。 */
    const val PLAYWRIGHT_LOAD_COOLDOWN_MS = 60_000L

    /** 同一引擎+路由的降级 ERROR 去重窗口。 */
    const val DEGRADATION_LOG_INTERVAL_MS = 5 * 60 * 1000L

    // ---------------------------------------------------------------- 载荷治理

    /** 卡片正文上限（字符）：超限不出图，避免巨量文本塞进无头浏览器后超时降级。 */
    const val DEFAULT_CARD_TEXT_MAX = 8000

    /** 展示文本单字段上限；后续由协议清单按 route 细分覆盖。 */
    const val DEFAULT_DISPLAY_TEXT_MAX = 1024

    /** 单个图层（data URL）字节上限。 */
    const val DEFAULT_PER_LAYER_MAX = 256 * 1024

    /** 二进制总量硬上限。 */
    const val DEFAULT_BINARY_TOTAL_MAX = 1024 * 1024

    /** 图层数上限（头部相关部件约 10 层，留足余量）。 */
    const val DEFAULT_LAYERS_MAX = 16

    /** 文本字符总量告警阈值。 */
    const val DEFAULT_TOTAL_TEXT_WARN = 256 * 1024

    // ---------------------------------------------------------------- 头像与字体

    /** 头像磁盘缓存默认有效期（小时）。 */
    const val DEFAULT_AVATAR_TTL_HOURS = 168

    /** 头像合成缓存键的策略版本：合成逻辑或键结构变化时 +1，使旧图自动失效。 */
    const val AVATAR_CACHE_POLICY_VERSION = 1

    /** 注入页面的字体体积上限（默认 16MB）：防止把超大字体塞进每一页。 */
    const val DEFAULT_FONT_MAX_BYTES = 16 * 1024 * 1024

    /**
     * 读取 `render.limits.<key>`，非正值回落到内置默认值。
     *
     * 配置里写 0 / 负数一律视为"没配"：这些上限的语义都是"不能超过 N"，
     * 0 会让每一次渲染都被治理掉，属于误配而不是"关闭治理"。
     */
    fun limit(config: PlatformConfig, key: String, fallback: Int): Int {
        val value = config.getInteger("render", "limits", key)
        return if (value <= 0) fallback else value
    }
}
