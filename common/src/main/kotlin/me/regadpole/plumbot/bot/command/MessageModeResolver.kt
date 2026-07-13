package me.regadpole.plumbot.bot.command

object MessageModeResolver {

    fun resolve(message: String, mode: Int, prefix: String?): String? {
        return when (mode) {
            0 -> message
            1 -> {
                val p = prefix ?: ""
                if (p.isNotEmpty() && message.startsWith(p)) {
                    message.removePrefix(p)
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
