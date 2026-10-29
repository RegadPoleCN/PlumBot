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

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/** RCON 一次执行的归一化结果。 */
internal sealed interface RconOutcome {
    data class Success(val lines: List<String>) : RconOutcome
    data class AuthFailed(val detail: String) : RconOutcome
    data class Error(val detail: String) : RconOutcome
}

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
internal class RconEchoClient(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val timeoutMs: Int
) {

    fun execute(command: String): RconOutcome {
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS)
                socket.connect(InetSocketAddress(host, port), timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS))
                val output = socket.getOutputStream()
                val input = socket.getInputStream()

                output.write(RconPacketCodec.encode(1, RconPacketCodec.TYPE_AUTH, password))
                output.flush()
                when (readAuth(input)) {
                    AuthResult.Failed -> return RconOutcome.AuthFailed("RCON 认证失败（密码或端口错误）")
                    AuthResult.Timeout -> return RconOutcome.Error("RCON 认证超时")
                    AuthResult.Ok -> Unit
                }

                output.write(RconPacketCodec.encode(2, RconPacketCodec.TYPE_COMMAND, command))
                output.flush()
                RconOutcome.Success(readResponse(socket, input))
            }
        } catch (t: Throwable) {
            RconOutcome.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun readAuth(input: InputStream): AuthResult {
        repeat(MAX_AUTH_FRAMES) {
            val frame = try {
                RconPacketCodec.readFrame(input) ?: return AuthResult.Timeout
            } catch (_: SocketTimeoutException) {
                return AuthResult.Timeout
            } catch (_: Throwable) {
                return AuthResult.Timeout
            }
            if (frame.id == -1) return AuthResult.Failed
            if (frame.id == 1) return AuthResult.Ok
        }
        return AuthResult.Timeout
    }

    private fun readResponse(socket: Socket, input: InputStream): List<String> {
        val lines = ArrayList<String>()
        var first = true
        while (true) {
            if (!first) socket.soTimeout = RESPONSE_IDLE_TIMEOUT_MS
            val frame = try {
                RconPacketCodec.readFrame(input) ?: break
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: Throwable) {
                break
            }
            first = false
            if (frame.type != RconPacketCodec.TYPE_RESPONSE) continue
            frame.body.split('\n').forEach { raw ->
                val line = raw.trim()
                if (line.isNotEmpty()) lines += line
            }
        }
        return lines
    }

    private sealed interface AuthResult {
        data object Ok : AuthResult
        data object Failed : AuthResult
        data object Timeout : AuthResult
    }

    companion object {
        private const val MIN_TIMEOUT_MS = 500
        private const val RESPONSE_IDLE_TIMEOUT_MS = 200
        private const val MAX_AUTH_FRAMES = 3
    }
}