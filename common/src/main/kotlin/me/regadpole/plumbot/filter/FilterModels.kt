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

package me.regadpole.plumbot.filter

/**
 * 命中敏感词后的处理动作
 */
enum class FilterAction {
    BLOCK,
    REPLACE;

    companion object {
        fun fromString(value: String?): FilterAction = when (value?.lowercase()) {
            "block" -> BLOCK
            else -> REPLACE
        }
    }
}

/**
 * 过滤处理结果
 */
data class FilterProcessResult(
    val isBlocked: Boolean,
    val matchedWords: List<String>,
    val sanitizedText: String
)

/**
 * 云端词库 JSON 对应实体
 */
data class CloudDatabase(
    val lastUpdateDate: String? = null,
    val words: List<String> = emptyList()
)
