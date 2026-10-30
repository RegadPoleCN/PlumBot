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

package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.platform.PlatformType

@PublicApi
enum class BotCapability {
    GROUP_MESSAGE_SEND,
    USER_MESSAGE_SEND,

    /**
     * **协议层**发图能力：能把图片字节投递到 IM 平台。不代表渲染可用（无图形环境、引擎关闭、
     * 模板缺失都会失败）；渲染可用性由渲染门户判定，出图失败路径**必须降级为纯文本**。
     */
    IMAGE_SEND,
    GROUP_MEMBER_QUERY,
    GROUP_MEMBER_CHECK,
    GROUP_MESSAGE_RECEIVE,
    GROUP_MEMBER_DECREASE_RECEIVE
}

@PublicApi
data class BotAdapterMetadata(
    val type: String,
    val displayName: String = type,
    val supportedPlatforms: Set<PlatformType> = emptySet(),
    val requiredPlugins: Set<String> = emptySet(),
    val capabilities: Set<BotCapability> = emptySet()
) {
    fun supports(platformType: PlatformType): Boolean =
        supportedPlatforms.isEmpty() || supportedPlatforms.contains(platformType)
}

@PublicApi
data class MemberInfo(
    val userId: Long,
    val name: String,
    val card: String,
    val role: String = "member"
)
