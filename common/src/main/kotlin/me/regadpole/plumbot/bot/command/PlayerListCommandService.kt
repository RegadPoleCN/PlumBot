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

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.render.AvatarBlend
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.AvatarLayer
import me.regadpole.plumbot.render.RenderPortal
import me.regadpole.plumbot.render.RenderRoute

class PlayerListCommandService(private val service: BotCommandService) {

    fun list(message: String, groupId: Long, userId: Long) {
        val players = service.context.playerService.listPlayers()

        val fallback = service.render(
            Messages.playerList,
            "%player_list%" to service.context.playerService.listPlayerString(),
            "%player_num%" to players.size.toString()
        )

        // 载荷在渲染协程内构建：头像由**服务端预取/合成**并写入磁盘缓存，
        // 浏览器只收到成品 data URL，从而让渲染耗时与外部头像服务抖动彻底解耦。
        service.sendRendered(
            groupId = groupId,
            route = RenderRoute.PLAYER_LIST,
            fallbackText = fallback,
            pic = true
        ) { portal ->
            mapOf(
                "total" to players.size,
                "players" to players.map { name ->
                    val qq = DatabaseProvider.getBindByName(name)
                    mapOf(
                        "name" to name,
                        "boundQq" to qq,
                        "avatarUrl" to resolveAvatar(portal, name, qq)
                    )
                }
            )
        }
    }

    /**
     * 头像解析顺序（五级漏斗）：
     * ① 皮肤插件 ② GameProfile 纹理 ③ 正版皮肤源 —— 以上由门户统一完成（服务端裁头 + 缓存）
     * ④ 平台原生外观图层（如 Hytale 的部件化皮肤）→ 服务端合成
     * ⑤ 绑定 QQ 头像（服务端预取并缓存）；仍不可用则返回 null，交由前端占位图
     */
    private suspend fun resolveAvatar(portal: RenderPortal, playerName: String, qq: String?): String? {
        // ①–③ 皮肤漏斗
        runCatching { portal.resolveSkinAvatar(playerName) }.getOrNull()?.let { return it }

        // ④ 平台原生外观图层
        val platformLayers = runCatching {
            service.context.playerService.resolveAvatarLayers(playerName)
        }.getOrDefault(emptyList())

        if (platformLayers.isNotEmpty()) {
            val composed = runCatching {
                portal.composeAvatar(
                    AvatarComposeRequest(
                        cacheKey = "platform:$playerName",
                        layers = platformLayers.map { layer ->
                            AvatarLayer(
                                png = layer.png,
                                blend = if (layer.blend.equals("multiply", ignoreCase = true)) {
                                    AvatarBlend.MULTIPLY
                                } else {
                                    AvatarBlend.NORMAL
                                },
                                color = layer.color
                            )
                        }
                    )
                )
            }.getOrNull()
            if (composed != null) return composed
        }

        // ⑤ 绑定 QQ 头像
        return qq?.let { buildQqAvatarUrl(portal, it) }
    }

    /** 优先返回预取后的 data URL；预取失败则回退为原始链接（前端仍可自行加载）。 */
    private suspend fun buildQqAvatarUrl(portal: RenderPortal, qq: String): String {
        val url = "https://q1.qlogo.cn/g?b=qq&nk=$qq&s=640"
        return runCatching { portal.fetchAvatarAsDataUrl("qq:$qq", url) }.getOrNull() ?: url
    }
}
