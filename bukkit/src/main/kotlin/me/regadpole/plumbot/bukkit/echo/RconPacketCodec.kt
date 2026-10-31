// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

internal data class RconFrame(val id: Int, val type: Int, val body: String)

/**
 * Minecraft RCON 协议的最小实现（小端）：length(4) id(4) type(4) body + 两个 NUL。
 * 抽成 object 是为了脱离网络做编解码单测（含截断包与认证失败 id=-1）。
 */
internal object RconPacketCodec {

    const val TYPE_RESPONSE = 0
    const val TYPE_COMMAND = 2
    const val TYPE_AUTH = 3
    const val TYPE_AUTH_RESPONSE = 2

    private const val MIN_SIZE = 10
    private const val MAX_SIZE = 1024 * 1024

    fun encode(id: Int, type: Int, body: String): ByteArray {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val size = 4 + 4 + payload.size + 2
        val buffer = ByteBuffer.allocate(4 + size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(size)
        buffer.putInt(id)
        buffer.putInt(type)
        buffer.put(payload)
        buffer.put(0)
        buffer.put(0)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): RconFrame? {
        if (bytes.size < 4 + MIN_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val size = buffer.int
        if (size < MIN_SIZE || size > MAX_SIZE || size + 4 > bytes.size) return null
        return decodeBody(buffer, size)
    }

    fun readFrame(input: InputStream): RconFrame? {
        val header = ByteArray(4)
        if (!readFully(input, header)) return null
        val size = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
        if (size < MIN_SIZE || size > MAX_SIZE) throw IOException("非法 RCON 包长度: " + size)
        val payload = ByteArray(size)
        if (!readFully(input, payload)) throw EOFException("RCON 包被截断")
        return decodeBody(ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN), size)
    }

    private fun decodeBody(buffer: ByteBuffer, size: Int): RconFrame? {
        if (buffer.remaining() < 8) return null
        val id = buffer.int
        val type = buffer.int
        val bodyLength = size - MIN_SIZE
        if (bodyLength < 0 || buffer.remaining() < bodyLength + 2) return null
        val body = ByteArray(bodyLength)
        buffer.get(body)
        buffer.short
        return RconFrame(id, type, String(body, StandardCharsets.UTF_8))
    }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }
}

/**
 * 远程控制台 RCON 客户端。
 *
 * 只在专用线程调用；连接、认证、执行、收包全部有超时。任何异常都转成 [RconOutcome.Error]，
 * 密码永远不进入 detail/日志。
 */
