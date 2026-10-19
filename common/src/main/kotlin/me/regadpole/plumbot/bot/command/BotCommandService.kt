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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.bot.command.whitelist.BindPolicy
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.AvatarBlend
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.AvatarLayer
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderPortal
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute

class BotCommandService(val context: PlatformContext, val bot: IBot) {
    /**
     * 配置读取入口。
     *
     * **必须是 getter，不能缓存成 val**：`/plumbot reload` 会替换 `context.config`，
     * 缓存旧引用会让所有门控、别名与文案继续读"上一份"配置——
     * 表现为"reload 成功但配置不生效"，而且不报任何错。
     */
    val config: PlatformConfig get() = context.config

    /** 出图协程作用域（渲染是挂起能力，不能阻塞调用线程）。 */
    private val renderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun shutdown() {
        renderScope.cancel()
    }

    fun isFeatureEnabled(feature: String): Boolean = config.getBoolean("feature", feature, "enable")

    /** 绑定写入的唯一决策点（三条写入路径共用，避免各自复制判断）。 */
    val bindPolicy = BindPolicy(this)

    fun isAdmin(userId: Long): Boolean = config.getLongList("admins").contains(userId)

    /**
     * 头像指令是否出图（`render.outputs.avatar`）。
     *
     * 门户对 `hytaleAvatar` 路由也会判同一开关；这里提前判断是为了在关闭时直接回纯文本，
     * 省去皮肤抓取与图层合成。
     */
    private fun avatarPicEnabled(): Boolean = config.getBoolean("render", "outputs", "avatar")

    /** 已提示过"适配器不支持成员校验"（去重，避免每条指令刷屏）。 */
    @Volatile
    private var warnedNoMemberCheck = false

    /**
     * 管理员**代绑**前的成员校验（`feature.bind.require_in_group`）。
     *
     * ## 只用于代绑路径
     *
     * 本方法**只应被"管理员为第三方 QQ 代绑"这一条路径调用**。理由：
     * 所有指令都经 `BotHandler.onGroupMessage` 进入，**没有私聊入口** ——
     * 自助申请（群员或管理员给自己绑）的人，其成员身份已由"能在本群发消息"这一事实确立。
     * 对他再查一次不带来任何新信息，却会引入"查询超时被误判为不在群 → 误拒"的风险。
     * 被代绑的 QQ 没发过任何消息，这才是唯一真正未知的量。
     *
     * ## 定位：防呆，不是安全边界
     *
     * 它拦的是"管理员打错 QQ 号，把白名单绑到不存在/无关的账号上"。
     * **不能**被当作"白名单里的 QQ 一定在群内"这一全局保证 —— 游戏内
     * `/plumbot bind add`（`plumbot.admin` 权限）与 `PlumBotAPI.addBinding` 两条写入路径
     * 在架构上无法查询 QQ 群成员，不经过本方法。
     *
     * 行为：
     * - 开关关闭 → 直接放行，**完全不调用** [IBot.checkUserInGroup]（零开销、零阻塞）；
     * - 适配器未声明 [BotCapability.GROUP_MEMBER_CHECK] → **放行 + 一次性 WARN**
     *   （无法校验时若一律拒绝，会让该开关在缺少此能力的适配器上使代绑功能整体不可用）；
     * - 其余按 [IBot.checkUserInGroup] 判定，不在群则发拒绝文案并返回 false。
     *
     * 注意：`checkUserInGroup` **无法区分"确认不在群"与"查询超时"**，两者都返回 `false`。
     * 因此拒绝文案对两种情况都成立（见 `messages.yml` 的 `notInGroup`）。
     *
     * @param targetUserId 被代绑的 QQ
     * @return true 表示允许继续创建绑定
     */
    fun ensureTargetInGroup(groupId: Long, targetUserId: Long): Boolean {
        if (!config.getBoolean("feature", "bind", "require_in_group")) return true

        if (!bot.metadata.capabilities.contains(BotCapability.GROUP_MEMBER_CHECK)) {
            if (!warnedNoMemberCheck) {
                warnedNoMemberCheck = true
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Bind] 已开启 require_in_group，但适配器 ${bot.metadata.type} " +
                        "未声明 GROUP_MEMBER_CHECK 能力，无法校验成员身份 —— 本次及后续绑定将**直接放行**。"
                )
            }
            return true
        }

        if (bot.checkUserInGroup(targetUserId, groupId)) return true

        sendRawMessage(groupId, render(Messages.notInGroup, "%user_id%" to targetUserId.toString()))
        return false
    }

    fun render(template: String, vararg replacements: Pair<String, String>): String =
        replacements.fold(template) { result, (placeholder, value) -> result.replace(placeholder, value) }

    /**
     * 以**指定路由**发送结构化载荷；门户不可用或渲染失败时自动降级为 [fallbackText]。
     *
     * 这是业务层与渲染层的标准接口：业务层只决定"哪个 route + 什么 payload + 纯文本兜底长什么样"。
     */
    fun sendRendered(
        groupId: Long,
        route: RenderRoute,
        payload: Any,
        fallbackText: String,
        pic: Boolean
    ) = sendRendered(groupId, route, fallbackText, pic) { payload }

    /**
     * 变体：载荷在**渲染协程内**构建。
     *
     * 用于需要在出图前做异步准备（例如服务端预取头像并写入磁盘缓存）的场景 ——
     * 这样准备开销既不占用调用线程，也不落在渲染超时窗口内。
     */
    fun sendRendered(
        groupId: Long,
        route: RenderRoute,
        fallbackText: String,
        pic: Boolean,
        payload: suspend (RenderPortal) -> Any
    ) {
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)

        val portal = if (pic) RenderProvider.getOrNull() else null
        if (portal == null) {
            bot.sendMsg(true, groupId, fallbackText, false)
            return
        }

        renderScope.launch {
            val result = try {
                portal.render(
                    RenderRequest(
                        route = route.route,
                        payload = payload(portal),
                        targetKey = "group:$groupId"
                    )
                )
            } catch (t: Throwable) {
                RenderResult.Failed(t.message ?: "exception")
            }
            val png = (result as? RenderResult.Image)?.png
            val sent = png != null && runCatching { bot.sendGroupImage(groupId, png) }.isSuccess
            if (!sent) {
                bot.sendMsg(true, groupId, fallbackText, false)
            }
        }
    }

    /**
     * 列表型绑定结果统一走 `bindList` 路由（`whitelist` 路由只承接单人成功绑定）。
     */
    fun sendBindList(groupId: Long, title: String, players: List<String>, fallbackText: String) {
        sendRendered(
            groupId = groupId,
            route = RenderRoute.BIND_LIST,
            payload = mapOf(
                "title" to title,
                "players" to players,
                "total" to players.size
            ),
            fallbackText = fallbackText,
            pic = true
        )
    }

    /**
     * 单人绑定成功卡片（`whitelist` 路由）。
     *
     * 与 [sendBindList] 的分工：**单人成功绑定**走定制卡片（含头像与绑定时间），
     * 其余列表型回复一律走 `bindList`。
     */
    fun sendWhitelistCard(
        groupId: Long,
        playerName: String,
        qq: String,
        bindTime: String?,
        fallbackText: String
    ) {
        sendRendered(
            groupId = groupId,
            route = RenderRoute.WHITELIST,
            fallbackText = fallbackText,
            pic = true
        ) { portal ->
            mapOf(
                "playerName" to playerName,
                "qq" to qq,
                "bindTime" to bindTime,
                "avatarUrl" to runCatching {
                    portal.fetchAvatarAsDataUrl("qq:$qq", "https://q1.qlogo.cn/g?b=qq&nk=$qq&s=640")
                }.getOrNull()
            )
        }
    }

    /**
     * 平台原生头像卡片（`hytaleAvatar` 路由）。
     *
     * 解析顺序：平台原生外观图层（Hytale 部件化皮肤，服务端合成）→ 绑定 QQ 头像 → 前端占位图。
     * 在非 Hytale 平台上退化为 QQ 头像卡片，因此该指令三端均可用。
     */
    fun sendAvatarCard(groupId: Long, playerName: String, fallbackText: String) {
        sendRendered(
            groupId = groupId,
            route = RenderRoute.HYTALE_AVATAR,
            fallbackText = fallbackText,
            pic = avatarPicEnabled()
        ) { portal ->
            val qq = DatabaseProvider.getBindByName(playerName)

            // ①–③ 皮肤漏斗（皮肤插件 → GameProfile → 正版源），服务端裁头 + 缓存
            val skin = runCatching { portal.resolveSkinAvatar(playerName) }.getOrNull()

            // ④ 平台原生外观图层（Hytale 部件化皮肤）→ 服务端合成
            val layers = runCatching {
                context.playerService.resolveAvatarLayers(playerName)
            }.getOrDefault(emptyList())

            val composed = if (skin != null || layers.isEmpty()) {
                null
            } else {
                runCatching {
                    portal.composeAvatar(
                        AvatarComposeRequest(
                            cacheKey = "platform:$playerName",
                            layers = layers.map { layer ->
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
            }

            // ⑤ 绑定 QQ 头像
            val qqAvatar = if (skin != null || composed != null) {
                null
            } else {
                qq?.let { q ->
                    runCatching {
                        portal.fetchAvatarAsDataUrl("qq:$q", "https://q1.qlogo.cn/g?b=qq&nk=$q&s=640")
                    }.getOrNull()
                }
            }

            mapOf(
                "username" to playerName,
                "avatarUrl" to (skin ?: composed ?: qqAvatar),
                "fallbackQq" to qq
            )
        }
    }

    fun sendBindMessage(groupId: Long, message: String) {
        val pic = true
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, message, pic)
    }

    fun sendBindTemplate(groupId: Long, template: String, vararg replacements: Pair<String, String>) {
        sendBindMessage(groupId, render(template, *replacements))
    }

    fun sendListTemplate(groupId: Long, template: String, vararg replacements: Pair<String, String>) {
        val pic = true
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, render(template, *replacements), pic)
    }

    fun sendWrongUsage(groupId: Long) {
        sendBindMessage(groupId, Messages.wrongUsage)
    }

    fun sendRawMessage(groupId: Long, message: String, pic: Boolean = false) {
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, message, pic)
    }

    fun groupMemberNameCard(groupId: Long, userId: Long): Pair<String, String> {
        bot.requireCapability(BotCapability.GROUP_MEMBER_QUERY)
        return bot.getGroupUserName(groupId, userId) to bot.getGroupUserCard(groupId, userId)
    }

    fun userReplacements(groupId: Long, userId: Long): Array<Pair<String, String>> {
        val (name, card) = groupMemberNameCard(groupId, userId)
        return arrayOf(
            "%user_id%" to userId.toString(),
            "%user_name%" to name,
            "%user_nick%" to card
        )
    }

    fun originReplacements(groupId: Long, userId: Long): Array<Pair<String, String>> {
        val (_, card) = groupMemberNameCard(groupId, userId)
        return arrayOf(
            "%origin_id%" to userId.toString(),
            "%origin_name%" to card
        )
    }

    fun checkPlayerExists(player: String): Boolean = DatabaseProvider.getBindByName(player) != null

    /** 绑定额度上限（`feature.bind.maxNum`）；供事务化写入在同一事务内使用（A04）。 */
    fun bindingLimit(): Int = config.getInteger("feature", "bind", "maxNum")

    fun checkUserBindingFull(userId: String): Boolean =
        // 走数据库端 COUNT：此前把该 QQ 的全部绑定行取回再 .size，行数随规模线性增长（A04 优化）
        DatabaseProvider.getDatabase()?.countByUser(userId)
            ?.let { it >= config.getInteger("feature", "bind", "maxNum") } ?: false
}
