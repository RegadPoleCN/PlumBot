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

package me.regadpole.plumbot.internal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A05 验收：**玩家名校验规则只有一份定义**。
 *
 * 为什么用源码扫描而不是普通单测：重复定义不会让任何行为"出错"，只会让**行为悄悄分叉**
 * ——加法是"再抄一份正则"，减法（删掉其中一份）没人会主动做。因此这里把"唯一性"本身
 * 变成断言：出现第二份副本即测试失败。
 *
 * 测试工作目录是模块目录（Gradle 默认），故 `:api` 用相对路径访问。
 */
class PlayerNameRulesTest {

    /** 与生产规则等价的字面量特征（用于发现"另抄一份"）。 */
    private val signature = "a-zA-Z0-9_.*]{3,16}"

    @Test
    fun `the player name regex is defined in exactly one place`() {
        val roots = listOf(File("src/main/kotlin"), File("../api/src/main/kotlin"))
            .filter { it.isDirectory }

        assertTrue(roots.isNotEmpty(), "找不到源码目录，扫描会静默通过 —— 判失败（工作目录：${File(".").absolutePath}）")

        val hits = roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().withIndex()
                        .filter { (_, line) -> line.contains(signature) }
                        .map { (index, line) -> "${file.path}:${index + 1}  ${line.trim()}" }
                }
        }

        assertEquals(
            1,
            hits.size,
            "玩家名校验规则必须只有一处定义，实际发现 ${hits.size} 处：\n" + hits.joinToString("\n")
        )
        assertTrue(
            hits.single().contains("PlayerNameRules"),
            "唯一那份定义必须位于 PlayerNameRules，实际：${hits.single()}"
        )
    }

    @Test
    fun `the shared rule keeps the documented bounds`() {
        assertTrue(PlayerNameRules.isValid("abc"), "3 位是下限")
        assertTrue(PlayerNameRules.isValid("Steve_123"))
        assertTrue(PlayerNameRules.isValid("a.b*c"), "点与星号是刻意允许的（基岩版互通等场景）")
        assertTrue(PlayerNameRules.isValid("a".repeat(16)), "16 位是上限")

        assertFalse(PlayerNameRules.isValid("ab"), "2 位过短")
        assertFalse(PlayerNameRules.isValid("a".repeat(17)), "17 位过长")
        assertFalse(PlayerNameRules.isValid("有中文"), "非 ASCII 名字不被接受（与三端登录名一致）")
        assertFalse(PlayerNameRules.isValid("with space"))
        assertFalse(PlayerNameRules.isValid(null))
        assertFalse(PlayerNameRules.isValid(" Steve "), "带空格应被拒 —— 校验不做隐式 trim，调用方负责")
    }
}
