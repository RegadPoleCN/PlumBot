// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.server

/**
 * 登录校验结果：[allowed]=false 时必须给出 [kickMessage]；[denial] 让平台区分「查清没绑定」
 * 与「没查清」，从而对玩家说不同的话。
 */
data class PreLoginResult(
    val allowed: Boolean,
    val kickMessage: String? = null,
    /** 拒绝的原因分类；平台可据此选择踢出方式（Bukkit 用不同 kick result）。 */
    val denial: PreLoginDenial = PreLoginDenial.NONE
)

/**
 * 登录被拒的原因：[NONE] 放行；[NOT_BOUND] **查到了**确实没绑定（玩家该去申请白名单）；
 * [LOOKUP_UNAVAILABLE] **没查清**（超时/未就绪/异常）——不是玩家的错，别给加群提示。
 */
enum class PreLoginDenial { NONE, NOT_BOUND, LOOKUP_UNAVAILABLE }
