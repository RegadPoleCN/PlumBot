// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.platform.PlatformConfig

/**
 * `feature.bind.enable` 的统一读取点。
 *
 * 群内指令、退群解绑与登录校验都必须读它 —— 否则关掉绑定功能后，
 * 玩家仍会因退群被解绑、仍会被登录白名单踢出，开关失去意义。
 *
 * 语义：**缺失 = 关闭**，即 `AbstractConfigurator.getBoolean` 的原生行为。
 * 不要改成"默认开启"：升级后会自动改变现有服务器的行为，而服主没有做过这个选择。
 */
internal object BindFeature {
    fun enabled(config: PlatformConfig): Boolean = config.getBoolean("feature", "bind", "enable")
}
