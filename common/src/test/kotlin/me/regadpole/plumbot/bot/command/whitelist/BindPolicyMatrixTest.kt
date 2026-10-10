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

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import me.regadpole.plumbot.testing.RecordingDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A05 验收：**写入策略的 5×5 矩阵**。
 *
 * 背景：写入此前分散在三条路径（管理员自助、管理员代绑、玩家自助），各自复制"名额判断 + 重名判断"，
 * 任何一处漏改就造成路径间行为不一致。现在统一走 [BindPolicy]，本用例把**初始库状态 × 请求形态**
 * 的决策结果固定下来 —— 这是"路径间行为一致"唯一可验证的形式。
 *
 * 5 种初始状态 × 5 种请求 = 25 个格子，逐格断言**决策结果**与**库内条数**。
 */
class BindPolicyMatrixTest {

    private val limit = 2
    private val target = "Steve"
    private val other = "Alex"

    /** 初始库状态：`qq -> 已绑定的角色名集合`。 */
    private data class State(val name: String, val seed: Map<Long, List<String>>)

    private data class Request(val name: String, val playerName: String, val bypass: Boolean)

    private fun policy(): BindPolicy {
        val context = FakePlatformContext(
            mapOf("feature.bind.maxNum" to limit)
        )
        return BotCommandService(context, FakeBot(context)).bindPolicy
    }

    private fun seed(database: RecordingDatabase, state: State) {
        state.seed.forEach { (qq, names) -> names.forEach { database.addBind(qq.toString(), it) } }
    }

    private fun newDatabase() = RecordingDatabase()

    private val states = listOf(
        State("空库", emptyMap()),
        State("该 QQ 已有 1 条（不同名）", mapOf(1L to listOf(other))),
        State("该 QQ 已达上限（$limit 条）", mapOf(1L to listOf(other, "Herobrine"))),
        State("目标名已被其它 QQ 绑定", mapOf(2L to listOf(target))),
        State("该 QQ 已满 + 目标名已被其它 QQ 绑定", mapOf(1L to listOf(other, "Herobrine"), 2L to listOf(target))),
    )

    private val requests = listOf(
        Request("新名字（普通）", "Notch", bypass = false),
        Request("已存在的名字（普通）", target, bypass = false),
        Request("已存在名字的大小写变体（普通）", "STEVE", bypass = false),
        Request("新名字（管理员绕过名额）", "Notch", bypass = true),
        Request("已存在的名字（管理员绕过名额）", target, bypass = true),
    )

    /**
     * 期望表：`状态序号 -> 请求序号 -> (决策, 该 QQ 最终条数)`。
     *
     * 手工列出而不是在测试里重算策略，否则"实现即预期"，矩阵就失去意义。
     */
    private val expected: Map<Int, Map<Int, Pair<BindPolicy.Result, Int>>> = mapOf(
        // 状态 0：空库 —— 都能写入
        0 to mapOf(
            0 to (BindPolicy.Result.Added to 1),
            1 to (BindPolicy.Result.Added to 1),
            2 to (BindPolicy.Result.Added to 1),
            3 to (BindPolicy.Result.Added to 1),
            4 to (BindPolicy.Result.Added to 1),
        ),
        // 状态 1：已有 1 条 —— 还能再写 1 条
        1 to mapOf(
            0 to (BindPolicy.Result.Added to 2),
            1 to (BindPolicy.Result.Added to 2),
            2 to (BindPolicy.Result.Added to 2),
            3 to (BindPolicy.Result.Added to 2),
            4 to (BindPolicy.Result.Added to 2),
        ),
        // 状态 2：名额已满 —— 普通路径拒绝；绕过路径在"名字空闲"时仍会写入（配额与同名是两件事）
        2 to mapOf(
            0 to (BindPolicy.Result.LimitReached to limit),
            1 to (BindPolicy.Result.LimitReached to limit),
            2 to (BindPolicy.Result.LimitReached to limit),
            3 to (BindPolicy.Result.Added to limit + 1),
            4 to (BindPolicy.Result.Added to limit + 1),
        ),
        // 状态 3：目标名被别的 QQ 占了 —— **绕过名额也救不了**（同名冲突与名额无关）；
        // 但注意请求 0/3 用的是**新名字** Notch，与"目标名被占"无关，因此正常写入
        3 to mapOf(
            0 to (BindPolicy.Result.Added to 1),
            1 to (BindPolicy.Result.PlayerAlreadyBound to 0),
            2 to (BindPolicy.Result.PlayerAlreadyBound to 0),
            3 to (BindPolicy.Result.Added to 1),
            4 to (BindPolicy.Result.PlayerAlreadyBound to 0),
        ),
        // 状态 4：既满员、目标名又被占 —— 普通路径报"名额已满"，绕过路径按"名字是否被占"判定
        4 to mapOf(
            0 to (BindPolicy.Result.LimitReached to limit),
            1 to (BindPolicy.Result.LimitReached to limit),
            2 to (BindPolicy.Result.LimitReached to limit),
            3 to (BindPolicy.Result.Added to limit + 1),
            4 to (BindPolicy.Result.PlayerAlreadyBound to limit),
        ),
    )

    @Test
    fun `the write policy behaves identically across the whole 5x5 matrix`() {
        val failures = mutableListOf<String>()

        states.forEachIndexed { stateIndex, state ->
            requests.forEachIndexed { requestIndex, request ->
                val database = newDatabase()
                seed(database, state)
                val policy = policy()

                val result = policy.bind(database, userId = 1L, playerName = request.playerName, bypassLimit = request.bypass)

                val (expectedResult, expectedCount) = expected.getValue(stateIndex).getValue(requestIndex)
                val actualCount = database.getByUser("1").size

                if (result != expectedResult) {
                    failures += "「${state.name}」×「${request.name}」：决策应为 $expectedResult，实际 $result"
                }
                if (actualCount != expectedCount) {
                    failures += "「${state.name}」×「${request.name}」：该 QQ 条数应为 $expectedCount，实际 $actualCount"
                }
                // 全局不变量：任何格子里都不得出现同一角色名的两条记录
                val sameName = database.getByUser("1").count { it.playerName.equals(target, ignoreCase = true) } +
                    database.getByUser("2").count { it.playerName.equals(target, ignoreCase = true) }
                if (sameName > 1) {
                    failures += "「${state.name}」×「${request.name}」：目标名出现 $sameName 条记录（唯一性被破坏）"
                }
            }
        }

        assertTrue(failures.isEmpty(), "矩阵共 ${states.size * requests.size} 格，${failures.size} 格不符：\n" + failures.joinToString("\n"))
        assertEquals(25, states.size * requests.size, "矩阵规模必须是 5×5")
    }
}
