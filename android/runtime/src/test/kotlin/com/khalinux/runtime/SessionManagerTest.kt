package com.khalinux.runtime

import java.io.File
import java.time.Duration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionManagerTest {
    private val rootfs = File("distribution/build/output/rootfs-arm64")
    private val proot = File("/usr/bin/proot")

    private fun runtime(): SessionManager = SessionManager(
        ProotLauncher(ProotConfig(proot, rootfs, File("/usr/bin/qemu-aarch64-static"))),
        maxSessions = 2
    )

    @Test fun commandIsPassedAsSingleGuestShellArgument() {
        val config = ProotConfig(proot, rootfs, File("/usr/bin/qemu-aarch64-static"))
        val args = config.commandFor(CommandRequest("printf '%s' 'hello;not-host'"))
        assertEquals("printf '%s' 'hello;not-host'", args.last())
        assertEquals("/bin/bash", args[args.size - 5])
    }

    @Test fun invalidEnvironmentNameIsRejected() {
        val config = ProotConfig(proot, rootfs, File("/usr/bin/qemu-aarch64-static"))
        assertFailsWith<IllegalArgumentException> {
            config.commandFor(CommandRequest("true", environment = mapOf("BAD=KEY" to "x")))
        }
    }

    @Test fun interactiveSessionUsesGuestBashAndConservativeTerminalEnvironment() {
        val config = ProotConfig(proot, rootfs, File("/usr/bin/qemu-aarch64-static"))
        val args = config.commandFor(CommandRequest("", interactive = true))
        assertEquals(listOf("/bin/bash", "--noprofile", "--norc", "-i"), args.takeLast(4))
        assertTrue(args.contains("TERM=dumb"))
        assertTrue(args.contains("PS1=khalinux:\\w\\$ "))
    }

    @Test fun sessionCapturesStdoutAndExitCode() {
        val runtime = runtime()
        try {
            val session = runtime.start(CommandRequest("printf 'hello'; exit 7"))
            val events = session.events().toList()
            val output = events.filterIsInstance<RuntimeEvent.Stdout>().joinToString("") { String(it.bytes, Charsets.UTF_8) }
            assertTrue(output.startsWith("hello"))
            assertTrue(events.any { it is RuntimeEvent.Exited && it.exitCode == 7 })
            assertEquals(SessionState.EXITED, session.snapshot().state)
        } finally { runtime.close() }
    }

    @Test fun activeSessionLimitRejectsAdditionalWorkUntilCompletion() {
        val runtime = SessionManager(
            ProotLauncher(ProotConfig(proot, rootfs, File("/usr/bin/qemu-aarch64-static"))),
            maxSessions = 1
        )
        try {
            val first = runtime.start(CommandRequest("sleep 5"))
            assertFailsWith<IllegalStateException> {
                runtime.start(CommandRequest("true"))
            }

            assertTrue(first.cancel())
            first.events().toList()

            val next = runtime.start(CommandRequest("true"))
            next.events().toList()
            assertEquals(SessionState.EXITED, next.snapshot().state)
        } finally { runtime.close() }
    }

    @Test fun completedSessionsReleaseActiveSessionCapacity() {
        val runtime = SessionManager(
            ProotLauncher(ProotConfig(proot, rootfs, File("/usr/bin/qemu-aarch64-static"))),
            maxSessions = 1
        )
        try {
            val first = runtime.start(CommandRequest("true"))
            first.events().toList()
            assertEquals(SessionState.EXITED, first.snapshot().state)

            val second = runtime.start(CommandRequest("true"))
            second.events().toList()
            assertEquals(SessionState.EXITED, second.snapshot().state)
        } finally { runtime.close() }
    }

    @Test fun timeoutCancelsLongRunningSession() {
        val runtime = runtime()
        try {
            val session = runtime.start(CommandRequest("sleep 5", timeout = Duration.ofMillis(100)))
            session.events().toList()
            assertTrue(session.snapshot().timedOut)
            assertEquals(SessionState.CANCELLED, session.snapshot().state)
        } finally { runtime.close() }
    }
}
