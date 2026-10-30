// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.avatar

import com.hypixel.hytale.server.core.NameMatching
import com.hypixel.hytale.server.core.asset.AssetModule
import com.hypixel.hytale.server.core.asset.util.AssetPackPathUtil
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule
import com.hypixel.hytale.server.core.cosmetics.PlayerSkin
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent
import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.api.platform.PlatformAvatarLayer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Hytale 原生外观 → 头像图层。
 *
 * ## 资产策略
 *
 * **全程运行时解析，绝不随插件分发**：`Server-0.6.8.pom` 声明的是
 * *Hypixel Studios Proprietary License*，把它的 cosmetic 贴图打进 PlumBot 属许可违规。
 *
 * ```
 * ① 服主覆盖  plugins/PlumBot/assets/characters/<category>/<id>.png（或同名文件）
 * ② 官方材质  AssetModule 的 asset pack（读取服务端自身文件）
 * ③ 部件默认  <category>/default.png
 * ```
 *
 * ## 调用链（javap 反编译确认）
 *
 * `Universe.getPlayerByUsername` → `PlayerRef.getComponent(PlayerSkinComponent.getComponentType())`
 * → `protocol.PlayerSkin`（20 个字符串字段）→ `PlayerSkin.PlayerSkinPartId.fromString`
 * → `CosmeticsModule.get().registry` 的对应部件表 → `PlayerSkinPart.textures[textureId]`
 * → `PlayerSkinPartTexture.texture`（路径）与 `baseColor`（染色）。
 *
 * ## 验证状态
 *
 * 本文件通过**编译期类型校验**，但**未在真实 Hytale 服务端上运行验证**：
 * ① asset pack 内部的贴图路径格式属于运行时数据，离线无法确认，故采用
 * "逐 pack 相对路径探测 + 失败明确告警"的保守策略；
 * ② `PlayerSkinPartTexture.baseColor` 的字符串格式（是否带 `#`、是否 RGB）同样只能运行时确认，
 * 已做多种格式的兼容解析。
 */
internal object HytaleAvatarResolver {

    /** 服主覆盖目录；由平台在启用时注入。 */
    @Volatile
    var overrideRoot: File? = null

    /** 日志出口；由平台注入，避免绕过平台日志体系。 */
    @Volatile
    var logSink: ((String) -> Unit)? = null

    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** 头部相关部件：`(部件名, 皮肤字段名, 读取器)`，顺序即绘制顺序（自底向上）。 */
    private val HEAD_PARTS: List<HeadPart> = listOf(
        HeadPart("face", "face") { it.face },
        HeadPart("ears", "ears") { it.ears },
        HeadPart("mouth", "mouth") { it.mouth },
        HeadPart("eyebrows", "eyebrows") { it.eyebrows },
        HeadPart("eyes", "eyes") { it.eyes },
        HeadPart("facialHair", "facialHair") { it.facialHair },
        HeadPart("haircut", "haircut") { it.haircut },
        HeadPart("headAccessory", "headAccessory") { it.headAccessory },
        HeadPart("faceAccessory", "faceAccessory") { it.faceAccessory },
        HeadPart("earAccessory", "earAccessory") { it.earAccessory }
    )

    /**
     * 解析玩家当前外观为头像图层（自底向上）。任何一步失败都返回已成功的部分，
     * 全部失败返回空列表（上层据此回退到 QQ 头像）。
     */
    fun resolveLayers(playerName: String): List<PlatformAvatarLayer> = runCatching {
        val playerRef = Universe.get().getPlayerByUsername(playerName, NameMatching.EXACT)
            ?: return emptyList()
        val skin = playerRef.getComponent(PlayerSkinComponent.getComponentType())?.playerSkin
            ?: return emptyList()
        val registry = CosmeticsModule.get()?.registry ?: return emptyList()

        val layers = ArrayList<PlatformAvatarLayer>(HEAD_PARTS.size)
        for (part in HEAD_PARTS) {
            val layer = resolvePart(part, skin, registry) ?: continue
            layers += layer
        }
        layers
    }.getOrElse { error ->
        warnOnce("__resolve__", "解析玩家 '$playerName' 外观失败：${error.message}")
        emptyList()
    }

    private fun resolvePart(
        part: HeadPart,
        skin: com.hypixel.hytale.protocol.PlayerSkin,
        registry: CosmeticRegistry
    ): PlatformAvatarLayer? {
        val raw = runCatching { part.read(skin) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null

        // 皮肤字段可能是序列化的 PlayerSkinPartId（assetId|textureId|variantId），也可能是纯 assetId
        val partId = runCatching { PlayerSkin.PlayerSkinPartId.fromString(raw) }.getOrNull()
        val assetId = partId?.assetId?.takeIf { it.isNotBlank() } ?: raw
        val textureId = partId?.textureId?.takeIf { it.isNotBlank() }

        val registryTable = registryTable(part, registry) ?: return null
        val skinPart = registryTable[assetId] ?: return null

        val texture = textureId?.let { skinPart.textures?.get(it) }
            ?: skinPart.textures?.values?.firstOrNull()
            ?: return null

        val texturePath = texture.texture?.takeIf { it.isNotBlank() } ?: return null
        val bytes = readAsset(texturePath, part) ?: run {
            warnOnce(part.name, "无法解析 Hytale 部件贴图 '$texturePath'（类别 ${part.name}）")
            return null
        }

        val color = texture.baseColor?.firstNotNullOfOrNull { normalizeColor(it) }
        return PlatformAvatarLayer(
            png = bytes,
            blend = if (color != null) "multiply" else "normal",
            color = color
        )
    }

    private fun registryTable(part: HeadPart, registry: CosmeticRegistry): Map<String, PlayerSkinPart>? =
        when (part.name) {
            "face" -> registry.faces
            "ears" -> registry.ears
            "mouth" -> registry.mouths
            "eyebrows" -> registry.eyebrows
            "eyes" -> registry.eyes
            "facialHair" -> registry.facialHairs
            "haircut" -> registry.haircuts
            "headAccessory" -> registry.headAccessories
            "faceAccessory" -> registry.faceAccessories
            "earAccessory" -> registry.earAccessories
            else -> null
        }

    /** 覆盖 → 官方材质 → 部件默认，三段回退。 */
    private fun readAsset(texturePath: String, part: HeadPart): ByteArray? {
        val fileName = runCatching { Paths.get(texturePath).fileName?.toString() }.getOrNull()

        overrideRoot?.let { root ->
            // ① 服主覆盖：完整相对路径 → 类别目录下的同名文件
            listOfNotNull(
                File(root, texturePath),
                fileName?.let { File(File(root, part.name), it) }
            ).firstOrNull { it.isFile }?.let { return runCatching { it.readBytes() }.getOrNull() }
        }

        // ② 官方材质
        officialAsset(texturePath)?.let { return it }

        // ③ 部件默认
        overrideRoot?.let { root ->
            val fallback = File(File(root, part.name), "default.png")
            if (fallback.isFile) return runCatching { fallback.readBytes() }.getOrNull()
        }
        return null
    }

    /**
     * 在服务端自身的 asset pack 中按相对路径探测。
     *
     * 不假定路径前缀：逐个 pack 用 `resolve` 探测，命中即返回；全部未命中再退回
     * `AssetPackPathUtil.resolveAssetPath`。任何异常都吞掉并返回 null（不影响机器人主流程）。
     */
    private fun officialAsset(texturePath: String): ByteArray? = runCatching {
        val module = AssetModule.get() ?: return null
        val relative = runCatching { Paths.get(texturePath) }.getOrNull() ?: return null

        val candidates = mutableListOf<Path>()
        module.assetPacks?.forEach { pack ->
            val resolved = runCatching { pack.resolve(relative) }.getOrNull()
            if (resolved != null) candidates.add(resolved)
        }
        runCatching { AssetPackPathUtil.resolveAssetPath(relative) }.getOrNull()?.let { candidates.add(it) }

        candidates.firstOrNull { Files.isRegularFile(it) }
            ?.let { path -> runCatching { Files.readAllBytes(path) }.getOrNull() }
    }.getOrNull()

    /** 兼容 `#RRGGBB` / `RRGGBB` / `0xRRGGBB` 三种写法。 */
    internal fun normalizeColor(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("0x")?.removePrefix("0X")?.removePrefix("#") ?: return null
        if (cleaned.length != 6 || cleaned.any { it.digitToIntOrNull(16) == null }) return null
        return "#${cleaned.uppercase()}"
    }

    private fun warnOnce(key: String, message: String) {
        if (!warned.add(key)) return
        val sink = logSink
        if (sink != null) sink("[PlumBot] [HytaleAvatar] $message") else System.err.println("[PlumBot] [HytaleAvatar] $message")
    }

    private class HeadPart(
        val name: String,
        val field: String,
        val read: (com.hypixel.hytale.protocol.PlayerSkin) -> String?
    )
}
