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

/**
 * 玩家名校验规则 —— **唯一定义**。
 *
 * 同一条正则曾在 `BindApplyCommand` 与 `PlumBotApiImpl` **各写一份**；危险不在重复，而在于两份
 * 副本**会各自演化**：聊天入口放宽了长度、API 入口没放宽，"群里能绑、走 API 绑不上"这类不一致
 * 会静默存在，且没有任何机制发现。
 *
 * 因此规则只在这里定义，由 `PlayerNameRulesTest` 用源码扫描断言其唯一性（出现第二份副本即失败）。
 */
object PlayerNameRules {

    /**
     * 允许 3–16 位：字母、数字、下划线、点、星号。保留 `.` 与 `*` 是有意的 —— 部分服务端允许
     * 带这类字符的名字（如基岩版互通场景）。
     */
    val REGEX = Regex("""^[a-zA-Z0-9_.*]{3,16}$""")

    /** 是否是一个格式合法的玩家名（调用方应自行 `trim()`，此处不隐式修改输入）。 */
    fun isValid(name: String?): Boolean = name != null && REGEX.matches(name)
}
