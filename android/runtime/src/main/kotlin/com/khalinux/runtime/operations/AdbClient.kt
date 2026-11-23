package com.khalinux.runtime.operations

import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.RuntimeSession
import com.khalinux.runtime.SessionState
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AdbCapabilities(
    val installed: Boolean,
    val versionOutput: String,
    val wirelessPairing: Boolean,
    val detail: String
)

data class AdbCommandOutcome(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val durationMillis: Long
)

/**
 * Adaptador ADB sobre o RootFS ARM64. O servidor fica em uma sessão PRoot dedicada
 * para sobreviver entre chamadas; o encerramento do adaptador encerra essa sessão.
 */
class AdbClient(
    private val runtime: LinuxRuntime,
    private val home: String = "/home/khalinux",
    private val serverPort: Int = 5038,
    private val serverStartupTimeout: Duration = Duration.ofSeconds(8)
) : Closeable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var serverSession: RuntimeSession? = null
    private var serverDrainThread: Thread? = null
    private val serverOutput = StringBuilder()

    init {
        require(home.startsWith("/") && !home.contains(".."))
        require(serverPort in 1024..65535)
    }

    fun capabilities(): AdbCapabilities {
        return try {
            val version = collect(startOperationCommand(listOf("version"), Duration.ofSeconds(8)))
            if (version.exitCode != 0) {
                return AdbCapabilities(false, version.stdout, false,
                    version.stderr.ifBlank { "ADB não disponível no RootFS." })
            }
            val help = collect(startOperationCommand(listOf("help"), Duration.ofSeconds(8)))
            val helpText = (help.stdout + "\n" + help.stderr).lowercase()
            val pairing = help.exitCode == 0 && helpText.contains("adb pair") &&
                !helpText.contains("unknown command")
            AdbCapabilities(
                installed = true,
                versionOutput = version.stdout.ifBlank { version.stderr }.trim(),
                wirelessPairing = pairing,
                detail = if (pairing) "ADB com suporte a pareamento sem fio."
                    else "ADB disponível, mas sem suporte detectável a adb pair."
            )
        } catch (error: Exception) {
            AdbCapabilities(false, "", false, error.message ?: "Não foi possível iniciar o ADB.")
        }
    }

    /** Pair port e connect port são distintos no Android Wireless debugging. */
    fun pair(host: String, pairingPort: Int, pairingCode: String): RuntimeSession {
        require(pairingCode.matches(Regex("^[0-9]{6}$"))) { "Código de pareamento deve ter seis dígitos" }
        val capability = capabilities()
        require(capability.installed) { capability.detail }
        require(capability.wirelessPairing) { "A versão instalada do ADB não oferece adb pair" }
        return startOperationCommand(listOf("pair", endpoint(host, pairingPort), pairingCode), Duration.ofSeconds(45))
    }

    fun connect(host: String, connectionPort: Int): RuntimeSession =
        startOperationCommand(listOf("connect", endpoint(host, connectionPort)), Duration.ofSeconds(20))

    fun disconnect(host: String, connectionPort: Int): RuntimeSession =
        startOperationCommand(listOf("disconnect", endpoint(host, connectionPort)), Duration.ofSeconds(10))

    fun devices(): RuntimeSession = startOperationCommand(listOf("devices", "-l"), Duration.ofSeconds(10))

    fun deviceProperties(serial: String): RuntimeSession =
        startOperationCommand(listOf("-s", validSerial(serial), "shell", "getprop"), Duration.ofSeconds(20))

    fun deviceNetworkInterfaces(serial: String): RuntimeSession =
        startOperationCommand(listOf("-s", validSerial(serial), "shell", "ip", "-j", "address", "show"),
            Duration.ofSeconds(20))

    fun deviceRoutes(serial: String): RuntimeSession =
        startOperationCommand(listOf("-s", validSerial(serial), "shell", "ip", "-j", "route", "show"),
            Duration.ofSeconds(20))

    /** Entrada de operações ADB declaradas no catálogo; não grava argumentos nem códigos de pareamento. */
    internal fun startOperationCommand(arguments: List<String>, timeout: Duration): RuntimeSession {
        check(!closed.get()) { "adaptador ADB encerrado" }
        require(arguments.isNotEmpty() && arguments.none { it.isBlank() || it.any(Char::isISOControl) })
        ensureServer()
        val shell = arguments.joinToString(" ") { quote(it) }
        return runtime.start(CommandRequest(
            shellCommand = "exec " + shell,
            workingDirectory = home,
            environment = mapOf("HOME" to home, "ADB_SERVER_PORT" to serverPort.toString()),
            timeout = timeout
        ))
    }

    private fun ensureServer() {
        val session = synchronized(lock) {
            check(!closed.get()) { "adaptador ADB encerrado" }
            val current = serverSession
            if (current != null && current.snapshot().state == SessionState.RUNNING) current
            else {
                serverOutput.setLength(0)
                val started = runtime.start(CommandRequest(
                    shellCommand = "command -v adb >/dev/null 2>&1 || { printf '%s\\n' 'adb não instalado'; exit 127; }; exec adb nodaemon server",
                    workingDirectory = home,
                    environment = mapOf("HOME" to home, "ADB_SERVER_PORT" to serverPort.toString()),
                    timeout = null
                ))
                serverSession = started
                serverDrainThread = Thread({
                    try {
                        for (event in started.events()) {
                            when (event) {
                                is RuntimeEvent.Stdout -> synchronized(lock) {
                                    serverOutput.append(String(event.bytes, Charsets.UTF_8).take(8192))
                                }
                                is RuntimeEvent.Stderr -> synchronized(lock) {
                                    serverOutput.append(String(event.bytes, Charsets.UTF_8).take(8192))
                                }
                                is RuntimeEvent.Exited, is RuntimeEvent.Failed -> break
                                is RuntimeEvent.Started -> Unit
                            }
                        }
                    } catch (_: Throwable) {
                    } finally {
                        synchronized(lock) {
                            if (serverSession === started) serverSession = null
                        }
                    }
                }, "khalinux-adb-server-output").apply { isDaemon = true; start() }
                started
            }
        }
        awaitServerReady(session)
    }

    private fun awaitServerReady(session: RuntimeSession) {
        val deadline = System.nanoTime() + serverStartupTimeout.toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            val state = session.snapshot()
            if (state.state != SessionState.RUNNING && state.state != SessionState.CREATED) {
                val detail = synchronized(lock) { serverOutput.toString() }
                throw IllegalStateException("Servidor ADB encerrou durante a inicialização: " + detail.trim())
            }
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", serverPort), 250)
                }
                return
            } catch (error: Exception) {
                lastError = error
            }
            try { Thread.sleep(75) } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Inicialização ADB interrompida", error)
            }
        }
        session.cancel()
        val detail = synchronized(lock) { serverOutput.toString() }
        throw IllegalStateException("Servidor ADB não abriu a porta $serverPort: " + detail.trim(), lastError)
    }

    private fun collect(session: RuntimeSession, limit: Int = 1024 * 1024): AdbCommandOutcome {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val started = System.nanoTime()
        var exitCode: Int? = null
        var timedOut = false
        session.use { running ->
            for (event in running.events()) {
                when (event) {
                    is RuntimeEvent.Stdout -> appendBounded(stdout, event.bytes, limit)
                    is RuntimeEvent.Stderr -> appendBounded(stderr, event.bytes, limit)
                    is RuntimeEvent.Exited -> { exitCode = event.exitCode; timedOut = event.timedOut }
                    is RuntimeEvent.Failed -> stderr.write(event.message.toByteArray(Charsets.UTF_8))
                    is RuntimeEvent.Started -> Unit
                }
            }
            val snapshot = running.snapshot()
            if (exitCode == null) exitCode = snapshot.exitCode
            timedOut = timedOut || snapshot.timedOut
        }
        return AdbCommandOutcome(
            exitCode = exitCode ?: -1,
            stdout = String(stdout.toByteArray(), Charsets.UTF_8),
            stderr = String(stderr.toByteArray(), Charsets.UTF_8),
            timedOut = timedOut,
            durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        )
    }

    private fun appendBounded(output: ByteArrayOutputStream, bytes: ByteArray, limit: Int) {
        val remaining = limit - output.size()
        if (remaining > 0) output.write(bytes, 0, minOf(remaining, bytes.size))
    }

    private fun endpoint(hostValue: String, port: Int): String {
        require(port in 1..65535) { "Porta inválida" }
        val host = hostValue.trim().removePrefix("[").removeSuffix("]")
        require(host.isNotBlank() && host.length <= 253 &&
            host.matches(Regex("^[A-Za-z0-9._:-]+$")) && !host.startsWith(".") && !host.endsWith(".")) {
            "Endereço do dispositivo inválido"
        }
        val formatted = if (host.contains(":")) "[$host]" else host
        return "$formatted:$port"
    }

    private fun validSerial(value: String): String {
        require(value.length in 1..255 && value.matches(Regex("^[A-Za-z0-9._:-]+$"))) {
            "Identificador ADB inválido"
        }
        return value
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(lock) {
            val value = serverSession
            serverSession = null
            value
        }
        current?.cancel()
    }
}
