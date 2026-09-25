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
