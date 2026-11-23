package com.khalinux.android.red

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor HTTP estritamente local para o laboratório RED.
 *
 * Vincula o socket a um IPv4 de interface Wi-Fi/AP identificada; não faz bind em 0.0.0.0.
 * O corpo de requisição não é persistido nem incluído em RedRequestObservation.
 */
class RedPortalServer(
    private val onEvent: (String) -> Unit,
    private val onAddress: (String?) -> Unit,
    private val onRequest: (RedRequestObservation) -> Unit = {}
) {
    @Volatile private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val clientSlots = Semaphore(MAX_CLIENTS)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var worker: Thread? = null
    @Volatile var address: String? = null
        private set
    @Volatile var boundInterface: String? = null
        private set
    @Volatile var boundPort: Int? = null
        private set

    @Synchronized
    fun start(preferredAddress: String? = null): String {
        if (running.get()) return address ?: error("Portal sem endereço disponível")
        val binding = resolveLabAddress(preferredAddress)
            ?: error("O Android não expôs um IPv4 de interface Wi-Fi/AP do laboratório.")
        val server = ServerSocket(PORT, BACKLOG, InetAddress.getByName(binding.second))
        server.soTimeout = ACCEPT_TIMEOUT_MS
        socket = server
        running.set(true)
        boundInterface = binding.first
        boundPort = server.localPort
        val url = "http://${binding.second}:${server.localPort}/"
        address = url
        onAddress(url)
        onEvent("Servidor HTTP local iniciado em ${binding.first}:${server.localPort}.")
        worker = Thread({ serve(server) }, "red-local-portal").apply { isDaemon = true; start() }
        return url
    }

    private fun serve(server: ServerSocket) {
        while (running.get()) {
            val client = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            if (!clientSlots.tryAcquire()) {
                runCatching { client.close() }
                continue
            }
            clients.add(client)
            Thread({
                try {
                    handle(client)
                } finally {
                    clients.remove(client)
                    clientSlots.release()
                }
            }, "red-portal-client").apply { isDaemon = true; start() }
        }
    }

    private fun handle(client: Socket) {
        client.use { peer ->
            peer.soTimeout = CLIENT_TIMEOUT_MS
            val input = BufferedInputStream(peer.getInputStream())
            val output = peer.getOutputStream()
            var method = "UNKNOWN"
            var path = "/"
            var contentType: String? = null
            var bodyBytes = 0
            var status = 400
            try {
                val headerBytes = readHeader(input) ?: return
                val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
                val lines = headerText.split("\r\n")
                val requestLine = lines.firstOrNull().orEmpty()
                val parts = requestLine.split(' ')
                if (parts.size != 3 || !parts[2].matches(Regex("HTTP/1\\.[01]"))) {
                    writeResponse(output, 400, "Requisição inválida.")
                    return
                }
                method = parts[0].uppercase(Locale.ROOT)
                val rawTarget = parts[1]
                path = rawTarget.substringBefore('?').take(512)
                if (path.isBlank() || !path.startsWith("/") || path.contains("..") || path.contains('\\')) {
                    status = 400
                    writeResponse(output, status, "Caminho inválido.")
                    return
                }
                val headers = parseHeaders(lines.drop(1))
                contentType = headers["content-type"]
                if (headers.containsKey("transfer-encoding")) {
                    status = 501
                    writeResponse(output, status, "Transfer-Encoding não disponível neste laboratório.")
                    return
                }
                val declaredLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (declaredLength < 0 || declaredLength > MAX_BODY_BYTES) {
                    status = 413
                    writeResponse(output, status, "Corpo acima do limite permitido.")
                    return
                }
                bodyBytes = readBody(input, declaredLength)?.size ?: 0
                if (bodyBytes != declaredLength) {
                    status = 400
                    writeResponse(output, status, "Corpo incompleto.")
                    return
                }

                val response = when {
                    method == "GET" && path == "/" -> {
                        status = 200
                        portalHtml()
                    }
                    method == "GET" && path == "/health" -> {
                        status = 200
                        """{"status":"ok","service":"khalinux-red-local-lab"}"""
                    }
                    method == "GET" && path == "/favicon.ico" -> {
                        status = 204
                        ""
                    }
                    method == "HEAD" -> {
                        status = 200
                        ""
                    }
                    method !in setOf("GET", "POST", "HEAD") -> {
                        status = 405
                        "Método não permitido."
                    }
                    else -> {
                        status = 404
                        "Rota não encontrada neste laboratório."
                    }
                }
                writeResponse(output, status, response, contentType = if (path == "/health") "application/json" else "text/html")
                onRequest(
                    RedRequestObservation(
                        method = method,
                        path = path,
                        statusCode = status,
                        contentType = contentType,
                        bodyBytes = bodyBytes,
                        remoteAddress = peer.inetAddress?.hostAddress
                    )
                )
                if (method == "GET" && path == "/") onEvent("O aparelho participante abriu o portal local.")
            } catch (error: Exception) {
                status = if (error is SocketTimeoutException) 408 else 400
                runCatching { writeResponse(output, status, "Não foi possível processar a requisição.") }
                onEvent("Falha ao processar requisição HTTP local: ${error.javaClass.simpleName}.")
                runCatching {
                    onRequest(
                        RedRequestObservation(
                            method = method,
                            path = path,
                            statusCode = status,
                            contentType = contentType,
                            bodyBytes = bodyBytes,
                            remoteAddress = peer.inetAddress?.hostAddress
                        )
                    )
                }
            }
        }
    }

    private fun readHeader(input: BufferedInputStream): ByteArray? {
        val bytes = ArrayList<Byte>(1024)
        var matched = 0
        val delimiter = byteArrayOf(13, 10, 13, 10)
        while (bytes.size < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else throw IllegalArgumentException("Cabeçalho incompleto")
            val byte = value.toByte()
            bytes.add(byte)
            matched = when {
                byte == delimiter[matched] -> matched + 1
                byte == delimiter[0] -> 1
                else -> 0
            }
            if (matched == delimiter.size) return ByteArray(bytes.size) { bytes[it] }
        }
        throw IllegalArgumentException("Cabeçalho acima do limite")
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> = buildMap {
        lines.forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
                if (name.matches(Regex("[a-z0-9-]{1,64}")) && name !in this) {
                    put(name, line.substring(separator + 1).trim().take(512))
                }
            }
        }
    }

    private fun readBody(input: BufferedInputStream, length: Int): ByteArray? {
        if (length == 0) return ByteArray(0)
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) return null
            offset += count
        }
        return body
    }

    private fun writeResponse(
        output: java.io.OutputStream,
        status: Int,
        body: String,
        contentType: String = "text/html"
    ) {
        val statusText = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            501 -> "Not Implemented"
            else -> "Error"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Content-Security-Policy: default-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${bytes.size}\r\n\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        if (status != 204) output.write(bytes)
        output.flush()
    }

    private fun portalHtml() = """
        <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Wi-Fi</title><body style="font:16px system-ui;max-width:560px;margin:36px auto;padding:0 18px;color:#202124">
        <h1>Bem-vindo</h1><p>Conecte-se para continuar navegando.</p>
        <p>O portal de treinamento está sendo preparado.</p></body></html>
    """.trimIndent()

    private fun resolveLabAddress(preferredAddress: String?): Pair<String, String>? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        val interfaces = candidates.filter { network ->
            val name = network.name.lowercase(Locale.ROOT)
            runCatching { network.isUp && !network.isLoopback }.getOrDefault(false) &&
                (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.contains("softap"))
        }
        if (!preferredAddress.isNullOrBlank()) {
            interfaces.firstNotNullOfOrNull { network ->
                val address = network.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    .firstOrNull { it.hostAddress == preferredAddress }
                address?.let { network.name to it.hostAddress }
            }?.let { return it }
            throw IllegalStateException("O endereço fornecido pelo hotspot não pertence a uma interface Wi-Fi/AP ativa.")
        }
        return interfaces.asSequence().flatMap { network ->
            network.inetAddresses.toList().asSequence()
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { network.name to it.hostAddress }
        }.firstOrNull { it.second.isNotBlank() }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        socket = null
        worker = null
        address = null
        boundInterface = null
        boundPort = null
        onAddress(null)
        onEvent("Servidor do portal local encerrado.")
    }

    companion object {
        const val PORT = 8080
        private const val BACKLOG = 16
        private const val MAX_CLIENTS = 4
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_BODY_BYTES = 4096
        private const val ACCEPT_TIMEOUT_MS = 1000
        private const val CLIENT_TIMEOUT_MS = 5000
    }
}
