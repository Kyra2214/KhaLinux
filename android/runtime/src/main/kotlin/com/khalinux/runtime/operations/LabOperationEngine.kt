package com.khalinux.runtime.operations

import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.RuntimeSession
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orquestrador LAB-1 para operações ROOTFS_ARGV declaradas no catálogo.
 * Cada argumento é escapado individualmente antes de chegar ao Bash do RootFS.
 */
class LabOperationEngine(
    private val runtime: LinuxRuntime,
    private val catalog: LabOperationCatalog,
    private val store: LabOperationStore,
    private val evidence: LabEvidenceStore,
    maxConcurrent: Int = 2,
    maxQueued: Int = 32,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxOutputBytes: Int = 2 * 1024 * 1024
) : Closeable {
    private class JobControl {
        val cancellationRequested = AtomicBoolean(false)
        @Volatile var session: RuntimeSession? = null
        @Volatile var future: FutureTask<Unit>? = null
        val finished = CountDownLatch(1)
    }

    private class OutputBuffer(private val limit: Int) {
        private val output = ByteArrayOutputStream(minOf(limit, 8192))
        @Volatile var truncated: Boolean = false
            private set
        @Synchronized fun append(bytes: ByteArray) {
            val remaining = limit - output.size()
            if (remaining <= 0) { truncated = true; return }
            val count = minOf(remaining, bytes.size)
            output.write(bytes, 0, count)
            if (count < bytes.size) truncated = true
        }
        @Synchronized fun bytes(): ByteArray = output.toByteArray()
    }

    private val closed = AtomicBoolean(false)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, JobControl>()
    private val listeners = CopyOnWriteArrayList<(LabEventRecord) -> Unit>()
    private val executor = ThreadPoolExecutor(
        maxConcurrent, maxConcurrent, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(maxQueued),
        { task -> Thread(task, "khalinux-lab-operation").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    init {
        require(maxConcurrent > 0 && maxQueued > 0 && maxOutputBytes > 0)
        recoverInterruptedState()
    }

    fun addEventListener(listener: (LabEventRecord) -> Unit): Closeable {
        listeners.add(listener)
        return Closeable { listeners.remove(listener) }
    }

    @Synchronized
    fun createSession(label: String, assetIds: Set<String> = emptySet()): LabSession {
        check(!closed.get()) { "motor de operações encerrado" }
        val session = LabSession(
            UUID.randomUUID().toString(), label.trim().take(100).ifBlank { "Laboratório" },
            assetIds.toSet(), LabSessionState.ACTIVE, clock()
        )
        store.saveSession(session)
        publish(session.id, null, null, "session.created", LabEventSeverity.INFO, "Sessão de laboratório criada")
        return session
    }

    @Synchronized
    fun closeSession(sessionId: String): Boolean {
        val current = store.session(sessionId) ?: return false
        if (current.state != LabSessionState.ACTIVE) return false
        jobs.keys.filter { store.execution(it)?.sessionId == sessionId }.forEach { cancel(it) }
        store.saveSession(current.copy(state = LabSessionState.CLOSED, closedAtEpochMillis = clock()))
        publish(sessionId, null, null, "session.closed", LabEventSeverity.INFO, "Sessão de laboratório encerrada")
        return true
    }

    fun submit(
        sessionId: String,
        operationId: String,
        assetIds: Set<String> = emptySet(),
        parameters: Map<String, String> = emptyMap()
    ): LabExecutionRecord {
        check(!closed.get()) { "motor de operações encerrado" }
        val session = store.session(sessionId) ?: throw IllegalArgumentException("Sessão desconhecida")
        require(session.state == LabSessionState.ACTIVE) { "Sessão não está ativa" }
        val operation = catalog.find(operationId) ?: throw IllegalArgumentException("Operação desconhecida: $operationId")
        require(operation.enabled) { "Operação desabilitada: $operationId" }
        require(operation.executor == LabExecutorKind.ROOTFS_ARGV && operation.targetKind == LabTargetKind.LOCAL_RUNTIME) {
            "Executor " + operation.executor + " ainda não foi conectado ao motor LAB-1"
        }
        val targets = if (assetIds.isEmpty()) session.assetIds else assetIds
        require(session.assetIds.containsAll(targets)) { "Ativo não pertence à sessão" }
        val command = resolveCommand(operation, parameters)
        val queued = LabExecutionRecord(
            id = UUID.randomUUID().toString(), sessionId = sessionId, operationId = operation.id,
            operationVersion = operation.version, assetIds = targets, state = LabExecutionState.QUEUED,
            queuedAtEpochMillis = clock()
        )
        store.saveExecution(queued)
        publish(sessionId, queued.id, targets.singleOrNull(), "operation.queued", LabEventSeverity.INFO,
            "Operação " + operation.id + " adicionada à fila")
        val control = JobControl()
        jobs[queued.id] = control
        val task = FutureTask<Unit> { execute(control, operation, queued, command) }
        control.future = task
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            jobs.remove(queued.id)
            control.finished.countDown()
            val failed = queued.copy(state = LabExecutionState.FAILED, endedAtEpochMillis = clock(),
                errorSummary = "Fila de operações cheia")
            store.saveExecution(failed)
            publish(sessionId, queued.id, targets.singleOrNull(), "operation.rejected",
                LabEventSeverity.MEDIUM, "Fila de operações cheia")
            return failed
        }
        return queued
    }

    fun cancel(executionId: String): Boolean {
        val control = jobs[executionId] ?: return false
        if (!control.cancellationRequested.compareAndSet(false, true)) return false
        val process = control.session
        if (process != null) {
            process.cancel()
        } else {
            val task = control.future
            if (task != null && task.cancel(false)) {
                val current = store.execution(executionId)
                if (current != null && current.state == LabExecutionState.QUEUED) {
                    store.saveExecution(current.copy(state = LabExecutionState.CANCELLED, endedAtEpochMillis = clock()))
                    publish(current.sessionId, current.id, current.assetIds.singleOrNull(),
                        "operation.cancelled", LabEventSeverity.INFO, "Operação cancelada na fila")
                    jobs.remove(executionId)
                    control.finished.countDown()
                }
            }
        }
        return true
    }

    fun operations(): List<LabOperationDefinition> = catalog.operations
    fun operation(id: String): LabOperationDefinition? = catalog.find(id)
    fun session(id: String): LabSession? = store.session(id)
    fun sessions(): List<LabSession> = store.sessions()
    fun execution(id: String): LabExecutionRecord? = store.execution(id)
    fun executions(sessionId: String? = null): List<LabExecutionRecord> = store.executions(sessionId)
    fun events(sessionId: String): List<LabEventRecord> = store.events(sessionId)
    fun evidenceForExecution(executionId: String): List<LabEvidenceReference> = store.evidenceForExecution(executionId)
    fun evidenceBytes(referenceId: String): ByteArray? {
        val reference = store.evidenceReference(referenceId) ?: return null
        return evidence.read(reference)
    }

    fun awaitExecution(executionId: String, timeout: Duration): LabExecutionRecord? {
        val current = store.execution(executionId) ?: return null
        if (current.state !in ACTIVE_STATES) return current
        val control = jobs[executionId] ?: return current
        control.finished.await(timeout.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
        return store.execution(executionId)
    }

    private fun execute(
        control: JobControl, operation: LabOperationDefinition, queued: LabExecutionRecord, argv: List<String>
    ) {
        try {
            executeInternal(control, operation, queued, argv)
        } catch (error: Throwable) {
            control.session?.cancel()
            val current = store.execution(queued.id)
            if (current != null && current.state in ACTIVE_STATES) {
                val state = if (control.cancellationRequested.get()) LabExecutionState.CANCELLED else LabExecutionState.FAILED
                val failed = current.copy(state = state, endedAtEpochMillis = clock(),
                    errorSummary = error.message ?: error.javaClass.simpleName)
                runCatching { store.saveExecution(failed) }
                runCatching {
                    publish(current.sessionId, current.id, current.assetIds.singleOrNull(),
                        "operation." + state.name.lowercase(), LabEventSeverity.MEDIUM,
                        failed.errorSummary ?: "Falha na operação")
                }
            }
        } finally {
            jobs.remove(queued.id)
            control.finished.countDown()
        }
    }

    private fun executeInternal(
        control: JobControl, operation: LabOperationDefinition, queued: LabExecutionRecord, argv: List<String>
    ) {
        var record = store.execution(queued.id) ?: queued
        if (control.cancellationRequested.get()) {
            store.saveExecution(record.copy(state = LabExecutionState.CANCELLED, endedAtEpochMillis = clock()))
            return
        }
        val startedNanos = System.nanoTime()
        record = record.copy(state = LabExecutionState.RUNNING, startedAtEpochMillis = clock())
        store.saveExecution(record)
        publish(record.sessionId, record.id, record.assetIds.singleOrNull(),
            "operation.started", LabEventSeverity.INFO, "Operação " + operation.id + " iniciada")

        val stdout = OutputBuffer(maxOutputBytes)
        val stderr = OutputBuffer(maxOutputBytes)
        var exitCode: Int? = null
        var timedOut = false
        var failure: String? = null
        var process: RuntimeSession? = null
        try {
            val shellCommand = argv.joinToString(" ") { quoteShellArgument(it) }
            process = runtime.start(CommandRequest(
                shellCommand = shellCommand,
                workingDirectory = "/home/khalinux",
                timeout = Duration.ofSeconds(operation.timeoutSeconds.toLong())
            ))
            control.session = process
            if (control.cancellationRequested.get()) process.cancel()
            process.use { running ->
                for (event in running.events()) {
                    when (event) {
                        is RuntimeEvent.Stdout -> stdout.append(event.bytes)
                        is RuntimeEvent.Stderr -> stderr.append(event.bytes)
                        is RuntimeEvent.Exited -> { exitCode = event.exitCode; timedOut = event.timedOut }
                        is RuntimeEvent.Failed -> failure = event.message
                        is RuntimeEvent.Started -> Unit
                    }
                }
                val snapshot = running.snapshot()
                if (exitCode == null) exitCode = snapshot.exitCode
                timedOut = timedOut || snapshot.timedOut
                if (failure == null) failure = snapshot.errorMessage
            }
        } catch (error: Throwable) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            failure = error.message ?: error.javaClass.simpleName
        } finally {
            control.session = null
        }

        val references = mutableListOf<LabEvidenceReference>()
        var evidenceFailure: String? = null
        try { evidence.save(record.id, "stdout", "runtime.stdout", stdout.bytes())?.let {
            store.saveEvidenceReference(it); references.add(it)
        } } catch (error: Throwable) { evidenceFailure = "stdout: " + (error.message ?: "falha ao persistir") }
        try { evidence.save(record.id, "stderr", "runtime.stderr", stderr.bytes())?.let {
            store.saveEvidenceReference(it); references.add(it)
        } } catch (error: Throwable) {
            evidenceFailure = listOfNotNull(evidenceFailure, "stderr: " + (error.message ?: "falha ao persistir")).joinToString("; ")
        }

        val finalState = when {
            control.cancellationRequested.get() -> LabExecutionState.CANCELLED
            timedOut -> LabExecutionState.TIMED_OUT
            failure != null || evidenceFailure != null -> LabExecutionState.FAILED
            exitCode == 0 -> LabExecutionState.SUCCEEDED
            else -> LabExecutionState.FAILED
        }
        val summary = failure ?: evidenceFailure ?: if (finalState == LabExecutionState.FAILED)
            "Processo terminou com código " + (exitCode ?: -1) else null
        val durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
        val completed = record.copy(
            state = finalState, endedAtEpochMillis = clock(), exitCode = exitCode,
            errorSummary = summary, evidenceIds = references.map { it.id }, durationMillis = durationMillis
        )
        store.saveExecution(completed)
        references.forEach { reference ->
            publish(record.sessionId, record.id, record.assetIds.singleOrNull(), "evidence.created",
                LabEventSeverity.INFO, "Evidência " + reference.kind + " persistida", listOf(reference.id))
        }
        if (stdout.truncated || stderr.truncated) {
            publish(record.sessionId, record.id, record.assetIds.singleOrNull(), "operation.output_truncated",
                LabEventSeverity.MEDIUM, "Saída limitada a " + maxOutputBytes + " bytes por fluxo")
        }
        val severity = if (finalState == LabExecutionState.SUCCEEDED) LabEventSeverity.INFO else LabEventSeverity.MEDIUM
        publish(record.sessionId, record.id, record.assetIds.singleOrNull(),
            "operation." + finalState.name.lowercase(), severity,
            summary ?: "Operação concluída; duração " + durationMillis + " ms", references.map { it.id })
    }

    private fun resolveCommand(operation: LabOperationDefinition, values: Map<String, String>): List<String> {
        val definitions = operation.parameters.associateBy { it.id }
        require(values.keys.all { it in definitions }) { "Parâmetro não declarado no catálogo" }
        definitions.values.forEach { parameter ->
            val value = values[parameter.id]
            if (parameter.required) require(!value.isNullOrBlank()) { "Parâmetro obrigatório ausente: " + parameter.id }
            if (value != null) when (parameter.type) {
                LabParameterType.STRING -> require(value.length <= 2048 && value.none(Char::isISOControl))
                LabParameterType.INTEGER -> require(value.toLongOrNull() != null)
                LabParameterType.BOOLEAN -> require(value == "true" || value == "false")
                LabParameterType.ENUM -> require(value in parameter.choices)
            }
        }
        val placeholder = Regex("^\\{\\{([a-z][a-z0-9_]*)}}$")
        return operation.command.map { token ->
            val match = placeholder.matchEntire(token)
            if (match != null) values[match.groupValues[1]]
                ?: throw IllegalArgumentException("Parâmetro ausente: " + match.groupValues[1])
            else {
                require(!token.contains("{{") && !token.contains("}}")) { "Placeholder deve ocupar um argumento inteiro" }
                token
            }
        }
    }

    private fun quoteShellArgument(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun publish(
        sessionId: String, executionId: String?, assetId: String?, type: String,
        severity: LabEventSeverity, summary: String, evidenceIds: List<String> = emptyList()
    ) {
        val event = LabEventRecord(
            id = UUID.randomUUID().toString(), sessionId = sessionId, executionId = executionId,
            assetId = assetId, eventType = type, severity = severity, occurredAtEpochMillis = clock(),
            source = "lab-operation-engine", summary = summary, evidenceIds = evidenceIds
        )
        runCatching { store.saveEvent(event) }
        listeners.forEach { listener -> runCatching { listener(event) } }
    }

    private fun recoverInterruptedState() {
        val now = clock()
        store.executions().filter {
            it.state == LabExecutionState.QUEUED || it.state == LabExecutionState.RUNNING
        }.forEach { old ->
            store.saveExecution(old.copy(state = LabExecutionState.INTERRUPTED, endedAtEpochMillis = now,
                errorSummary = "Aplicativo foi encerrado antes do término da operação"))
            publish(old.sessionId, old.id, old.assetIds.singleOrNull(), "operation.interrupted",
                LabEventSeverity.MEDIUM, "Operação interrompida pelo encerramento do aplicativo")
        }
        store.sessions().filter { it.state == LabSessionState.ACTIVE }.forEach { old ->
            store.saveSession(old.copy(state = LabSessionState.INTERRUPTED, closedAtEpochMillis = now))
            publish(old.id, null, null, "session.interrupted", LabEventSeverity.MEDIUM,
                "Sessão interrompida pelo encerramento do aplicativo")
        }
    }

    private companion object {
        val ACTIVE_STATES = setOf(LabExecutionState.QUEUED, LabExecutionState.RUNNING)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        jobs.keys.toList().forEach(::cancel)
        executor.shutdownNow()
    }
}
