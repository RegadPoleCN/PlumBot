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

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import java.util.Base64

/**
 * Minecraft 皮肤头像的**五级漏斗**中前三级的调度器。
 *
 * ```
 * ① 皮肤插件（SkinsRestorer 等，平台反射取得纹理 URL）
 * ② 内存 GameProfile 纹理（正版登录 / 插件注入）
 * ③ 正版皮肤源（sessionserver.mojang.com → textures.minecraft.net）
 * ④ 绑定 QQ 头像  ← 由调用方处理
 * ⑤ 占位图       ← 由前端组件降级
 * ```
 *
 * 每一级有**独立超时**（[LEVEL_TIMEOUT_MS]），整条漏斗另有**总预算**（[TOTAL_BUDGET_MS]）：
 * 某一级卡住只拖慢该级，任何一级失败都静默进入下一级；头像解析与卡片渲染是两套独立预算。
 *
 * 产物是**已裁切的头部 PNG**（裁切在服务端完成），缓存按**来源**分键
 * （`skin:<source>:<玩家名>`），换源后不会脏读到上一级的皮肤。
 */
internal class AvatarResolver(
    private val context: PlatformContext,
    private val fetcher: AvatarFetcher,
    private val headSize: Int = DEFAULT_HEAD_SIZE
) {

    /** 皮肤来源（同时用作缓存键前缀，保证换源不脏读）。 */
    enum class Source(val key: String) {
        PLUGIN("plugin"),
        PROFILE("profile"),
        PREMIUM("premium")
    }

    /** 整条漏斗全灭后的冷却（避免列表里每个无皮肤玩家每次都跑满 3 秒预算）。 */
    private val cooldown = FailureCooldown(cooldownMillis())

    sealed interface Resolution {
        class Found(val png: ByteArray, val source: Source) : Resolution
        data object Miss : Resolution
    }

    suspend fun resolve(playerName: String): Resolution {
        if (playerName.isBlank()) return Resolution.Miss
        // 熔断：冷却期内直接降级，不再发起任何外部请求
        if (cooldown.isCooling(playerName)) return Resolution.Miss

        val found = withTimeoutOrNull(totalBudgetMs()) {
            fromPlugin(playerName)
                ?: fromProfile(playerName)
                ?: fromPremium(playerName)
        }

        if (found == null) {
            cooldown.recordFailure(playerName)
            return Resolution.Miss
        }
        cooldown.clear(playerName)
        return found
    }

    /**
     * 冷却时长（毫秒），配置值按分钟解释：`> 0` 生效，`< 0` **禁用熔断**（返回 0），
     * `0` / 键缺失取默认 10 分钟。
     *
     * 不用「0 = 禁用」是因为 `PlatformConfig.getInteger` 分不清"键缺失"与"显式 0"，
     * 会让未迁移的配置**静默关闭**这层保护；负数则"可禁用"且"缺失时 fail-closed"。
     */
    private fun cooldownMillis(): Long {
        val minutes = context.config.getInteger("render", "avatar", "failure_cooldown_minutes")
        return when {
            minutes > 0 -> minutes * 60_000L
            minutes < 0 -> 0L
            else -> DEFAULT_COOLDOWN_MS
        }
    }

    private fun levelTimeoutMs(): Long =
        context.config.getLong("render", "avatar", "level_timeout_ms")
            .takeIf { it > 0 } ?: LEVEL_TIMEOUT_MS

    private fun totalBudgetMs(): Long =
        context.config.getLong("render", "avatar", "total_budget_ms")
            .takeIf { it > 0 } ?: TOTAL_BUDGET_MS

    /** ① 皮肤插件（反射取得纹理属性）。 */
    private suspend fun fromPlugin(playerName: String): Resolution.Found? = level(Source.PLUGIN, playerName) {
        context.playerService.skinTextureFromPlugin(playerName)
    }

    /** ② 内存 GameProfile 纹理。 */
    private suspend fun fromProfile(playerName: String): Resolution.Found? = level(Source.PROFILE, playerName) {
        context.playerService.skinTextureFromProfile(playerName)
    }

    /**
     * ③ 正版皮肤源：UUID → 会话服务，返回的同样是 base64 纹理属性，
     * 因此三级来源的产物类型一致，`base64 → JSON → SKIN.url` 的解析只有一处。
     */
    private suspend fun fromPremium(playerName: String): Resolution.Found? = level(Source.PREMIUM, playerName) {
        val uuid = context.playerService.premiumUuid(playerName)
            ?.replace("-", "")
            ?.takeIf { it.isNotBlank() }
            ?: return@level null

        val body = fetcher.fetchBytes(null, "${sessionServer()}/$uuid")?.decodeToString()
            ?: return@level null
        val profile = runCatching { JsonParser.parse(body) }.getOrNull() ?: return@level null
        JsonParser.path(profile, "properties", 0, "value") as? String
    }

    /** 会话服务地址可配（国内镜像 / 自建反代）。 */
    private fun sessionServer(): String =
        context.config.getString("render", "avatar", "session_server")
            ?.trim()
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: DEFAULT_SESSION_SERVER

    /**
     * 执行一级：取纹理属性 → 解析出 SKIN.url → 抓取 → 裁头；[propertyProvider] 返回 base64
     * 纹理属性（三级来源统一形态）。
     */
    private suspend fun level(
        source: Source,
        playerName: String,
        propertyProvider: suspend () -> String?
    ): Resolution.Found? {
        val cacheKey = "skin:${source.key}:$playerName"
        fetcher.readCached(cacheKey)?.let { return Resolution.Found(it, source) }

        val png = withTimeoutOrNull(levelTimeoutMs()) {
            runCatching {
                val property = propertyProvider() ?: return@runCatching null
                val url = companionSkinUrl(property) ?: return@runCatching null
                val raw = fetcher.fetchBytes(null, url) ?: return@runCatching null
                MinecraftSkinCropper.cropHead(raw, headSize)
            }.getOrNull()
        }

        if (png == null) {
            context.log(
                LogLevel.DEBUG,
                "[PlumBot-Render] 头像漏斗 ${source.key} 级未命中（玩家 $playerName）"
            )
            return null
        }
        fetcher.writeCache(cacheKey, png)
        return Resolution.Found(png, source)
    }

    companion object {
        /** 单级超时：某一级卡住不应拖垮整条漏斗。 */
        const val LEVEL_TIMEOUT_MS = 800L

        /** 整条漏斗的总预算。 */
        const val TOTAL_BUDGET_MS = 3_000L

        private const val DEFAULT_SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/profile"

        const val DEFAULT_HEAD_SIZE = 128

        /** 漏斗全灭后的默认冷却（10 分钟）。 */
        const val DEFAULT_COOLDOWN_MS = 10 * 60_000L

        /** base64 纹理属性 → 皮肤 URL（纯函数，便于单测）。 */
        internal fun companionSkinUrl(property: String): String? {
            val decoded = runCatching {
                Base64.getDecoder().decode(property.trim()).decodeToString()
            }.getOrNull() ?: return null
            val root = runCatching { JsonParser.parse(decoded) }.getOrNull() ?: return null
            val url = JsonParser.path(root, "textures", "SKIN", "url") as? String ?: return null
            return normalizeTextureUrl(url)
        }

        /**
         * 归一化纹理地址：Mojang 返回的 `textures.SKIN.url` 是 **http**
         * （`http://textures.minecraft.net/texture/<hash>`），而抓取器只允许 https，
         * 故只把该域名的 http 升为 https，其余非 https 一律拒绝。
         */
        internal fun normalizeTextureUrl(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.startsWith("https://", ignoreCase = true)) return trimmed
            if (trimmed.startsWith("http://", ignoreCase = true)) {
                val host = trimmed.removePrefix("http://").substringBefore('/').substringBefore(':')
                if (host.equals("textures.minecraft.net", ignoreCase = true)) {
                    return "https://" + trimmed.removePrefix("http://")
                }
            }
            return null
        }
    }
}
