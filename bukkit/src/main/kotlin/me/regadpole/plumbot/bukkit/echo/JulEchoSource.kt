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

package me.regadpole.plumbot.bukkit.echo

import org.bukkit.Bukkit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter

/**
 * 通过 Bukkit 的 java.util.logging.Logger 旁路捕获控制台日志。
 *
 * 旧版 Spigot（没有 log4j2 core）或 log4j2 反射失败时这是最可靠的兜底：只挂在 Bukkit.getLogger()
 * （通常名为 Minecraft）上，不改 root logger 配置，finish 时精确移除自己。
 */
internal class JulEchoSource(
    private val session: EchoSession
) : Handler(), EchoSource {

    override val name: String = SOURCE

    private val formatter = SimpleFormatter()
    private var attached = false

    init {
        level = Level.ALL
    }

    override fun start(): Boolean {
        if (attached) return true
        return runCatching {
            Bukkit.getLogger().addHandler(this)
            attached = true
            true
        }.getOrDefault(false)
    }

    override fun finish(session: EchoSession) {
        if (!attached) return
        attached = false
        runCatching { Bukkit.getLogger().removeHandler(this) }
    }

    override fun publish(record: LogRecord) {
        if (!attached || session.isClosed) return
        if (record.millis < session.startMillis - START_SLACK_MS) return
        val text = runCatching { formatter.formatMessage(record) }
            .getOrElse { record.message ?: return }
        session.logLine(SOURCE, text)
    }

    override fun flush() = Unit

    override fun close() = Unit

    companion object {
        private const val SOURCE = "jul"
        private const val START_SLACK_MS = 20L
    }
}