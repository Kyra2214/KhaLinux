package com.khalinux.runtime.diagnostics

import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.ProotLocation
import com.khalinux.runtime.ProotLocator
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.RuntimeSession
import com.khalinux.runtime.SessionSnapshot
import com.khalinux.runtime.SessionState
import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeDiagnosticsTest {
    private val temp = Files.createTempDirectory("khalinux-diag").toFile()
    @After fun cleanup() { temp.deleteRecursively() }

    private fun locator(found: Boolean) = object : ProotLocator {
        override fun locate() = if (found) ProotLocation(File("/fake/proot"), "teste") else null
        override fun describe() = "origem de teste"
    }

    /** Sessão falsa: comandos não interativos respondem por script; o shell interativo emula stty/echo/Ctrl+C. */
    private class FakeRuntime(
        private val responses: Map<String, Pair<Int, String>>,
        private val honorResize: Boolean = true
    ) : LinuxRuntime {
        override fun start(request: CommandRequest): RuntimeSession =
            if (request.interactive) InteractiveSession(request.initialRows, request.initialColumns, honorResize)
            else {
                val (code, out) = responses.entries.first { request.shellCommand.contains(it.key) }.value
                CommandSession(code, out)
            }
        override fun snapshot(sessionId: String): SessionSnapshot? = null
        override fun cancel(sessionId: String) = true
        override fun writeInput(sessionId: String, bytes: ByteArray) = false
        override fun resize(sessionId: String, rows: Int, columns: Int) = false
        override fun sessions(): List<SessionSnapshot> = emptyList()
        override fun close() = Unit
    }

    private abstract class BaseSession : RuntimeSession {
        override val id = "fake"
        protected val queue = LinkedBlockingQueue<RuntimeEvent>()
        override fun events(): Sequence<RuntimeEvent> = sequence {
            while (true) {
                val event = queue.take()
                yield(event)
                if (event is RuntimeEvent.Exited || event is RuntimeEvent.Failed) break
            }
        }
        override fun snapshot() = SessionSnapshot(id, SessionState.RUNNING, null, null, null, false, null)
        override fun writeInput(bytes: ByteArray) = false
        override fun resize(rows: Int, columns: Int) = false
        override fun cancel(): Boolean { queue.offer(RuntimeEvent.Exited(id, 130, false)); return true }
        override fun close() = Unit
    }

    private class CommandSession(code: Int, output: String) : BaseSession() {
        init {
            queue.offer(RuntimeEvent.Stdout(id, output.toByteArray()))
            queue.offer(RuntimeEvent.Exited(id, code, false))
        }
    }

    private class InteractiveSession(private var rows: Int, private var cols: Int, private val honorResize: Boolean) : BaseSession() {
        private var sleeping = false
        override fun writeInput(bytes: ByteArray): Boolean {
            val text = String(bytes)
            fun out(s: String) = queue.offer(RuntimeEvent.Stdout(id, s.toByteArray()))
            when {
                text.startsWith("echo PTY_READY") -> out("PTY_READY_42\n")
                text == "stty size\n" -> out("$rows $cols\n")
                text.startsWith("sleep") -> sleeping = true
                bytes.size == 1 && bytes[0] == 3.toByte() -> sleeping = false
                text.startsWith("echo CTRLC") -> if (!sleeping) out("CTRLC_42\n")
            }
            return true
        }
        override fun resize(rows: Int, columns: Int): Boolean {
            if (honorResize) { this.rows = rows; this.cols = columns }
            return true
        }
    }

    private val okResponses = mapOf(
        "uname -m" to (0 to "aarch64\n0\n"),
        "getent hosts" to (0 to "151.101.66.132 debian.org\n"),
        "curl" to (0 to "200")
    )

    private fun diagnostics(
        found: Boolean = true,
        runtime: LinuxRuntime? = FakeRuntime(okResponses),
        host: HostCommandRunner = HostCommandRunner { _, _, _ -> ProbeResult(0, "proot 5.1.107\n") }
    ) = RuntimeDiagnostics(locator(found), { runtime }, temp, host, commandTimeoutMillis = 5000, ptyStepTimeoutMillis = 3000)

    @Test(timeout = 30000) fun allAutomaticChecksPassAndManualOnesWait() {
        val report = diagnostics().run()
        listOf(DiagnosticId.PROOT_EXEC, DiagnosticId.ROOTFS_SHELL, DiagnosticId.DNS, DiagnosticId.NETWORK, DiagnosticId.PTY)
            .forEach { assertEquals(DiagnosticStatus.OK, report.item(it).status, "${it}: ${report.item(it).detail}") }
        assertEquals(DiagnosticStatus.MANUAL, report.item(DiagnosticId.ROTATION).status)
        assertEquals(DiagnosticStatus.MANUAL, report.item(DiagnosticId.BACKGROUND).status)
        assertTrue(report.automaticChecksPassed && report.isFinished)
        assertTrue(report.toText().contains("PRoot"))
    }

    @Test fun missingProotSkipsDependents() {
        val report = diagnostics(found = false).run()
        assertEquals(DiagnosticStatus.FAILED, report.item(DiagnosticId.PROOT_EXEC).status)
        assertTrue(report.item(DiagnosticId.PROOT_EXEC).detail!!.contains("origem de teste"))
        assertEquals(DiagnosticStatus.SKIPPED, report.item(DiagnosticId.PTY).status)
    }

    @Test fun prootThatCannotExecuteMentionsPlanB() {
        val report = diagnostics(host = HostCommandRunner { _, _, _ -> ProbeResult(null, "", error = "error=13, Permission denied") }).run()
        val item = report.item(DiagnosticId.PROOT_EXEC)
        assertEquals(DiagnosticStatus.FAILED, item.status)
        assertTrue(item.detail!!.contains("jniLibs"))
        assertEquals(DiagnosticStatus.SKIPPED, report.item(DiagnosticId.DNS).status)
    }

    @Test fun wrongArchitectureAndDnsFailureAreReported() {
        val runtime = FakeRuntime(
            mapOf(
                "uname -m" to (0 to "x86_64\n0\n"),
                "getent hosts" to (1 to "--- /etc/resolv.conf\ncat: no such file\n"),
                "curl" to (3 to "NO_CURL\n")
            )
        )
        val report = diagnostics(runtime = runtime).run()
        assertEquals(DiagnosticStatus.FAILED, report.item(DiagnosticId.ROOTFS_SHELL).status)
        assertEquals(DiagnosticStatus.FAILED, report.item(DiagnosticId.DNS).status)
        assertEquals(DiagnosticStatus.SKIPPED, report.item(DiagnosticId.NETWORK).status)
        assertTrue(!report.automaticChecksPassed)
    }

    @Test(timeout = 30000) fun ptyResizeNotReachingShellFails() {
        val report = diagnostics(runtime = FakeRuntime(okResponses, honorResize = false)).run()
        assertEquals(DiagnosticStatus.FAILED, report.item(DiagnosticId.PTY).status)
    }

    @Test fun noRuntimeSkipsGuestChecks() {
        val report = diagnostics(runtime = null).run()
        assertEquals(DiagnosticStatus.OK, report.item(DiagnosticId.PROOT_EXEC).status)
        assertEquals(DiagnosticStatus.SKIPPED, report.item(DiagnosticId.ROOTFS_SHELL).status)
    }
}
