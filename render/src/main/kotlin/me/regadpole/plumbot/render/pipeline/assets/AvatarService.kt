// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.assets

import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.DEFAULT_RENDER_USER_AGENT
import me.regadpole.plumbot.render.avatar.AvatarCache
import me.regadpole.plumbot.render.avatar.AvatarComposer
import me.regadpole.plumbot.render.avatar.AvatarFetcher
import me.regadpole.plumbot.render.avatar.AvatarResolver
import me.regadpole.plumbot.render.support.RenderLimits
import me.regadpole.plumbot.render.support.RenderSecurity
import me.regadpole.plumbot.render.support.SafeHttp

/**
 * 头像装配：抓取 / 皮肤漏斗解析 / 合成 / 磁盘缓存。
 *
 * 三个组件都是**首次使用时按当时的配置**构造，并在 [invalidate] 时整体丢弃：
 * 配置重载后必须重建，否则缓存目录、尺寸、白名单、UA 仍是旧值。
 */
internal class AvatarService(
    private val context: PlatformContext,
    private val runtimeWebDir: File,
    private val lock: Any,
    private val userAgentOf: () -> String?
) {

    @Volatile
    private var avatarCacheRef: AvatarCache? = null

    @Volatile
    private var avatarFetcherRef: AvatarFetcher? = null

    /**
     * 皮肤漏斗解析器。
     *
     * **必须跨调用复用**：它持有的 [me.regadpole.plumbot.render.pipeline.FailureCooldown] 是有状态
     * 的熔断表，每次调用新建的话冷却表会随对象一起被回收，熔断形同虚设。
     */
    @Volatile
    private var avatarResolverRef: AvatarResolver? = null

    /** 丢弃全部头像组件（配置重载 / 引擎重建 / 停服时调用）。 */
    fun invalidate() {
        synchronized(lock) {
            avatarCacheRef = null
            avatarFetcherRef = null
            avatarResolverRef = null
        }
    }

    /** 门户入口：按 `https` + 白名单校验后抓取，失败返回 null（永不抛异常）。 */
    suspend fun fetchAsDataUrl(cacheKey: String, url: String): String? =
        avatarFetcher().fetchAsDataUrl(cacheKey, url)

    /** 三级皮肤漏斗：插件 → 档案 → 正版 UUID；命中的 PNG 转成 data URL。 */
    suspend fun resolveSkinAvatar(playerName: String): String? = withContext(Dispatchers.IO) {
        when (val resolution = avatarResolver().resolve(playerName)) {
            is AvatarResolver.Resolution.Found -> toDataUrl(resolution.png)
            AvatarResolver.Resolution.Miss -> null
        }
    }

    /** 合成头像：命中磁盘缓存直接返回，否则合成后写回缓存。 */
    suspend fun composeAvatar(request: AvatarComposeRequest): String? = withContext(Dispatchers.IO) {
        val cache = avatarCache()
        // 缓存键必须带**策略版本 + 尺寸**：合成结果依赖尺寸等配置，
        // 只按调用方给的 key 存盘会让配置变化后继续命中旧图（"改了 head_size 没生效"）。
        // 合成逻辑本身变化时提升 RenderLimits.AVATAR_CACHE_POLICY_VERSION 即可作废旧图。
        val key = request.cacheKey
            ?.takeIf { it.isNotBlank() }
            ?.let { "v${RenderLimits.AVATAR_CACHE_POLICY_VERSION}:h${avatarHeadSize()}:$it" }
        if (key != null) {
            cache.read(key)?.let { return@withContext toDataUrl(it) }
        }
        val composed = runCatching { AvatarComposer.compose(request) }.getOrNull()
            ?: return@withContext null
        if (key != null) cache.write(key, composed)
        toDataUrl(composed)
    }

    private fun avatarHeadSize(): Int =
        context.config.getInteger("render", "avatar", "head_size")
            .takeIf { it in 16..512 }
            ?: AvatarResolver.DEFAULT_HEAD_SIZE

    /** data URL 前缀按**实际内容**给出（与嗅探保持一致，避免声明 MIME 与内容不符）。 */
    private fun toDataUrl(bytes: ByteArray): String =
        "data:" + (SafeHttp.mimeOf(bytes) ?: "image/png") + ";base64," +
            Base64.getEncoder().encodeToString(bytes)

    private fun avatarFetcher(): AvatarFetcher = avatarFetcherRef ?: synchronized(lock) {
        avatarFetcherRef ?: buildAvatarFetcher().also { avatarFetcherRef = it }
    }

    private fun buildAvatarFetcher(): AvatarFetcher {
        val config = context.config
        return AvatarFetcher(
            context = context,
            cache = avatarCache(),
            allowedHosts = RenderSecurity.allowedHostsFrom(config),
            maxBytes = RenderLimits.limit(config, "per_layer_max", RenderLimits.DEFAULT_PER_LAYER_MAX),
            userAgent = userAgentOf() ?: DEFAULT_RENDER_USER_AGENT
        )
    }

    /**
     * 复用的漏斗解析器（见 [avatarResolverRef] 的说明）。
     *
     * 注意：`head_size` 与冷却时长在构造时读取，因此**改这些配置需要 `reload()`**；
     * `reload()` 同时会重置熔断状态（配置变更后允许重试）。
     */
    private fun avatarResolver(): AvatarResolver = avatarResolverRef ?: synchronized(lock) {
        avatarResolverRef ?: AvatarResolver(context, avatarFetcher(), avatarHeadSize())
            .also { avatarResolverRef = it }
    }

    private fun avatarCache(): AvatarCache = avatarCacheRef ?: synchronized(lock) {
        avatarCacheRef ?: buildAvatarCache().also { avatarCacheRef = it }
    }

    private fun buildAvatarCache(): AvatarCache {
        val config = context.config
        val pluginFolder = context.dataDirectory.toFile().absolutePath
        val cacheDir = config.getString("render", "avatar", "cache_dir")
            ?.takeIf { it.isNotBlank() }
            ?.replace("%plugin_folder%", pluginFolder)
            ?.let { File(it) }
            ?: File(runtimeWebDir.parentFile ?: runtimeWebDir, "cache/avatars")
        val ttlHours = config.getInteger("render", "avatar", "cache_ttl_hours")
            .let { if (it <= 0) RenderLimits.DEFAULT_AVATAR_TTL_HOURS else it }
        return AvatarCache(cacheDir, ttlHours * 60L * 60L * 1000L)
    }
}
