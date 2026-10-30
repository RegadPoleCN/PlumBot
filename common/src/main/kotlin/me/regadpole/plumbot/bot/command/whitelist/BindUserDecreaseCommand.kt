// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.bot.command.BotCommandService
import java.util.concurrent.ConcurrentHashMap

/**
 * 退群解绑：只解绑**来源群就是本次退出的群**的记录。
 *
 * 旧实现是"把这个 QQ 的全部绑定解掉并踢人"，退一个群就把他在其他群的绑定一起废掉。
 * 现在按 `source_group` 三分：
 *
 * - **mine**（`source_group` == 本群）→ 踢人 + 删除该行；
 * - **unknown**（列为 NULL / 空串：升级前的历史数据、游戏内指令、外部 API 写入）→ **保留**，
 *   每个群在该进程内首次遇到时告警一次。删错等于把玩家锁在服务器门外，代价不可逆，
 *   因此宁可留脏数据也不猜；
 * - **others**（别的群）→ 保留，不告警。
 *
 * 顺带受 `feature.bind.enable` 门控：功能关闭时不该继续改变玩家数据。
 */
class BindUserDecreaseCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun handle(message: String, groupId: Long, userId: Long) {
        if (!BindFeature.enabled(service.config)) {
            service.context.log(
                LogLevel.DEBUG,
                "[PlumBot-Bind] 绑定功能已关闭，跳过退群解绑（群 $groupId，QQ $userId）"
            )
            return
        }

        val database = requireDatabase()
        val rows = database.getByUser(userId.toString())
        val groupKey = groupId.toString()
        val mine = rows.filter { it.sourceGroup == groupKey }
        val unknown = rows.filter { it.sourceGroup.isNullOrBlank() }

        if (unknown.isNotEmpty() && warnedUnknownGroups.add(groupKey)) {
            service.context.log(
                LogLevel.WARN,
                "[PlumBot-Bind] 群 $groupKey 的成员 QQ $userId 退群，但有 ${unknown.size} 条绑定没有来源群标记" +
                    "（升级前的历史数据），**已保留**：${unknown.joinToString(", ") { it.playerName }}；" +
                    "如需清理请用解绑指令或管理员手动处理。之后本群再出现同类情况只记 DEBUG。"
            )
        }

        if (mine.isEmpty()) {
            service.context.log(
                LogLevel.DEBUG,
                "[PlumBot-Bind] 群 $groupKey 成员 QQ $userId 退群：没有来自本群的绑定" +
                    "（共 ${rows.size} 条，全部保留）"
            )
            return
        }

        var kicked = 0
        var removed = 0
        mine.forEach { row ->
            // 先踢后删：先删会让"踢出"这条动作失去上下文（代码里还要用 playerName 去踢）
            runCatching { service.context.playerService.kickPlayer(row.playerName) }
                .onSuccess { kicked++ }
                .onFailure {
                    service.context.log(
                        LogLevel.WARN,
                        "[PlumBot-Bind] 退群解绑时踢出失败（${row.playerName}）：${it.message}"
                    )
                }
            runCatching { database.removeBindByNum(userId.toString(), row.id) }
                .onSuccess { if (it != null) removed++ }
                .onFailure {
                    service.context.log(
                        LogLevel.WARN,
                        "[PlumBot-Bind] 退群解绑时删除失败（${row.playerName} / id=${row.id}）：${it.message}"
                    )
                }
        }

        service.context.log(
            LogLevel.INFO,
            "[PlumBot-Bind] 群 $groupKey 成员 QQ $userId 退群：解绑 ${mine.size} 条" +
                "（踢出 $kicked 个在线角色，实际删除 $removed 条），保留 ${rows.size - mine.size} 条"
        )
    }

    companion object {
        /** 已就"存在无来源群绑定"告警过的群（进程内去重，避免每次退群都刷屏）。 */
        private val warnedUnknownGroups: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
