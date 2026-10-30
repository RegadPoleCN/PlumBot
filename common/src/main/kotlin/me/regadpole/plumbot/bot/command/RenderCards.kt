// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.render.RenderCard
import me.regadpole.plumbot.utils.ComponentProjector

/**
 * 渲染卡片的**唯一**构造入口。每个路由同时产出：`text`（带 `§` 色码的完整正文，图片与
 * 纯文本共同的正文真源）、`title`（业务层给的标题，前端不得再写死同类文案）、`data`
 * （模板需要、但正文里无法可靠反解的结构化字段）。
 *
 * 调用方**不得**再另外拼一份 fallbackText：统一用 [plain] 取 `text` 的无样式投影，这样
 * 渲染失败、配置关闭、发送失败三种降级路径与图片路径共享同一份内容。
 */
object RenderCards {

    /** 图片路径不可用时的纯文本投影：去掉 `§` 色码，保留换行与全部正文。 */
    fun plain(text: String): String = ComponentProjector.stripSectionCodes(text)

    /**
     * 卡片的纯文本投影：先按 [RenderCard.displayText] 合入标题，再去掉 `§` 色码。
     *
     * 降级路径必须走这里而不是 [plain] 的字符串重载：直接传 `card.text` 会让只存在于
     * `title` 的信息（status 的服务器名、kick/advancement 的标题）在文字版里消失。
     */
    fun plain(card: RenderCard): String = plain(card.displayText)

    private fun fill(template: String, vararg replacements: Pair<String, String>): String =
        replacements.fold(template) { result, (placeholder, value) -> result.replace(placeholder, value) }

    /**
     * 把「只存在于 `data` 的事实」补进正文：`text` 与 `data` 由同一次调用产生，调用方自己
     * 拼这一行迟早会漏，放在这里图片与纯文本两条投影必然共享同一份内容。
     *
     * 重复追加由 [marker]（默认整行）拦截，因此用户自定义 `messages.yml` 已含该行时不会翻倍。
     */
    private fun appendFact(text: String, line: String?, marker: String? = null): String {
        if (line.isNullOrBlank()) return text
        val key = (marker ?: line).trim()
        if (key.isNotEmpty() && text.contains(key)) return text
        return if (text.isBlank()) line else text.trimEnd() + "\n" + line
    }

    fun status(
        serverName: String,
        tps: List<String>,
        mspt: String,
        usedMem: Long,
        maxMem: Long,
        online: Int,
        health: String
    ): RenderCard {
        val text = fill(
            Messages.serverStatus,
            "%tps_1m%" to tps.getOrElse(0) { "?" },
            "%tps_5m%" to tps.getOrElse(1) { "?" },
            "%tps_15m%" to tps.getOrElse(2) { "?" },
            "%mspt%" to mspt,
            "%used_mem%" to usedMem.toString(),
            "%max_mem%" to maxMem.toString(),
            "%online%" to online.toString(),
            "%health%" to health
        )
        return RenderCard(
            text = text,
            title = serverName,
            data = linkedMapOf(
                "serverName" to serverName,
                "tps" to tps,
                "mspt" to mspt,
                "usedMem" to usedMem,
                "maxMem" to maxMem,
                "online" to online,
                "health" to health
            )
        )
    }

    fun whitelist(
        text: String,
        title: String,
        playerName: String,
        qq: String,
        bindTime: String?,
        avatarUrl: String?,
        originId: String?,
        originName: String?,
        bindCount: Int,
        current: List<String>
    ): RenderCard = RenderCard(
        // 绑定时间只在 data 里：不补进正文，文字版就看不到（图片版却由模板显示）。
        text = appendFact(
            text,
            bindTime?.takeIf { it.isNotBlank() }?.let { fill(Messages.bindTimeLine, "%bind_time%" to it) },
            bindTime
        ),
        title = title,
        data = linkedMapOf(
            "playerName" to playerName,
            "qq" to qq,
            "bindTime" to bindTime,
            "avatarUrl" to avatarUrl,
            "originId" to originId,
            "originName" to originName,
            "bindCount" to bindCount,
            "current" to current
        )
    )

    fun bindList(
        text: String,
        title: String,
        userId: String,
        players: List<String>,
        limit: Int
    ): RenderCard = RenderCard(
        // 上限与名单都可能"只在 data 里"：两者都补进正文，图片与文字才不会各说一半。
        text = appendFact(
            appendFact(text, fill(Messages.bindLimitLine, "%limit%" to limit.toString())),
            players.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?.let { fill(Messages.bindPlayersLine, "%players%" to it) },
            players.joinToString(", ")
        ),
        title = title,
        data = linkedMapOf(
            "title" to title,
            "userId" to userId,
            "players" to players,
            "total" to players.size,
            "limit" to limit
        )
    )

    fun help(text: String, prefix: String, lines: List<String>): RenderCard {
        val commands = lines.drop(1).map { line ->
            val match = HELP_LINE.find(line)
            if (match == null) {
                linkedMapOf("usage" to line.trim(), "description" to "")
            } else {
                linkedMapOf(
                    "usage" to match.groupValues[1],
                    "description" to match.groupValues[2].trim()
                )
            }
        }
        // 正文只由 `text` 承载，`data` 只留模板无法从正文反解的字段（prefix / commands）。
        // 放一份逐行副本会变成两份正文，改一处就图文不一致。
        return RenderCard(
            text = text,
            title = lines.firstOrNull()?.takeIf { it.isNotBlank() },
            data = linkedMapOf(
                "prefix" to prefix,
                "commands" to commands
            )
        )
    }

    fun notice(text: String): RenderCard = RenderCard(text = text)

    fun message(text: String, playerName: String, serverName: String, message: String): RenderCard =
        RenderCard(
            text = text,
            title = playerName,
            data = linkedMapOf(
                "playerName" to playerName,
                "serverName" to serverName,
                "message" to message
            )
        )

    fun joinAndLeave(
        text: String,
        type: String,
        playerName: String,
        serverName: String?
    ): RenderCard = RenderCard(
        text = text,
        title = playerName,
        data = linkedMapOf(
            "type" to type,
            "playerName" to playerName,
            "serverName" to serverName
        )
    )

    fun changeServer(
        text: String,
        playerName: String,
        originServer: String,
        targetServer: String
    ): RenderCard = RenderCard(
        text = text,
        title = playerName,
        data = linkedMapOf(
            "playerName" to playerName,
            "originServer" to originServer,
            "targetServer" to targetServer
        )
    )

    fun death(
        text: String,
        playerName: String,
        serverName: String,
        deathMessage: String
    ): RenderCard = RenderCard(
        text = text,
        title = playerName,
        data = linkedMapOf(
            "playerName" to playerName,
            "serverName" to serverName,
            "deathMessage" to deathMessage
        )
    )

    fun advancement(
        text: String,
        playerName: String,
        serverName: String,
        advancementTitle: String
    ): RenderCard = RenderCard(
        text = text,
        title = advancementTitle,
        data = linkedMapOf(
            "playerName" to playerName,
            "serverName" to serverName,
            "advancementTitle" to advancementTitle
        )
    )

    fun kick(
        text: String,
        playerName: String,
        reason: String,
        guide: String
    ): RenderCard = RenderCard(
        text = text,
        title = playerName,
        data = linkedMapOf(
            "playerName" to playerName,
            "reason" to reason,
            "guide" to guide
        )
    )

    /** 控制台卡片：正文由 [me.regadpole.plumbot.bot.command.RemoteConsoleCommandService] 组装。 */
    fun console(
        text: String,
        title: String?,
        state: String,
        channel: String?,
        code: String?,
        durationMs: Long?
    ): RenderCard = RenderCard(
        // 状态/通道只在 data 里（模板用它画徽标）：正文补一行，文字版才有同样的信息。
        text = appendFact(
            text,
            fill(Messages.consoleMetaLine, "%state%" to state, "%channel%" to (channel ?: "-"))
        ),
        title = title,
        data = linkedMapOf(
            "state" to state,
            "channel" to channel,
            "code" to code,
            "durationMs" to durationMs
        )
    )

    fun text(text: String, title: String? = null): RenderCard =
        RenderCard(text = text, title = title)

    /**
     * 头像卡片载荷（`avatar` / `hytaleAvatar` 两个路由共用同一形状）。
     *
     * @param platform 平台品牌（`bukkit` / `velocity` / `hytale` / `unknown`）；null 表示旧路由
     *   不带该字段，**不写入 data** —— 写入 null 会变成协议里多出的键，触发「载荷多出协议
     *   未声明」的 WARN。
     */
    fun hytaleAvatar(
        text: String,
        playerName: String,
        avatarUrl: String?,
        fallbackQq: String?,
        source: String,
        platform: String? = null
    ): RenderCard = RenderCard(
        text = text,
        title = playerName,
        data = buildMap {
            put("username", playerName)
            put("avatarUrl", avatarUrl)
            put("fallbackQq", fallbackQq)
            put("source", source)
            if (platform != null) put("platform", platform)
        }
    )

    private val HELP_LINE = Regex("""^\s*(\S+)(?:\s{2,}|\t+)(.*)$""")
}
