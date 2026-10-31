// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo.logsource

import me.regadpole.plumbot.bukkit.echo.capture.EchoSession
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
