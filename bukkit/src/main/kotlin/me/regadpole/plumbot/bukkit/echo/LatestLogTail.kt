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

import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 增量读取 logs/latest.log：start 记下文件长度，finish 只读其后追加的字节，
 * 避免把几十 MB 日志全量读进内存；文件被轮转或截断时 offset 归零重读。
 * 任何 IO 问题只让该源不可用，不影响结果状态。
 */
internal class LatestLogTail(
    private val path: Path = Path.of("logs", "latest.log")
) : EchoSource {

    override val name: String = SOURCE

    private var offset = -1L

    override fun start(): Boolean {
        return runCatching {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) return false
            offset = Files.size(path)
            true
        }.getOrDefault(false)
    }

    override fun finish(session: EchoSession) {
        if (offset < 0) return
        runCatching { readNewLines(session) }
    }

    private fun readNewLines(session: EchoSession) {
        RandomAccessFile(path.toFile(), "r").use { file ->
            var length = file.length()
            if (length < offset) offset = 0L
            val remaining = length - offset
            if (remaining <= 0L) return
            val toRead = minOf(remaining, MAX_READ_BYTES.toLong()).toInt()
            val bytes = ByteArray(toRead)
            file.seek(offset)
            file.readFully(bytes)
            offset += toRead
            String(bytes, StandardCharsets.UTF_8)
                .split('\n')
                .forEach { raw -> session.logLine(name, raw) }
        }
    }

    companion object {
        private const val SOURCE = "latest_log"
        private const val MAX_READ_BYTES = 1024 * 1024
    }
}
