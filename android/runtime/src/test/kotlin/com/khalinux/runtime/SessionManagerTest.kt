package com.khalinux.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {
    private val rootfs = Files.createTempDirectory("khalinux-test-rootfs").toFile().apply {
        File(this, "bin/bash").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\\n")
        }
    }
    private val proot = File("/bin/true")

    @After
    fun cleanup() {
        rootfs.deleteRecursively()
    }

    private fun config(): ProotConfig =
        ProotConfig(prootExecutable = proot, rootfsDirectory = rootfs)

    private fun launcher(): ProotLauncher = ProotLauncher(
        config(),
        processLauncher = object : ProcessLauncher {
            override fun launch(arguments: List<String>, processEnvironment: Map<String, String>): Process {
                require(arguments.isNotEmpty())
                return ProcessBuilder("/bin/bash", "--noprofile", "--norc", "-lc", arguments.last())
                    .apply {
                        environment().clear()
                        environment().putAll(processEnvironment)
                        environment().put("PATH", "/usr/bin:/bin")
                    }
                    .start()
            }
        }
    )

    private fun runtime(): SessionManager = SessionManager(launcher(), maxSessions = 2)

    @Test fun commandIsPassedAsSingleGuestShellArgument() {
        val args = config().commandFor(CommandRequest("printf '%s' 'hello;not-host'"))
        assertEquals("printf '%s' 'hello;not-host'", args.last())
        assertEquals("/bin/bash", args[args.size - 5])
    }

    @Test fun invalidEnvironmentNameIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            config().commandFor(CommandRequest("true", environment = mapOf("BAD=KEY" to "x")))
        }
    }

    @Test fun interactiveSessionUsesGuestBashAndConservativeTerminalEnvironment() {
        val args = config().commandFor(CommandRequest("", interactive = true))
        assertEquals(listOf("/bin/bash", "--noprofile", "--norc", "-i"), args.takeLast(4))
        assertTrue(args.contains("TERM=dumb"))
        assertTrue(args.contains("PS1=khalinux:\\w\\$ "))
    }

    @Test fun sessionCapturesStdoutAndExitCode() {
        val runtime = runtime()
        try {
            val session = runtime.start(CommandRequest("printf 'hello'; exit 7"))
            val events = session.events().toList()
            val output = events.filterIsInstance<RuntimeEvent.Stdout>()
                .joinToString("") { String(it.bytes, Charsets.UTF_8) }
            assertTrue(output.startsWith("hello"))
            assertTrue(events.any { it is RuntimeEvent.Exited && it.exitCode == 7 })
            assertEquals(SessionState.EXITED, session.snapshot().state)
        } finally {
            runtime.close()
        }
    }

    @Test fun activeSessionLimitRejectsAdditionalWorkUntilCompletion() {
        val runtime = SessionManager(launcher(), maxSessions = 1)
        try {
            val first = runtime.start(CommandRequest("exec sleep 5"))
            assertFailsWith<IllegalStateException> {
                runtime.start(CommandRequest("true"))
            }

            assertTrue(first.cancel())
            first.events().toList()

            val next = runtime.start(CommandRequest("true"))
            next.events().toList()
            assertEquals(SessionState.EXITED, next.snapshot().state)
        } finally {
            runtime.close()
        }
    }

    @Test fun completedSessionsReleaseActiveSessionCapacity() {
        val runtime = SessionManager(launcher(), maxSessions = 1)
        try {
            val first = runtime.start(CommandRequest("true"))
            first.events().toList()
            assertEquals(SessionState.EXITED, first.snapshot().state)

            val second = runtime.start(CommandRequest("true"))
            second.events().toList()
            assertEquals(SessionState.EXITED, second.snapshot().state)
        } finally {
            runtime.close()
        }
    }

    @Test fun completedSessionHistoryRemainsBounded() {
        val runtime = SessionManager(launcher(), maxSessions = 1, maxRetainedSessions = 1)
        try {
            val first = runtime.start(CommandRequest("true"))
            first.events().toList()

            val second = runtime.start(CommandRequest("true"))
            second.events().toList()

            assertNull(runtime.snapshot(first.id))
            assertNotNull(runtime.snapshot(second.id))
            assertEquals(1, runtime.sessions().size)
        } finally {
            runtime.close()
        }
    }

    @Test fun processGroupCancellationTerminatesShellDescendants() {
        val setsid = listOf(File("/usr/bin/setsid"), File("/bin/setsid"))
            .firstOrNull { it.canExecute() }
        assumeTrue("setsid ausente no host", setsid != null)
        val kill = File("/usr/bin/kill")
        assumeTrue("utilitário kill ausente no host", kill.canExecute())

        val pidFile = Files.createTempFile("khalinux-process-group-", ".pid").toFile()
        val wrapperScript = "printf '%s\\n' \"\$\$\" > \"\$1\"; shift; exec \"\$@\""
        val child = ProcessBuilder(
            setsid!!.absolutePath,
            "-w",
            "/bin/sh",
            "-c",
            wrapperScript,
            "khalinux-process-group",
            pidFile.absolutePath,
            "/bin/bash",
            "--noprofile",
            "--norc",
            "-lc",
            "sleep 10 & echo $!; wait"
        ).start()
        try {
            val deadlineForPid = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            var processGroupId: Int? = null
            while (System.nanoTime() < deadlineForPid && processGroupId == null) {
                processGroupId = runCatching { pidFile.readText().trim().toInt() }.getOrNull()
                if (processGroupId == null && child.isAlive) Thread.sleep(10)
            }
            val resolvedGroupId = requireNotNull(processGroupId) { "wrapper não publicou o PID do grupo" }
            val descendantPid = child.inputStream.bufferedReader().readLine().trim().toInt()
            val managed = ProcessGroupManagedProcess(child, resolvedGroupId) { pid, force ->
                val signal = if (force) "KILL" else "TERM"
                ProcessBuilder(kill.absolutePath, "-$signal", "--", "-$pid")
                    .start()
                    .waitFor() == 0
            }

            fun descendantIsActive(): Boolean {
                val stat = File("/proc/$descendantPid/stat")
                if (!stat.isFile) return false
                val state = stat.readText().substringAfterLast(") ").firstOrNull()
                return state != null && state != 'Z' && state != 'X'
            }

            managed.destroy()
            assertTrue("processo/grupo não encerrou no prazo", managed.waitFor(3, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (descendantIsActive() && System.nanoTime() < deadline) {
                Thread.sleep(25)
            }
            assertTrue("processo descendente sobreviveu ao cancelamento", !descendantIsActive())
        } finally {
            pidFile.delete()
        }
    }

    @Test fun launchFailureReleasesActiveSessionCapacity() {
        val brokenLauncher = ProotLauncher(
            config(),
            processLauncher = object : ProcessLauncher {
                override fun launch(arguments: List<String>, processEnvironment: Map<String, String>): Process {
                    throw IOException("simulated process launch failure")
                }
            }
        )
        val runtime = SessionManager(brokenLauncher, maxSessions = 1)
        try {
            repeat(2) {
                assertFailsWith<IOException> {
                    runtime.start(CommandRequest("true"))
                }
            }
        } finally {
            runtime.close()
        }
    }

    @Test fun timeoutCancelsLongRunningSession() {
        val runtime = runtime()
        try {
            val session = runtime.start(CommandRequest("exec sleep 5", timeout = Duration.ofMillis(100)))
            session.events().toList()
            assertTrue(session.snapshot().timedOut)
            assertEquals(SessionState.CANCELLED, session.snapshot().state)
        } finally {
            runtime.close()
        }
    }
}
