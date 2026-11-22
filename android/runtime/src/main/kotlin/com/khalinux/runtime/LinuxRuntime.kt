package com.khalinux.runtime

import java.io.Closeable
import java.time.Duration

/** Contrato que desacopla o runtime Linux da UI Android. */
interface LinuxRuntime : Closeable {
    fun start(request: CommandRequest): RuntimeSession
    fun snapshot(sessionId: String): SessionSnapshot?
    fun cancel(sessionId: String): Boolean
    fun writeInput(sessionId: String, bytes: ByteArray): Boolean
    fun resize(sessionId: String, rows: Int, columns: Int): Boolean
    fun sessions(): List<SessionSnapshot>
}

data class CommandRequest(
    /** Texto destinado ao Bash dentro do RootFS; nunca é executado por um shell do host. */
    val shellCommand: String,
    val workingDirectory: String = "/home/khalinux",
    val environment: Map<String, String> = emptyMap(),
    val timeout: Duration? = null,
    val interactive: Boolean = false,
    val initialRows: Int = 24,
    val initialColumns: Int = 80
) {
    init {
        require(interactive || shellCommand.isNotBlank()) {
            "shellCommand não pode ser vazio para uma execução não interativa"
        }
        require(workingDirectory.startsWith("/")) { "workingDirectory deve ser absoluto no RootFS" }
        require(timeout == null || !timeout.isNegative && !timeout.isZero) {
            "timeout deve ser positivo"
        }
        require(initialRows in 1..65535) { "initialRows fora do intervalo suportado" }
        require(initialColumns in 1..65535) { "initialColumns fora do intervalo suportado" }
    }
}

data class SessionSnapshot(
    val id: String,
    val state: SessionState,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val exitCode: Int?,
    val timedOut: Boolean,
    val errorMessage: String?
)

enum class SessionState { CREATED, RUNNING, CANCELLING, EXITED, FAILED, CANCELLED }

sealed class RuntimeEvent {
    data class Started(val sessionId: String) : RuntimeEvent()
    data class Stdout(val sessionId: String, val bytes: ByteArray) : RuntimeEvent()
    data class Stderr(val sessionId: String, val bytes: ByteArray) : RuntimeEvent()
    data class Exited(val sessionId: String, val exitCode: Int, val timedOut: Boolean) : RuntimeEvent()
    data class Failed(val sessionId: String, val message: String) : RuntimeEvent()
}

interface RuntimeSession : Closeable {
    val id: String
    fun events(): Sequence<RuntimeEvent>
    fun snapshot(): SessionSnapshot
    fun writeInput(bytes: ByteArray): Boolean
    fun resize(rows: Int, columns: Int): Boolean
    fun cancel(): Boolean
}
