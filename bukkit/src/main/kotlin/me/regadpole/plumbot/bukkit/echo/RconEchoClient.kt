// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

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
