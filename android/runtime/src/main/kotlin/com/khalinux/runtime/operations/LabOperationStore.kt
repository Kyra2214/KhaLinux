package com.khalinux.runtime.operations

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

/** Persistência local atômica de sessões, execuções e eventos do laboratório. */
class LabOperationStore(private val root: File) {
    private val sessionsDir = File(root, "sessions")
    private val executionsDir = File(root, "executions")
    private val eventsDir = File(root, "events")

    @Synchronized fun saveSession(value: LabSession) {
        val p = Properties().apply {
            setProperty("id", value.id); setProperty("label", value.label)
            setProperty("assetIds", pack(value.assetIds)); setProperty("state", value.state.name)
            setProperty("createdAt", value.createdAtEpochMillis.toString())
            value.closedAtEpochMillis?.let { setProperty("closedAt", it.toString()) }
        }
        write(sessionsDir, value.id, p)
    }

    @Synchronized fun session(id: String): LabSession? = read(sessionsDir, id)?.let { p ->
        runCatching {
            LabSession(p.getProperty("id"), p.getProperty("label"), unpack(p.getProperty("assetIds", "")).toSet(),
                LabSessionState.valueOf(p.getProperty("state")), p.getProperty("createdAt").toLong(),
                p.getProperty("closedAt")?.toLong())
        }.getOrNull()
    }

    @Synchronized fun sessions(): List<LabSession> =
        files(sessionsDir).mapNotNull { session(it.nameWithoutExtension) }.sortedByDescending { it.createdAtEpochMillis }

    @Synchronized fun saveExecution(value: LabExecutionRecord) {
        val p = Properties().apply {
            setProperty("id", value.id); setProperty("sessionId", value.sessionId)
            setProperty("operationId", value.operationId); setProperty("operationVersion", value.operationVersion.toString())
            setProperty("assetIds", pack(value.assetIds)); setProperty("state", value.state.name)
            setProperty("queuedAt", value.queuedAtEpochMillis.toString())
            value.startedAtEpochMillis?.let { setProperty("startedAt", it.toString()) }
            value.endedAtEpochMillis?.let { setProperty("endedAt", it.toString()) }
            value.exitCode?.let { setProperty("exitCode", it.toString()) }
            value.errorSummary?.let { setProperty("errorSummary", it) }
            setProperty("evidenceIds", pack(value.evidenceIds))
        }
        write(executionsDir, value.id, p)
    }

    @Synchronized fun execution(id: String): LabExecutionRecord? = read(executionsDir, id)?.let { p ->
        runCatching {
            LabExecutionRecord(p.getProperty("id"), p.getProperty("sessionId"), p.getProperty("operationId"),
                p.getProperty("operationVersion").toInt(), unpack(p.getProperty("assetIds", "")).toSet(),
                LabExecutionState.valueOf(p.getProperty("state")), p.getProperty("queuedAt").toLong(),
                p.getProperty("startedAt")?.toLong(), p.getProperty("endedAt")?.toLong(),
                p.getProperty("exitCode")?.toInt(), p.getProperty("errorSummary"),
                unpack(p.getProperty("evidenceIds", "")))
        }.getOrNull()
    }

    @Synchronized fun executions(sessionId: String? = null): List<LabExecutionRecord> =
        files(executionsDir).mapNotNull { execution(it.nameWithoutExtension) }
            .filter { sessionId == null || it.sessionId == sessionId }
            .sortedByDescending { it.queuedAtEpochMillis }

    @Synchronized fun saveEvent(value: LabEventRecord) {
        val p = Properties().apply {
            setProperty("id", value.id); setProperty("sessionId", value.sessionId)
            value.executionId?.let { setProperty("executionId", it) }
            value.assetId?.let { setProperty("assetId", it) }
            setProperty("eventType", value.eventType); setProperty("severity", value.severity.name)
            setProperty("occurredAt", value.occurredAtEpochMillis.toString())
            setProperty("source", value.source); setProperty("summary", value.summary)
            setProperty("evidenceIds", pack(value.evidenceIds))
        }
        write(eventsDir, value.id, p)
    }

    @Synchronized fun events(sessionId: String): List<LabEventRecord> =
        files(eventsDir).mapNotNull { file ->
            read(eventsDir, file.nameWithoutExtension)?.let { p ->
                runCatching {
                    LabEventRecord(p.getProperty("id"), p.getProperty("sessionId"), p.getProperty("executionId"),
                        p.getProperty("assetId"), p.getProperty("eventType"),
                        LabEventSeverity.valueOf(p.getProperty("severity")), p.getProperty("occurredAt").toLong(),
                        p.getProperty("source"), p.getProperty("summary"), unpack(p.getProperty("evidenceIds", "")))
                }.getOrNull()
            }
        }.filter { it.sessionId == sessionId }.sortedBy { it.occurredAtEpochMillis }

    private fun write(directory: File, id: String, properties: Properties) {
        require(SAFE_ID.matches(id)) { "Id persistido inválido" }
        directory.mkdirs()
        val target = File(directory, "$id.properties")
        val temporary = File(directory, ".$id.tmp")
        FileOutputStream(temporary).use { output -> properties.store(output, null); output.fd.sync() }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { if (temporary.exists()) temporary.delete() }
    }

    private fun read(directory: File, id: String): Properties? {
        if (!SAFE_ID.matches(id)) return null
        val file = File(directory, "$id.properties")
        if (!file.isFile) return null
        return runCatching { Properties().also { p -> FileInputStream(file).use(p::load) } }.getOrNull()
    }

    private fun files(directory: File): List<File> =
        directory.listFiles()?.filter { it.isFile && it.extension == "properties" }.orEmpty()

    private fun pack(values: Collection<String>): String = values.joinToString(".") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
    }
    private fun unpack(value: String): List<String> = value.split('.').filter { it.isNotEmpty() }.map {
        String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
    }

    private companion object { val SAFE_ID = Regex("^[A-Za-z0-9-]{1,80}$") }
}
