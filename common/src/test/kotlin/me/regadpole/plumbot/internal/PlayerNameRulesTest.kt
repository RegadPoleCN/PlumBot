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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 玩家名校验规则的**行为**边界：「规则只有一份定义」由构建期 `checkSingleDefinitions` 源码扫描
 * 保证（不依赖测试工作目录），这里只留行为断言。
 */
class PlayerNameRulesTest {

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
