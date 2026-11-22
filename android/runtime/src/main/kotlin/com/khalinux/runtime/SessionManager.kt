package com.khalinux.runtime

import java.io.Closeable
import java.io.InputStream
import java.time.Clock
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SessionManager(
    private val launcher: ProotLauncher,
    private val maxSessions: Int = 4,
    private val clock: Clock = Clock.systemUTC()
) : LinuxRuntime {
    private val sessions = ConcurrentHashMap<String, ManagedSession>()
    private val activeSessionPermits = Semaphore(maxSessions)
    private val lifecycleLock = Any()
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val ioPool = Executors.newCachedThreadPool()
    private val closed = AtomicBoolean(false)

    init {
        require(maxSessions > 0) { "maxSessions deve ser maior que zero" }
    }

    override fun start(request: CommandRequest): RuntimeSession {
        val session = synchronized(lifecycleLock) {
            check(!closed.get()) { "runtime encerrado" }
            check(activeSessionPermits.tryAcquire()) {
                "limite de sessões ativas atingido: $maxSessions"
            }
            ManagedSession(UUID.randomUUID().toString(), request).also {
                sessions[it.id] = it
            }
        }
        try {
            session.start()
            return session
        } catch (error: Throwable) {
            sessions.remove(session.id)
            session.fail(error)
            throw error
        }
    }

    override fun snapshot(sessionId: String): SessionSnapshot? = sessions[sessionId]?.snapshot()

    override fun cancel(sessionId: String): Boolean = sessions[sessionId]?.cancel() ?: false

    override fun writeInput(sessionId: String, bytes: ByteArray): Boolean =
        sessions[sessionId]?.writeInput(bytes) ?: false

    override fun resize(sessionId: String, rows: Int, columns: Int): Boolean =
        sessions[sessionId]?.resize(rows, columns) ?: false

    override fun sessions(): List<SessionSnapshot> = sessions.values.map { it.snapshot() }.sortedBy { it.id }

    override fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            closed.compareAndSet(false, true)
        }
        if (!shouldClose) return

        sessions.values.forEach { it.cancel() }
        scheduler.shutdownNow()
        ioPool.shutdownNow()
    }

    private inner class ManagedSession(
        override val id: String,
        private val request: CommandRequest
    ) : RuntimeSession {
        private val queue: BlockingQueue<RuntimeEvent> = LinkedBlockingQueue()
        private val lock = Any()
        private val inputLock = Any()
        private val streamPumpsFinished = CountDownLatch(2)
        private val slotReleased = AtomicBoolean(false)
        private var process: Process? = null
        private var timeoutTask: ScheduledFuture<*>? = null
        private var current = SessionSnapshot(id, SessionState.CREATED, null, null, null, false, null)

        fun start() {
            val launched = launcher.launch(request)
            try {
                synchronized(lock) {
                    check(!closed.get()) { "runtime encerrado durante inicialização da sessão" }
                    process = launched
                    current = current.copy(
                        state = SessionState.RUNNING,
                        startedAtEpochMillis = clock.millis()
                    )
                }
                queue.offer(RuntimeEvent.Started(id))
                ioPool.submit {
                    try { pump(launched.inputStream, false) }
                    finally { streamPumpsFinished.countDown() }
                }
                ioPool.submit {
                    try { pump(launched.errorStream, true) }
                    finally { streamPumpsFinished.countDown() }
                }
                ioPool.submit { awaitExit(launched) }
                request.timeout?.let { timeout ->
                    timeoutTask = scheduler.schedule({
                        synchronized(lock) {
                            if (current.state == SessionState.RUNNING) current = current.copy(timedOut = true)
                        }
                        cancelInternal()
                    }, timeout.toMillis(), TimeUnit.MILLISECONDS)
                }
            } catch (error: Throwable) {
                try { launched.destroyForcibly() } catch (_: Throwable) { }
                try { launched.inputStream.close() } catch (_: Throwable) { }
                try { launched.errorStream.close() } catch (_: Throwable) { }
                try { launched.outputStream.close() } catch (_: Throwable) { }
                throw error
            }
        }

        private fun pump(stream: InputStream, stderr: Boolean) {
            stream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        val bytes = buffer.copyOf(count)
                        if (stderr) queue.offer(RuntimeEvent.Stderr(id, bytes))
                        else queue.offer(RuntimeEvent.Stdout(id, bytes))
                    }
                }
            }
        }

        private fun awaitExit(child: Process) {
            try {
                val exitCode = child.waitFor()
                streamPumpsFinished.await()
                val (timedOut, wasCancelling) = synchronized(lock) {
                    current.timedOut to (current.state == SessionState.CANCELLING)
                }
                synchronized(lock) {
                    current = current.copy(
                        state = if (timedOut || wasCancelling) SessionState.CANCELLED else SessionState.EXITED,
                        endedAtEpochMillis = clock.millis(),
                        exitCode = exitCode
                    )
                }
                timeoutTask?.cancel(false)
                releaseSlotOnce()
                queue.offer(RuntimeEvent.Exited(id, exitCode, timedOut))
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                fail(error)
            } catch (error: Throwable) {
                fail(error)
            } finally {
                releaseSlotOnce()
            }
        }

        private fun cancelInternal(): Boolean {
            val child = synchronized(lock) {
                if (current.state != SessionState.RUNNING && current.state != SessionState.CANCELLING) return false
                current = current.copy(state = SessionState.CANCELLING)
                process
            } ?: return false
            child.destroy()
            if (!child.waitFor(500, TimeUnit.MILLISECONDS)) child.destroyForcibly()
            return true
        }

        override fun writeInput(bytes: ByteArray): Boolean = synchronized(inputLock) {
            if (!request.interactive || bytes.isEmpty()) return false
            val child = synchronized(lock) {
                if (current.state != SessionState.RUNNING) null else process
            } ?: return false
            try {
                child.outputStream.write(bytes)
                child.outputStream.flush()
                true
            } catch (_: java.io.IOException) {
                false
            }
        }

        override fun resize(rows: Int, columns: Int): Boolean {
            require(rows in 1..65535) { "rows fora do intervalo suportado" }
            require(columns in 1..65535) { "columns fora do intervalo suportado" }
            if (!request.interactive) return false
            val child = synchronized(lock) {
                if (current.state != SessionState.RUNNING) null else process
            } ?: return false
            return (child as? ResizableProcess)?.resize(rows, columns) ?: false
        }

        override fun cancel(): Boolean = cancelInternal()

        override fun events(): Sequence<RuntimeEvent> = sequence {
            while (true) {
                val event = queue.take()
                yield(event)
                if (event is RuntimeEvent.Exited || event is RuntimeEvent.Failed) break
            }
        }

        override fun snapshot(): SessionSnapshot = synchronized(lock) { current }

        fun fail(error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            synchronized(lock) {
                current = current.copy(
                    state = SessionState.FAILED,
                    endedAtEpochMillis = clock.millis(),
                    errorMessage = message
                )
            }
            releaseSlotOnce()
            queue.offer(RuntimeEvent.Failed(id, message))
        }

        private fun releaseSlotOnce() {
            if (slotReleased.compareAndSet(false, true)) activeSessionPermits.release()
        }

        override fun close() { cancel() }
    }
}
