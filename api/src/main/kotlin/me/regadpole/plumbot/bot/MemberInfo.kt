package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.PublicApi

/**
 * 群成员信息数据模型。
 *
 * @param userId 成员 QQ 号
 * @param name 昵称
 * @param card 群名片
 * @param role 角色，例如 owner/admin/member
 */
@PublicApi
data class MemberInfo(
    val userId: Long,
    val name: String,
    val card: String,
    val role: String = "member"
)
