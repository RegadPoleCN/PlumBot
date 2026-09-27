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

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi
import net.kyori.adventure.text.Component
import java.nio.file.Path

@StableApi
interface PlatformContext {
    val config: PlatformConfig
    val datasource: PlatformConfig
    val dataDirectory: Path
    val scheduler: PlatformScheduler
    val playerService: PlatformPlayerService
    val platformType: PlatformType

    /** 当前运行平台所声明支持的全部特性能力集合 */
    val supportedCapabilities: Set<PlatformCapability>
        get() = emptySet()

    /**
     * 日志输出能力（内聚于平台上下文）
     */
    fun log(level: LogLevel, message: String)

    /**
     * 向全服控制台与在线玩家广播 Adventure 富文本消息
     */
    fun sendMessage(message: Component)

    fun isPluginAvailable(name: String): Boolean

    /** 校验当前平台是否具备某个能力 */
    fun hasCapability(capability: PlatformCapability): Boolean =
        capability in supportedCapabilities
}
