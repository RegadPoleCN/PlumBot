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
