package com.khalinux.android.red

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Endpoint HTTP mínimo: publica apenas o certificado público da CA, nunca o portal sensível. */
class RedTlsPreparationServer(
    private val onEvent: (String) -> Unit = {},
    private val port: Int = PORT
) {
    private val running = AtomicBoolean(false)
    private val clientSlots = Semaphore(MAX_CLIENTS)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var certificateDer: ByteArray? = null
    @Volatile var certificateUrl: String? = null
        private set

    @Synchronized
    fun start(ipv4Address: String, caCertificateDer: ByteArray): String {
        if (running.get()) return certificateUrl ?: error("Preparação TLS sem endereço.")
        require(port in 1024..65535)
        require(caCertificateDer.isNotEmpty() && caCertificateDer.size <= MAX_CERTIFICATE_BYTES)
        val binding = InetAddress.getByName(ipv4Address)
        require(binding.hostAddress == ipv4Address && binding.address.size == 4) {
            "O endpoint de preparação exige o IPv4 literal da rede local."
        }
        val socket = ServerSocket(port, BACKLOG, binding).apply { soTimeout = ACCEPT_TIMEOUT_MS }
        certificateDer = caCertificateDer.copyOf()
        val url = "http://$ipv4Address:$port/ca.cer"
        certificateUrl = url
        serverSocket = socket
        running.set(true)
        Thread({ serve(socket, ipv4Address) }, "red-tls-preparation").apply { isDaemon = true; start() }
        onEvent("Endpoint HTTP de preparação TLS iniciado em $ipv4Address:$port; publica somente a CA pública.")
        return url
    }

    private fun serve(server: ServerSocket, expectedAddress: String) {
        while (running.get()) {
            val client = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            catch (failure: Exception) {
                if (running.get()) onEvent("Endpoint de preparação TLS encerrou: ${failure.javaClass.simpleName}.")
                break
            }
            if (!clientSlots.tryAcquire()) {
                runCatching { client.close() }
                continue
            }
            clients.add(client)
            Thread({
                try {
                    handle(client, expectedAddress)
                } finally {
                    clients.remove(client)
                    clientSlots.release()
                }
            }, "red-tls-preparation-client").apply { isDaemon = true; start() }
        }
    }

    private fun handle(client: Socket, expectedAddress: String) {
        client.use { peer ->
            peer.soTimeout = CLIENT_TIMEOUT_MS
            val input = BufferedInputStream(peer.getInputStream())
            val headerBytes = readHeader(input) ?: run {
                respond(peer, 400, "Bad request".toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")
                return
            }
            val header = String(headerBytes, StandardCharsets.ISO_8859_1)
            val firstLine = header.substringBefore("\r\n")
            val method = firstLine.substringBefore(' ')
            val path = firstLine.split(' ').getOrNull(1).orEmpty()
            val host = header.lineSequence().firstOrNull { it.startsWith("Host:", true) }?.substringAfter(':')?.trim()
            if (method != "GET" || host != "$expectedAddress:$port" || path !in setOf("/ca.cer", "/health")) {
                respond(peer, 404, "Not found".toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")
                return
            }
            if (path == "/health") {
                respond(peer, 200, "ready".toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")
                return
            }
            val certificate = certificateDer ?: run {
                respond(peer, 503, "Certificate unavailable".toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")
                return
            }
            respond(peer, 200, certificate, "application/pkix-cert", "attachment; filename=\"khalinux-red-lab-ca.cer\"")
        }
    }

    private fun readHeader(input: BufferedInputStream): ByteArray? {
        val output = ByteArrayOutputStream(512)
        val delimiter = byteArrayOf(13, 10, 13, 10)
        var matched = 0
        while (output.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) return null
            output.write(next)
            matched = when {
                next.toByte() == delimiter[matched] -> matched + 1
                next.toByte() == delimiter[0] -> 1
                else -> 0
            }
            if (matched == delimiter.size) return output.toByteArray()
        }
        return null
    }

    private fun respond(client: Socket, status: Int, body: ByteArray, contentType: String, disposition: String? = null) {
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 503 -> "Service Unavailable"; else -> "Not Found" }
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Cache-Control: no-store\r\n")
            disposition?.let { append("Content-Disposition: $it\r\n") }
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${body.size}\r\n\r\n")
        }
        client.getOutputStream().apply { write(headers.toByteArray(StandardCharsets.US_ASCII)); write(body); flush() }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        serverSocket = null
        certificateDer = null
        certificateUrl = null
    }

    companion object {
        const val PORT = 8080
        private const val BACKLOG = 8
        private const val MAX_CLIENTS = 2
        private const val MAX_HEADER_BYTES = 2048
        private const val ACCEPT_TIMEOUT_MS = 1000
        private const val CLIENT_TIMEOUT_MS = 3000
        private const val MAX_CERTIFICATE_BYTES = 16384
    }
}
