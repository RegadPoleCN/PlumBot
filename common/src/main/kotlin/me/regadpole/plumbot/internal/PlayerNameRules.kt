// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
