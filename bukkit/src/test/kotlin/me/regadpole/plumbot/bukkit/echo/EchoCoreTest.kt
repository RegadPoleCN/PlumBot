// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.api.platform.EchoCode
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.bukkit.echo.EchoResultBuilder
import me.regadpole.plumbot.bukkit.echo.EchoSession
import me.regadpole.plumbot.bukkit.echo.EchoChannelSelector
import me.regadpole.plumbot.bukkit.echo.EchoSettings
import me.regadpole.plumbot.bukkit.echo.RconSettings
import me.regadpole.plumbot.bukkit.echo.LatestLogTail
import me.regadpole.plumbot.bukkit.echo.RconPacketCodec
import net.kyori.adventure.text.Component
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EchoSessionTest {

    @Test
    fun crossSourceDuplicatesAreSuppressedButSameSourceDuplicatesAreKept() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.logLine("log4j2", "Hello")
        session.logLine("jul", "Hello")
        session.logLine("latest_log", "Hello")
        assertEquals(listOf("Hello"), session.snapshot())

        session.line("console", "Hello")
        assertEquals(listOf("Hello"), session.snapshot())
        assertEquals(listOf("log4j2"), session.sources())
    }

    @Test
    fun sourcesAreRecordedInFirstSeenOrder() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.line("console", "one")
        session.logLine("jul", "two")
        session.logLine("latest_log", "three")
        assertEquals(listOf("console", "jul", "latest_log"), session.sources())
        assertEquals(listOf("one", "two", "three"), session.snapshot())
    }

    @Test
    fun sameSourceDuplicatesAreKept() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.line("console", "same")
        session.line("console", "same")
        assertEquals(listOf("same", "same"), session.snapshot())
    }

    @Test
    fun logPrefixIsStrippedOnlyWhenConfigured() {
        val stripped = EchoSession(0L, includeLogPrefix = false)
        stripped.logLine("jul", "[12:34:56] [Server thread/INFO]: Done")
        assertEquals(listOf("Done"), stripped.snapshot())

        val kept = EchoSession(0L, includeLogPrefix = true)
        kept.logLine("jul", "[12:34:56] [Server thread/INFO]: Done")
        assertEquals(listOf("[12:34:56] [Server thread/INFO]: Done"), kept.snapshot())
    }

    @Test
    fun auditAndSystemChatLinesAreFiltered() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.logLine("jul", "[PlumBot-Security] 远程控制台 正在执行指令: /version")
        session.logLine("log4j2", "System chat: [Server: Done]")
        session.line("console", "[PlumBot-Security] 远程控制台 执行指令: version")
        assertEquals(listOf("[Server: Done]"), session.snapshot())
    }

    @Test
    fun ansiIsConvertedToSectionCodes() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.line("console", "\u001B[31mred\u001B[0m")
        assertEquals(listOf("§cred§r"), session.snapshot())
    }

    @Test
    fun closedSessionIgnoresLateLines() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.close()
        session.line("console", "late")
        assertEquals(emptyList(), session.snapshot())
    }

    @Test
    fun componentIsQueuedWithoutProjectionUntilSnapshot() {
        val session = EchoSession(0L, includeLogPrefix = false)
        session.component("paper_feedback", Component.text("Hello from Paper"))
        assertEquals(0, session.projectedLineCountForTest(), "component 入队不得在调用线程投影")
        assertEquals(listOf("Hello from Paper"), session.snapshot(), "投影必须推迟到 snapshot")
        assertEquals(1, session.projectedLineCountForTest())
    }
}

class EchoResultBuilderTest {

    @Test
    fun exceptionWinsOverEverything() {
        val result = EchoResultBuilder.build(
            channel = "console",
            lines = listOf("partial"),
            dispatchOk = true,
            exception = IllegalStateException("boom"),
            failurePatterns = emptyList(),
            durationMs = 12L
        )
        assertEquals(EchoState.FAILED, result.state)
        assertEquals(EchoCode.ECHO_EXCEPTION, result.code)
        assertEquals(listOf("partial"), result.lines)
        assertTrue(result.detail!!.contains("boom"))
    }

    @Test
    fun failurePatternMapsToUnknownCommand() {
        val result = EchoResultBuilder.build(
            channel = "console",
            lines = listOf("Unknown command. Type /help for help."),
            dispatchOk = true,
            exception = null,
            failurePatterns = listOf("Unknown command"),
            durationMs = 1L
        )
        assertEquals(EchoState.UNKNOWN_OR_REJECTED, result.state)
        assertEquals(EchoCode.ECHO_UNKNOWN_COMMAND, result.code)
        assertEquals(1, result.lines.size)
    }

    @Test
    fun capturedWhenLinesExist() {
        val result = EchoResultBuilder.build(
            channel = "paper_feedback",
            lines = listOf("Done"),
            dispatchOk = true,
            exception = null,
            failurePatterns = emptyList(),
            durationMs = 1L
        )
        assertEquals(EchoState.CAPTURED, result.state)
        assertNull(result.code)
    }

    @Test
    fun noOutputWithDispatchTrueIsSuccessNoOutput() {
        val result = EchoResultBuilder.build(
            channel = "console",
            lines = emptyList(),
            dispatchOk = true,
            exception = null,
            failurePatterns = emptyList(),
            durationMs = 1L
        )
        assertEquals(EchoState.SUCCESS_NO_OUTPUT, result.state)
        assertEquals(EchoCode.ECHO_NO_CHANNEL, result.code)
    }

    @Test
    fun noOutputWithDispatchFalseIsRejected() {
        val result = EchoResultBuilder.build(
            channel = "console",
            lines = emptyList(),
            dispatchOk = false,
            exception = null,
            failurePatterns = emptyList(),
            durationMs = 1L
        )
        assertEquals(EchoState.UNKNOWN_OR_REJECTED, result.state)
        assertEquals(EchoCode.ECHO_DISPATCH_FALSE, result.code)
    }
}

class EchoChannelSelectorTest {

    @Test
    fun forcedOverridesConfiguredChannels() {
        val settings = settings(
            channels = listOf("console"),
            forced = listOf(" Paper_Feedback ", "paper_feedback", "")
        )
        assertEquals(listOf("paper_feedback"), EchoChannelSelector.candidates(settings))
    }

    @Test
    fun defaultChannelsAreNormalizedAndDeduplicated() {
        val settings = settings(
            channels = listOf("console", "CONSOLE", " plugin_command ", ""),
            forced = emptyList()
        )
        assertEquals(listOf("console", "plugin_command"), EchoChannelSelector.candidates(settings))
    }

    @Test
    fun unknownChannelsAreNotKnown() {
        assertTrue(EchoChannelSelector.isKnown("paper_feedback"))
        assertFalse(EchoChannelSelector.isKnown("plugin_direct"))
    }
}

class RconPacketCodecTest {

    @Test
    fun commandFrameRoundTrips() {
        val encoded = RconPacketCodec.encode(7, RconPacketCodec.TYPE_COMMAND, "say 你好")
        val decoded = RconPacketCodec.decode(encoded)
        assertEquals(7, decoded!!.id)
        assertEquals(RconPacketCodec.TYPE_COMMAND, decoded.type)
        assertEquals("say 你好", decoded.body)
    }

    @Test
    fun readFrameReadsSizePrefixedPacket() {
        val encoded = RconPacketCodec.encode(1, RconPacketCodec.TYPE_AUTH_RESPONSE, "")
        val frame = RconPacketCodec.readFrame(ByteArrayInputStream(encoded))
        assertEquals(1, frame!!.id)
        assertEquals("", frame.body)
    }

    @Test
    fun authFailureFrameKeepsNegativeId() {
        val encoded = RconPacketCodec.encode(-1, RconPacketCodec.TYPE_AUTH_RESPONSE, "bad password")
        val frame = RconPacketCodec.readFrame(ByteArrayInputStream(encoded))
        assertEquals(-1, frame!!.id)
    }

    @Test
    fun truncatedBodyThrowsEof() {
        val encoded = RconPacketCodec.encode(1, RconPacketCodec.TYPE_RESPONSE, "hello")
        val truncated = encoded.copyOf(encoded.size - 3)
        assertFailsWith<EOFException> {
            RconPacketCodec.readFrame(ByteArrayInputStream(truncated))
        }
    }

    @Test
    fun invalidSizeReturnsNull() {
        val bad = ByteArray(4) { 0xFF.toByte() }
        assertNull(RconPacketCodec.decode(bad))
    }
}

class LatestLogTailTest {

    @Test
    fun onlyReadsBytesAppendedAfterStart() {
        val dir = Files.createTempDirectory("plumbot-echo-test")
        val path = dir.resolve("latest.log")
        Files.writeString(path, "old line\n", StandardCharsets.UTF_8)
        val tail = LatestLogTail(path)
        assertTrue(tail.start())

        Files.writeString(path, "new line\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND)
        val session = EchoSession(0L, includeLogPrefix = false)
        tail.finish(session)
        assertEquals(listOf("new line"), session.snapshot())
    }

    @Test
    fun missingFileIsUnavailable() {
        val tail = LatestLogTail(Path.of("definitely-missing", "latest.log"))
        assertFalse(tail.start())
    }

    @Test
    fun finishBeforeStartIsNoOp() {
        val tail = LatestLogTail(Path.of("definitely-missing", "latest.log"))
        val session = EchoSession(0L, includeLogPrefix = false)
        tail.finish(session)
        assertEquals(emptyList(), session.snapshot())
    }
}

private fun settings(channels: List<String>, forced: List<String>): EchoSettings = EchoSettings(
    channels = channels,
    forced = forced,
    dispatchTimeoutMs = 1500L,
    captureWindowMs = 400L,
    maxCaptureMs = 2000L,
    includeLogPrefix = false,
    failurePatterns = EchoSettings.DEFAULT_FAILURE_PATTERNS,
    paperFeedback = true,
    log4j2 = true,
    jul = true,
    latestLog = true,
    nmsEnabled = false,
    proxySenderEnabled = false,
    rcon = RconSettings(false, "127.0.0.1", 25575, "", 3000),
    logCommands = false
)
