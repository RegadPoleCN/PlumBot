// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * messages.yml 模板与代码传参的变量一致性：
 *
 * 1. 模板里出现的 `%x%` 必须是代码真正传入的变量（防止玩家看到裸占位符）；
 * 2. `# 变量:` 注释既不能漏掉模板实际使用的变量，也不能列出代码不支持的变量（防注释漂移）；
 * 3. 变量表键与 yml 键一一对应（防漏登记/防遗留）。
 *
 * 代码真值固化在 [CODE_VARS]（key → 代码可能传入的变量集合）。测试只读 classpath 资源，
 * 不依赖工作目录。新增模板键时：先核对代码传参，再同步补 [CODE_VARS] 与 yml 注释。
 */
class TemplateVariableConsistencyTest {

    private val yml: String =
        javaClass.getResourceAsStream("/messages.yml")?.readBytes()?.decodeToString()
            ?: error("classpath 缺少 /messages.yml")

    /** 代码侧可能传入的变量集合（允许是模板当前未用到的超集）。 */
    private val CODE_VARS: Map<String, Set<String>> = mapOf(
        "load" to emptySet(),
        "unload" to emptySet(),
        "ob2server" to setOf("group_name", "user_name", "message", "group_id", "user_id", "user_nick"),
        "server2ob" to setOf("server", "player_name", "message"),
        "playerList" to setOf("player_list", "player_num"),
        "avatarCard" to setOf("player_name"),
        "avatarUsage" to setOf("cmdprefix"),
        "notInGroup" to setOf("user_id"),
        "joinProxy" to setOf("player_name"),
        "leaveProxy" to setOf("player_name", "server"),
        "changeServer" to setOf("player_name", "origin_server", "target_server"),
        "death" to setOf("server", "player_name", "death_message"),
        "advancement" to setOf("server", "player_name", "advancement_title"),
        "kickServer" to setOf("groups"),
        "kickPlatform" to setOf("player_name"),
        "kickUnavailable" to emptySet(),
        "playerAddBind" to setOf("user_id", "user_name", "user_nick", "target_player", "num", "current"),
        "playerDeleteBind" to setOf("user_id", "user_name", "user_nick", "target_player", "num", "current"),
        "playerQueryBind" to setOf("user_id", "user_name", "user_nick", "num", "current"),
        "fullBind" to setOf("user_id", "user_name", "user_nick", "num", "players", "whitelist_limit"),
        "bindTimeLine" to setOf("bind_time"),
        "bindLimitLine" to setOf("limit"),
        "bindPlayersLine" to setOf("players"),
        "consoleMetaLine" to setOf("state", "channel"),
        "qqEmptyBind" to setOf("user_id", "user_name", "user_nick"),
        "idEmptyBind" to setOf("player"),
        "existsBind" to setOf("player_name"),
        "notExistsBind" to setOf("player_name"),
        "notBelongToYou" to setOf("player_name"),
        "wrongUsage" to emptySet(),
        "internalError" to emptySet(),
        "adminAddBind" to setOf("origin_id", "origin_name", "user_id", "user_name", "user_nick", "target_player", "num", "current"),
        "adminDeleteBind" to setOf("origin_id", "origin_name", "user_id", "user_name", "user_nick", "target_player", "num", "current"),
        "adminQueryQQBind" to setOf("origin_id", "origin_name", "user_id", "user_name", "user_nick", "num", "current"),
        "adminQueryIdBind" to setOf("origin_id", "origin_name", "player", "user_id", "user_name", "user_nick"),
        "help" to setOf("cmdprefix"),
        "serverStatus" to setOf("tps_1m", "tps_5m", "tps_15m", "mspt", "used_mem", "max_mem", "online", "health"),
        "statusUnavailable" to emptySet(),
        "remoteCommandDenied" to emptySet(),
        "remoteCommandDisabled" to emptySet(),
        "remoteCommandNoOutput" to setOf("channel", "duration"),
        "remoteCommandUnknown" to setOf("channel", "output"),
        "remoteCommandFailed" to setOf("channel", "code", "output"),
        "remoteCommandExecuted" to setOf("output"),
        "remoteCommandError" to emptySet(),
        "remoteCommandTimeout" to emptySet(),
    )

    private data class Entry(val key: String, val template: String, val declared: Set<String>)

    private fun stripQuotes(value: String): String =
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    /** 旧式注释形如 "# player_name, user_id"（整行只有小写标识符列表）时原样识别。 */
    private fun legacyVarList(content: String): Set<String>? {
        val re = Regex("^[a-z_][a-z_0-9]*(,[ ]*[a-z_][a-z_0-9]*)*$")
        if (!re.matches(content)) return null
        return content.split(',').map { it.trim() }.toSet()
    }

    private fun parseEntries(): List<Entry> {
        val entries = mutableListOf<Entry>()
        var declared = mutableSetOf<String>()
        val listBuffer = mutableListOf<String>()
        var listKey: String? = null
        var listDeclared: Set<String> = emptySet()

        fun flushList() {
            val key = listKey ?: return
            entries += Entry(key, listBuffer.joinToString("\n"), listDeclared)
            listKey = null
            listBuffer.clear()
        }

        yml.lineSequence().forEach { raw ->
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> {}
                trimmed.startsWith("#") -> {
                    val content = trimmed.substring(1).trim()
                    if (content.startsWith("变量:")) {
                        val tokens = content.removePrefix("变量:")
                            .split(',', '，')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && it != "无" }
                        declared.addAll(tokens)
                    } else {
                        declared.addAll(legacyVarList(content) ?: emptySet())
                    }
                }
                listKey != null && trimmed.startsWith("-") -> {
                    listBuffer += stripQuotes(trimmed.removePrefix("-").trim())
                }
                else -> {
                    flushList()
                    val key = trimmed.substringBefore(':').trim()
                    if (key != "ver") {
                        val value = trimmed.substringAfter(':').trim()
                        if (value.isEmpty()) {
                            listKey = key
                            listDeclared = declared.toSet()
                        } else {
                            entries += Entry(key, stripQuotes(value), declared.toSet())
                        }
                    }
                    declared = mutableSetOf()
                }
            }
        }
        flushList()
        return entries
    }

    private fun placeholders(template: String): Set<String> =
        Regex("%([A-Za-z0-9_]+)%").findAll(template).map { it.groupValues[1] }.toSet()

    @Test
    fun `every yml key is registered in the code variable table`() {
        val keys = parseEntries().map { it.key }.toSet()
        assertTrue(keys.isNotEmpty(), "messages.yml 未解析到任何键")
        val missing = keys - CODE_VARS.keys
        assertTrue(missing.isEmpty(), "以下模板键未登记代码变量表（先核对代码传参再补表）: $missing")
        val stale = CODE_VARS.keys - keys
        assertTrue(stale.isEmpty(), "变量表包含 messages.yml 已不存在的键: $stale")
    }

    @Test
    fun `template placeholders only use variables the code passes`() {
        parseEntries().forEach { entry ->
            val used = placeholders(entry.template)
            val unsupported = used - CODE_VARS.getValue(entry.key)
            assertTrue(
                unsupported.isEmpty(),
                "${entry.key} 模板使用了代码未传入的变量: $unsupported"
            )
        }
    }

    @Test
    fun `variable comments match the code and the template`() {
        parseEntries().forEach { entry ->
            val used = placeholders(entry.template)
            val undocumented = used - entry.declared
            assertTrue(
                undocumented.isEmpty(),
                "${entry.key} 的『# 变量:』注释漏掉了模板实际使用的变量: $undocumented"
            )
            val phantom = entry.declared - CODE_VARS.getValue(entry.key)
            assertTrue(
                phantom.isEmpty(),
                "${entry.key} 的『# 变量:』注释列出了代码不传入的变量: $phantom"
            )
        }
    }
}
