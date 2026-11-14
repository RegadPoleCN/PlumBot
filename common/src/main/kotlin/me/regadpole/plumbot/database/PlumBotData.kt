// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.Binding

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import taboolib.module.database.ResultProcessorList
import java.sql.ResultSet

fun Binding.Companion.of(data: ResultSet?): Binding? {
    data ?: return null
    val id = data.getInt("id")
    val userId = data.getString("user_id") ?: return null
    val playerName = data.getString("player_name") ?: return null
    val playerUUID = data.getString("player_uuid")
    val sourceGroup = data.getString("source_group")
    val bindingTime = parseBindingTime(data.getString("binding_time"), playerName)
    return Binding(id, userId, playerName, playerUUID, bindingTime, sourceGroup)
}

fun Binding.Companion.of(data: ResultProcessorList): List<Binding> {
    val result = ArrayList<Binding>()
    data.forEach {
        // 不要在这里调用 `wasNull()`：JDBC 规范里它指的是"上一次 getXxx 读到的列"，
        // 而本行尚未读任何列。SQLite 驱动会直接抛 `column -1 out of bounds`（MySQL 恰好返回 false，
        // 因此该缺陷只在 SQLite 后端暴露）。空值由下面的 null 判断处理。
        val id = getInt("id")
        val userId = getString("user_id") ?: return@forEach
        val playerName = getString("player_name") ?: return@forEach
        val playerUUID = getString("player_uuid")
        val sourceGroup = getString("source_group")
        val bindingTime = parseBindingTime(getString("binding_time"), playerName)
        result.add(Binding(id, userId, playerName, playerUUID, bindingTime, sourceGroup))
    }
    return result
}

/** 每个进程只告警一次：损坏行可能成批出现，逐行打印会淹没服务端日志。 */
private val warnedMalformedBindingTime = java.util.concurrent.atomic.AtomicBoolean(false)

/**
 * 解析 binding_time 字段。
 *
 * - 字段缺失（NULL，历史数据行）：回落当前时间 —— 旧行本来就没有该值，回落可接受；
 * - 字段有值但不是合法 epoch 毫秒：数据已损坏，返回 null（Binding.bindingTime 即"未知"）
 *   并告警一次，绝不再回落"现在" —— 那会把损坏伪装成"刚刚绑定"，管理员无法察觉。
 */
private fun parseBindingTime(raw: String?, playerName: String?): Instant? {
    if (raw == null) return Clock.System.now()
    val parsed = raw.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
    if (parsed == null && warnedMalformedBindingTime.compareAndSet(false, true)) {
        // 数据层拿不到 PlatformContext，用 JDK 标准日志兜底（三个宿主都会把它接到平台日志）。
        java.util.logging.Logger.getLogger("PlumBot").warning(
            "[PlumBot-DB] binding_time 字段损坏（玩家 $playerName，原始值: $raw），绑定时间将显示为未知；请检查数据库或迁移脚本。"
        )
    }
    return parsed
}
