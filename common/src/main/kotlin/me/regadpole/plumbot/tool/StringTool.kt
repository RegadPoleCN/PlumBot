package me.regadpole.plumbot.tool

import java.util.regex.Matcher
import java.util.regex.Pattern

object StringTool {
    fun filterColor(text: String?): String {
        val regEx = "§[0-9a-zA-Z]"
        val p: Pattern = Pattern.compile(regEx)
        val matcher: Matcher = p.matcher(text)
        return matcher.replaceAll("").trim()
    }
}