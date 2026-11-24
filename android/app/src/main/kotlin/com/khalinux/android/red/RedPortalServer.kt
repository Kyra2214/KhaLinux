package com.khalinux.android.red

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
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
    private val onRequest: (RedRequestObservation) -> Unit = {},
    private val onSyntheticSubmission: (RedSyntheticSubmission) -> Unit = {},
    private val onCookieConsent: (RedCookieConsent) -> Unit = {},
    private val onWebPermission: (RedWebPermissionObservation) -> Unit = {},
    private val configuredPort: Int = PORT
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
    @Volatile var boundIpv4Address: String? = null
        private set
    @Volatile private var portalMode: RedPortalMode = RedPortalMode.LOCAL_LOGIN
    @Volatile private var activeSessionToken: String? = null
    @Volatile private var sessionStartedAt: Long = 0L
    @Volatile private var lastActivityAt: Long = 0L
    @Volatile private var sessionOwnerAddress: String? = null
    private val failedSessionAttempts = ConcurrentHashMap<String, FailedAttempt>()
    private val failedLoginAttempts = ConcurrentHashMap<String, FailedAttempt>()
    private data class FailedAttempt(val count: Int, val blockedUntil: Long)
    private enum class AuthorizationResult { AUTHORIZED, INVALID_TOKEN, INVALID_ORIGIN, RATE_LIMITED, EXPIRED }

    fun setMode(mode: RedPortalMode) { portalMode = mode }

    @Synchronized
    fun start(preferredAddress: String? = null, mode: RedPortalMode = portalMode): String {
        if (running.get()) return address ?: error("Portal sem endereço disponível")
        portalMode = mode
        require(configuredPort in 1024..65535) { "A porta do portal deve estar entre 1024 e 65535." }
        val binding = resolveLabAddress(preferredAddress)
            ?: error("O Android não expôs um IPv4 de interface Wi-Fi/AP do laboratório.")
        // Bind somente no IPv4 da interface do hotspot; nunca usar 0.0.0.0.
        // A porta 8080 é o padrão, mas pode ser alterada explicitamente pelo operador.
        val server = ServerSocket(configuredPort, BACKLOG, InetAddress.getByName(binding.second))
        server.soTimeout = ACCEPT_TIMEOUT_MS
        val now = System.currentTimeMillis()
        activeSessionToken = createSessionToken()
        sessionStartedAt = now
        lastActivityAt = now
        sessionOwnerAddress = null
        failedSessionAttempts.clear()
        failedLoginAttempts.clear()
        socket = server
        running.set(true)
        boundInterface = binding.first
        boundPort = server.localPort
        boundIpv4Address = binding.second
        val url = "http://${binding.second}:${server.localPort}/"
        address = url
        onAddress(url)
        onEvent("Servidor HTTP local iniciado em ${binding.first}:${server.localPort}.")
        worker = Thread({ serve(server, binding.first, binding.second) }, "red-local-portal").apply { isDaemon = true; start() }
        return url
    }

    private fun serve(server: ServerSocket, expectedInterface: String, expectedAddress: String) {
        while (running.get()) {
            val client = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                if (isSessionExpired()) {
                    onEvent("Sessão do portal expirou por inatividade ou tempo máximo.")
                    stop()
                    break
                }
                if (!isBoundAddressAvailable(expectedInterface, expectedAddress)) {
                    onEvent("Interface $expectedInterface perdeu o IPv4 $expectedAddress; encerrando o portal.")
                    stop()
                    break
                }
                continue
            } catch (error: Exception) {
                if (running.get()) {
                    onEvent("Socket do portal encerrou inesperadamente: " + error.javaClass.simpleName + ".")
                    stop()
                }
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
            var requestBody = ByteArray(0)
            var status = 400
            var receivedHeader = false
            var requestReceivedAt = System.currentTimeMillis()
            var responseSent = false
            var rejectionReason: String? = null
            var serverFailure = false
            var sessionExpired = false
            var observedHeaders: Map<String, String> = emptyMap()
            var pendingCookieConsent: RedCookieConsent? = null
            var pendingWebPermission: RedWebPermissionObservation? = null

            fun respond(code: Int, body: String, responseContentType: String = "text/html") {
                status = code
                writeResponse(output, code, body, responseContentType)
                responseSent = true
            }

            try {
                val headerBytes = readHeader(input) ?: return
                receivedHeader = true
                requestReceivedAt = System.currentTimeMillis()
                val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
                val lines = headerText.split("\r\n")
                val requestLine = lines.firstOrNull().orEmpty()
                val parts = requestLine.split(' ')
                method = parts.getOrNull(0)?.uppercase(Locale.ROOT) ?: "UNKNOWN"
                if (parts.size > 1) path = parts[1].substringBefore('?').take(512)
                if (parts.size != 3 || (parts[2] != "HTTP/1.0" && parts[2] != "HTTP/1.1")) {
                    rejectionReason = "invalid_request_line"
                    respond(400, "Requisição inválida.")
                    return
                }

                val rawTarget = parts[1]
                val rawPath = rawTarget.substringBefore('?')
                val query = rawTarget.substringAfter('?', "")
                if (rawPath.length > 512 || query.length > 1024) {
                    rejectionReason = "uri_too_long"
                    respond(414, "URI acima do limite permitido.")
                    return
                }
                path = rawPath
                if (query.isNotEmpty()) {
                    rejectionReason = "query_not_supported"
                    respond(400, "Query string não é aceita por este portal local.")
                    return
                }
                if (path.isBlank() || !path.startsWith("/") || path.contains("..") || path.contains('\\')) {
                    rejectionReason = "invalid_path"
                    respond(400, "Caminho inválido.")
                    return
                }

                val headers = parseHeaders(lines.drop(1))
                observedHeaders = safeRequestHeaders(headers)
                val expectedHost = "${boundIpv4Address}:${boundPort}"
                val hostHeader = headers["host"]
                if ((parts[2] == "HTTP/1.1" && hostHeader.isNullOrBlank()) ||
                    (!hostHeader.isNullOrBlank() && !hostHeader.equals(expectedHost, ignoreCase = true))
                ) {
                    rejectionReason = "host_mismatch"
                    respond(421, "Host não corresponde ao endereço do portal local.")
                    return
                }

                val remoteAddress = peer.inetAddress?.hostAddress
                if (isSessionExpired()) {
                    sessionExpired = true
                    rejectionReason = "session_expired"
                    respond(410, "Sessão do portal expirada. Solicite uma nova sessão ao operador.")
                    return
                }
                if (sessionOwnerAddress != null && sessionOwnerAddress != remoteAddress) {
                    rejectionReason = "participant_ip_mismatch"
                    respond(403, "Esta sessão já está vinculada ao aparelho participante.")
                    return
                }

                if (method == "POST") {
                    when (authorizeMutation(headers, peer.inetAddress?.hostAddress)) {
                        AuthorizationResult.AUTHORIZED -> Unit
                        AuthorizationResult.INVALID_TOKEN -> {
                            rejectionReason = "invalid_session_token"
                            respond(403, "Sessão inválida. Reabra o portal pelo endereço informado pelo operador.")
                            return
                        }
                        AuthorizationResult.INVALID_ORIGIN -> {
                            rejectionReason = "invalid_request_origin"
                            respond(403, "Origem da solicitação não corresponde ao portal.")
                            return
                        }
                        AuthorizationResult.RATE_LIMITED -> {
                            rejectionReason = "session_rate_limited"
                            respond(429, "Muitas tentativas. Aguarde antes de tentar novamente.")
                            return
                        }
                        AuthorizationResult.EXPIRED -> {
                            sessionExpired = true
                            rejectionReason = "session_expired"
                            respond(410, "Sessão do portal expirada. Solicite uma nova sessão ao operador.")
                            return
                        }
                    }
                }

                contentType = headers["content-type"]
                if (method == "POST" && path in setOf("/consent", "/login", "/browser-permission") &&
                    contentType?.substringBefore(';')?.trim()
                        ?.equals("application/x-www-form-urlencoded", ignoreCase = true) != true
                ) {
                    rejectionReason = "unsupported_content_type"
                    respond(415, "Content-Type não suportado para este formulário.")
                    return
                }
                if (headers.containsKey("transfer-encoding")) {
                    rejectionReason = "transfer_encoding_unsupported"
                    respond(501, "Transfer-Encoding não disponível neste laboratório.")
                    return
                }
                val contentLength = headers["content-length"]
                if (contentLength != null && !contentLength.matches(Regex("[0-9]{1,10}"))) {
                    throw IllegalArgumentException("Content-Length inválido.")
                }
                val declaredLength = contentLength?.toIntOrNull() ?: 0
                if (declaredLength < 0 || declaredLength > MAX_BODY_BYTES) {
                    rejectionReason = "body_too_large"
                    respond(413, "Corpo acima do limite permitido.")
                    return
                }
                requestBody = readBody(input, declaredLength) ?: ByteArray(0)
                bodyBytes = requestBody.size
                if (bodyBytes != declaredLength) {
                    rejectionReason = "incomplete_body"
                    respond(400, "Corpo incompleto.")
                    return
                }

                val response = when {
                    method == "GET" && path == "/" -> {
                        status = 200
                        portalHtml(portalMode)
                    }
                    method == "GET" && path == "/login" && portalMode == RedPortalMode.LOCAL_LOGIN -> {
                        status = 200
                        loginHtml()
                    }
                    method == "GET" && path == "/open" && portalMode == RedPortalMode.OPEN_ACCESS -> {
                        status = 200
                        openAccessHtml()
                    }
                    method == "GET" && path == "/terms" -> {
                        status = 200
                        termsHtml()
                    }
                    method == "GET" && path == "/links" -> {
                        status = 200
                        linksHtml()
                    }
                    method == "GET" && path == "/destination/news" -> {
                        status = 200
                        destinationHtml("Notícias locais", "Página de conteúdo do laboratório.")
                    }
                    method == "GET" && path == "/destination/offers" -> {
                        status = 200
                        destinationHtml("Ofertas locais", "Página de conteúdo demonstrativa do laboratório.")
                    }
                    method == "GET" && path == "/notice" -> {
                        status = 200
                        destinationHtml("Aviso de navegação", "Você chegou a uma página local por um redirecionamento do laboratório.")
                    }
                    method == "GET" && path == "/go/notice" -> {
                        status = 302
                        writeRedirect(output, "/notice")
                        responseSent = true
                        onEvent("Redirecionamento local executado: /go/notice → /notice.")
                        return
                    }
                    method == "GET" && path == "/permissions" -> {
                        status = 200
                        permissionsHtml()
                    }
                    method == "GET" && path == "/health" -> {
                        status = 200
                        """{"status":"ok","service":"khalinux-red-local-lab"}"""
                    }
                    method == "GET" && path == "/favicon.ico" -> {
                        status = 204
                        ""
                    }
                    method == "POST" && path == "/consent" -> {
                        val consent = RedLabInputValidator.validateCookieConsent(requestBody, contentType)
                        if (consent == null) {
                            status = 400
                            rejectionReason = "invalid_cookie_consent"
                            resultHtml("Escolha inválida", "Não foi possível validar as categorias selecionadas.")
                        } else {
                            status = 200
                            pendingCookieConsent = consent
                            resultHtml("Preferências registradas", "Sua escolha de cookies foi registrada no laboratório.")
                        }
                    }
                    method == "POST" && path == "/browser-permission" -> {
                        val observation = RedLabInputValidator.validateWebPermission(requestBody, contentType)
                        if (observation == null) {
                            status = 400
                            rejectionReason = "invalid_web_permission"
                            resultHtml("Resultado inválido", "O estado de permissão do navegador não foi reconhecido.")
                        } else {
                            status = 200
                            pendingWebPermission = observation
                            resultHtml("Resultado registrado", "O resultado da permissão foi registrado no painel do laboratório.")
                        }
                    }
                    method == "POST" && path == "/login" && portalMode == RedPortalMode.LOCAL_LOGIN -> {
                        val remote = peer.inetAddress?.hostAddress ?: "unknown"
                        if (isLoginRateLimited(remote)) {
                            status = 429
                            rejectionReason = "login_rate_limited"
                            resultHtml("Aguarde", "Limite de tentativas atingido. Aguarde antes de tentar novamente.")
                        } else {
                            val result = processSyntheticLogin(requestBody, contentType)
                            status = result.first
                            if (status >= 400) {
                                rejectionReason = "form_validation_failed"
                                recordLoginFailure(remote)
                            } else {
                                failedLoginAttempts.remove(remote)
                            }
                            result.second
                        }
                    }
                    method == "HEAD" && (
                        path == "/" || path in setOf("/terms", "/links", "/permissions", "/destination/news", "/destination/offers", "/notice", "/health", "/favicon.ico") ||
                            (path == "/login" && portalMode == RedPortalMode.LOCAL_LOGIN) ||
                            (path == "/open" && portalMode == RedPortalMode.OPEN_ACCESS)
                    ) -> {
                        status = if (path == "/favicon.ico") 204 else 200
                        ""
                    }
                    method == "HEAD" -> {
                        status = 404
                        rejectionReason = "route_not_found"
                        ""
                    }
                    method !in setOf("GET", "POST", "HEAD") -> {
                        status = 405
                        rejectionReason = "method_not_allowed"
                        "Método não permitido."
                    }
                    else -> {
                        status = 404
                        rejectionReason = "route_not_found"
                        "Rota não encontrada neste laboratório."
                    }
                }
                val renderedResponse = if (
                    status in 200..299 && path != "/health" && response.contains("</body>")
                ) response.replace("</body>", cookieBannerHtml() + "</body>") else response
                if (renderedResponse.toByteArray(StandardCharsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                    rejectionReason = "response_too_large"
                    respond(413, "Resposta acima do limite permitido.")
                } else {
                    writeResponse(
                        output,
                        status,
                        renderedResponse,
                        contentType = if (path == "/health") "application/json" else "text/html",
                        setCookieHeader = if (status in 200..299) {
                            sessionCookieHeader(
                                peer.inetAddress?.hostAddress,
                                claimOwner = method == "GET" && path in setOf("/", "/login", "/open")
                            )
                        } else null
                    )
                    responseSent = true
                }
                if (method == "GET" && path == "/") {
                    onEvent("Portal de treinamento acessado pelo aparelho participante.")
                }
            } catch (error: Exception) {
                status = when {
                    error is SocketTimeoutException -> 408
                    error is IllegalArgumentException -> 400
                    else -> 500
                }
                rejectionReason = when {
                    error is SocketTimeoutException -> "client_timeout"
                    error is IllegalArgumentException -> "malformed_request"
                    else -> null
                }
                serverFailure = error !is SocketTimeoutException && error !is IllegalArgumentException
                runCatching {
                    writeResponse(
                        output,
                        status,
                        if (serverFailure) "Falha interna ao processar a requisição." else "Não foi possível processar a requisição."
                    )
                    responseSent = true
                }
                onEvent("Falha ao processar requisição HTTP local: ${error.javaClass.simpleName}.")
            } finally {
                if (receivedHeader) {
                    runCatching {
                        onRequest(
                            RedRequestObservation(
                                timestamp = requestReceivedAt,
                                method = method,
                                path = path,
                                statusCode = status,
                                contentType = contentType,
                                bodyBytes = bodyBytes,
                                remoteAddress = peer.inetAddress?.hostAddress,
                                safeHeaders = observedHeaders,
                                responseSent = responseSent,
                                rejectionReason = rejectionReason,
                                serverFailure = serverFailure
                            )
                        )
                    }
                    runCatching { pendingCookieConsent?.let(onCookieConsent) }
                    runCatching { pendingWebPermission?.let(onWebPermission) }
                }
                if (sessionExpired) stop()
            }
        }
    }

    private fun processSyntheticLogin(body: ByteArray, contentType: String?): Pair<Int, String> {
        val result = RedLabInputValidator.validateSyntheticForm(body, contentType)
        onSyntheticSubmission(
            RedSyntheticSubmission(
                fields = result.fields,
                accepted = result.accepted,
                message = result.message
            )
        )
        return if (result.accepted) {
            val page = resultHtml("Acesso liberado", "Os dados de teste foram aceitos pelo portal local.")
                .replace("</body>", browserPermissionPanelHtml() + "</body>")
            200 to page
        } else {
            400 to resultHtml("Dados não aceitos", result.message)
        }
    }

    private fun resultHtml(title: String, message: String) = """
        <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title><body style="font:16px system-ui;max-width:480px;margin:8vh auto;padding:24px;color:#202124">
        <h1>$title</h1><p>$message</p><p><a href="/">Voltar ao portal</a></p></body></html>
    """.trimIndent()

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

    private fun safeRequestHeaders(headers: Map<String, String>): Map<String, String> {
        val allowed = setOf("host", "accept", "content-type", "referer")
        return headers.filterKeys { it in allowed }.mapValues { (name, value) ->
            val safeValue = if (name == "referer") {
                runCatching { java.net.URI(value).rawPath.orEmpty() }.getOrDefault("")
            } else value
            safeValue.take(180)
        }
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> = buildMap {
        lines.filter { it.isNotEmpty() }.forEach { line ->
            if (line.firstOrNull() == ' ' || line.firstOrNull() == '\\t') {
                throw IllegalArgumentException("Header folding não é permitido.")
            }
            val separator = line.indexOf(':')
            if (separator <= 0) throw IllegalArgumentException("Cabeçalho HTTP inválido.")
            val rawName = line.substring(0, separator)
            if (rawName != rawName.trim()) throw IllegalArgumentException("Espaço inválido no nome do cabeçalho.")
            val name = rawName.lowercase(Locale.ROOT)
            val value = line.substring(separator + 1).trim()
            if (!name.matches(Regex("[a-z0-9-]{1,64}")) || value.length > 512 ||
                value.any { (it.code < 0x20 && it != '\t') || it.code == 0x7f }
            ) {
                throw IllegalArgumentException("Nome ou valor de cabeçalho inválido.")
            }
            if (name in this) throw IllegalArgumentException("Cabeçalho HTTP duplicado: $name.")
            put(name, value)
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

    private fun writeRedirect(output: java.io.OutputStream, location: String) {
        val response = "HTTP/1.1 302 Found\r\nLocation: $location\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun writeResponse(
        output: java.io.OutputStream,
        status: Int,
        body: String,
        contentType: String = "text/html",
        setCookieHeader: String? = null
    ) {
        val statusText = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            414 -> "URI Too Long"
            415 -> "Unsupported Media Type"
            421 -> "Misdirected Request"
            403 -> "Forbidden"
            410 -> "Gone"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            501 -> "Not Implemented"
            else -> "Error"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_RESPONSE_BYTES) {
            writeResponse(output, 413, "Resposta acima do limite permitido.", contentType)
            return
        }
        val headers = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            setCookieHeader?.let { append("Set-Cookie: $it\r\n") }
            append("X-Content-Type-Options: nosniff\r\n")
            append("Referrer-Policy: same-origin\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Permissions-Policy: camera=(self), microphone=(self), geolocation=(self)\r\n")
            append("Cross-Origin-Resource-Policy: same-origin\r\n")
            append("X-Permitted-Cross-Domain-Policies: none\r\n")
            append("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${bytes.size}\r\n\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        if (status != 204) output.write(bytes)
        output.flush()
    }

    private fun portalHtml(mode: RedPortalMode) = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Conecta Local</title><style>
        body{font:16px system-ui;background:#f4f6f8;color:#202124;margin:0}.wrap{max-width:520px;margin:7vh auto;padding:28px}
        .card{background:#fff;border:1px solid #e2e5e9;border-radius:14px;padding:26px;box-shadow:0 5px 24px #0000000b}
        h1{font-size:25px;margin:0 0 8px}.muted{color:#5f6368;line-height:1.5}.actions{display:grid;gap:12px;margin-top:24px}
        a{display:block;padding:13px 16px;border-radius:8px;background:#2457a6;color:white;text-decoration:none;text-align:center}
        a.secondary{background:#eef2f7;color:#23364d}.foot{font-size:12px;color:#73777d;margin-top:22px}
        </style></head><body><main class="wrap"><section class="card">
        <h1>Conecta Local</h1><p class="muted">Acesso à rede de visitantes.</p><p class="muted"><strong>Ambiente de laboratório:</strong> esta página usa HTTP sem criptografia TLS. Utilize somente identificadores e códigos de teste.</p>
        <p class="muted">${if (mode == RedPortalMode.LOCAL_LOGIN) "Entre para continuar." else "A conexão está disponível. Você já pode continuar."}</p>
        <div class="actions">
        <a href="${if (mode == RedPortalMode.LOCAL_LOGIN) "/login" else "/open"}">${if (mode == RedPortalMode.LOCAL_LOGIN) "Acessar Wi-Fi" else "Continuar navegando"}</a>
        <a class="secondary" href="/terms">Termos de utilização</a><a class="secondary" href="/links">Conteúdo e navegação</a>
        <a class="secondary" href="/permissions">Permissões do navegador (demonstração transparente)</a>
        </div><p class="foot">Conecta Local · Acesso de visitantes</p></section></main></body></html>
    """.trimIndent()

    private fun loginHtml() = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Acesso Wi-Fi</title><style>body{font:16px system-ui;background:#f4f6f8;color:#202124;margin:0}.wrap{max-width:480px;margin:7vh auto;padding:24px}
        .card{background:#fff;border:1px solid #e2e5e9;border-radius:14px;padding:26px}label{display:block;margin:14px 0 6px}
        input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #c8cdd3;border-radius:7px;font:inherit}
        button{width:100%;padding:13px;border:0;border-radius:8px;background:#2457a6;color:white;font:inherit;margin-top:20px}
        .muted{color:#5f6368;font-size:13px}</style></head><body><main class="wrap"><section class="card">
        <h1>Acesso Wi-Fi</h1><p class="muted">Informe os dados de acesso de teste preparados para esta aula.</p><p class="muted"><strong>HTTP sem TLS:</strong> não informe senhas de contas pessoais ou de outros serviços.</p>
        <form method="post" action="/login"><label for="demo_id">Identificação de teste</label><input id="demo_id" name="demo_id" maxlength="30" autocomplete="off" placeholder="aluno-demo" required>
        <label for="demo_code">Código de acesso de teste</label><input id="demo_code" name="demo_code" maxlength="8" autocomplete="off" placeholder="LAB-2048" required>
        <button type="submit">Conectar</button></form><p class="muted">Use identificador aluno-... e código LAB-0000, fornecidos para o laboratório.</p>
        <p><a href="/">Voltar</a></p></section></main></body></html>
    """.trimIndent()

    private fun browserPermissionPanelHtml() = """
        <section id="browser-permissions" style="margin:20px auto;padding:16px;border:1px solid #d5d9df;border-radius:14px;max-width:520px">
          <h2 style="font:600 18px system-ui">Permissões do navegador</h2>
          <p style="font:14px system-ui;color:#4b5563">Câmera, microfone e localização são permissões independentes. Nenhuma é solicitada pelo botão de cookies ou pelo login. Abra a página de permissões e escolha individualmente cada solicitação; o navegador exibirá seus próprios avisos.</p>
          <a href="/permissions" style="display:inline-block;padding:10px 16px;border-radius:8px;background:#1f2937;color:white;text-decoration:none">Ver permissões disponíveis</a>
        </section>
    """.trimIndent()

    private fun cookieBannerHtml() = """
        <style>
        .cookie-layer{position:fixed;inset:auto 0 0;background:#fff;border-top:1px solid #d7dce2;box-shadow:0 -8px 28px #0002;padding:18px;z-index:99;color:#202124}
        .cookie-inner{max-width:720px;margin:auto;font:14px system-ui}.cookie-inner h2{font-size:18px;margin:0 0 8px}
        .cookie-inner p{line-height:1.45;margin:6px 0 12px}.cookie-actions{display:flex;flex-wrap:wrap;gap:8px}
        .cookie-actions button{border:0;border-radius:7px;padding:10px 14px;font:inherit;cursor:pointer;background:#2457a6;color:#fff}
        .cookie-actions .quiet{background:#edf1f5;color:#263746}.cookie-options{display:none;gap:8px;flex-wrap:wrap;margin:12px 0}
        .cookie-options label{display:flex;gap:6px;align-items:center}
        </style>
        <aside class="cookie-layer" id="cookie-layer" role="dialog" aria-labelledby="cookie-title">
          <div class="cookie-inner">
            <h2 id="cookie-title">Sua privacidade importa</h2>
            <p>Este portal usa um cookie necessário para lembrar sua escolha. As categorias opcionais guardam somente preferências de demonstração para análise, preferências e personalização. Não usamos rastreadores de terceiros.</p>
            <div class="cookie-options" id="cookie-options">
              <label><input type="checkbox" checked disabled> Necessários (sempre ativos)</label>
              <label><input type="checkbox" id="cookie-analytics"> Análise</label>
              <label><input type="checkbox" id="cookie-preferences"> Preferências</label>
              <label><input type="checkbox" id="cookie-personalization"> Personalização</label>
            </div>
            <div class="cookie-actions">
              <button type="button" onclick="saveCookieChoice('accept_all')">Aceitar todos</button>
              <button type="button" class="quiet" onclick="saveCookieChoice('reject_optional')">Recusar opcionais</button>
              <button type="button" class="quiet" onclick="showCookieOptions()">Configurar</button>
              <button type="button" class="quiet" id="cookie-save" style="display:none" onclick="saveCookieChoice('custom')">Salvar escolhas</button>
            </div>
          </div>
        </aside>
        <script>
        if(document.cookie.split(';').some(item=>item.trim().startsWith('kl_cookie_consent='))){document.getElementById('cookie-layer').remove();}
        function showCookieOptions(){
          document.getElementById('cookie-options').style.display='flex';
          document.getElementById('cookie-save').style.display='inline-block';
        }
        async function saveCookieChoice(choice){
          const selected={
            analytics:choice==='accept_all'||(choice==='custom'&&document.getElementById('cookie-analytics').checked),
            preferences:choice==='accept_all'||(choice==='custom'&&document.getElementById('cookie-preferences').checked),
            personalization:choice==='accept_all'||(choice==='custom'&&document.getElementById('cookie-personalization').checked)
          };
          const buttons=document.querySelectorAll('#cookie-layer button');
          buttons.forEach(button=>button.disabled=true);
          const maxAge=15552000;
          const secureFlag=location.protocol==='https:'?'; Secure':'';
          const body=new URLSearchParams({
            choice:choice,
            analytics:selected.analytics?'1':'0',
            preferences:selected.preferences?'1':'0',
            personalization:selected.personalization?'1':'0'
          });
          try{
            const response=await fetch('/consent',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body.toString(),credentials:'same-origin'});
            if(!response.ok) throw new Error('HTTP '+response.status);
            document.cookie='kl_cookie_consent='+choice+'; Path=/; Max-Age='+maxAge+'; SameSite=Lax'+secureFlag;
            Object.keys(selected).forEach(key=>{
              document.cookie='kl_'+key+'='+(selected[key]?'1':'0')+'; Path=/; Max-Age='+maxAge+'; SameSite=Lax'+secureFlag;
            });
            document.getElementById('cookie-layer').remove();
          }catch(error){
            buttons.forEach(button=>button.disabled=false);
            const message=document.createElement('p');
            message.textContent='Não foi possível registrar a escolha no painel do laboratório. Tente novamente.';
            document.querySelector('.cookie-inner').appendChild(message);
          }
        }
        </script>
    """.trimIndent()

    private fun permissionsHtml() = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Permissões do navegador</title>
        <style>
        body{font:16px system-ui;background:#f4f6f8;color:#202124;margin:0}.wrap{max-width:620px;margin:5vh auto;padding:20px}
        .card{background:#fff;border:1px solid #e2e5e9;border-radius:14px;padding:22px;margin:12px 0}
        .muted{color:#5f6368;line-height:1.5}.actions{display:grid;gap:10px}
        button{padding:12px;border:0;border-radius:8px;background:#2457a6;color:white;font:inherit}
        button:disabled{opacity:.55}.result{font-size:14px;margin-top:8px}
        </style></head><body><main class="wrap">
        <section class="card">
          <h1>Permissões do navegador</h1>
          <p><strong>Demonstração explícita do laboratório.</strong> Cada botão solicita somente o recurso indicado. O navegador exibirá seu próprio aviso quando a origem e a política permitirem.</p>
          <p class="muted">O portal não oculta pedidos nem usa o botão de cookies para conceder permissões. Nenhuma foto, gravação ou coordenada será enviada ao aparelho A. Para câmera e microfone, o fluxo local interrompe as faixas imediatamente. Para localização, as coordenadas são descartadas no aparelho B.</p>
          <p id="secure-status" class="muted" aria-live="polite"></p>
        </section>
        <section class="card actions">
          <button type="button" data-resource="camera">Solicitar acesso à câmera</button>
          <div id="camera-result" class="result" aria-live="polite">Ainda não solicitado.</div>
          <button type="button" data-resource="microphone">Solicitar acesso ao microfone</button>
          <div id="microphone-result" class="result" aria-live="polite">Ainda não solicitado.</div>
          <button type="button" data-resource="location">Solicitar acesso à localização</button>
          <div id="location-result" class="result" aria-live="polite">Ainda não solicitado.</div>
        </section>
        <p><a href="/">Voltar ao portal</a></p>
        <script>
        const secureContextAvailable = window.isSecureContext === true;
        document.getElementById('secure-status').textContent = secureContextAvailable
          ? 'Esta página está em contexto seguro. A disponibilidade ainda depende do navegador e das configurações do aparelho.'
          : 'Este endereço HTTP local não é um contexto seguro para APIs sensíveis. As solicitações serão registradas como indisponíveis; nenhum aviso de permissão será forçado.';
        async function reportPermission(resource,state){
          const body=new URLSearchParams({resource:resource,state:state});
          const response=await fetch('/browser-permission',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body.toString(),credentials:'same-origin'});
          if(!response.ok) throw new Error('Não foi possível registrar o estado no aparelho A.');
        }
        async function watchPermissionRevocation(resource,permissionName){
          if(!navigator.permissions||typeof navigator.permissions.query!=='function') return;
          try{
            const permission=await navigator.permissions.query({name:permissionName});
            if(permission.state!=='granted') return;
            permission.addEventListener('change',()=>{
              if(permission.state==='denied'){
                reportPermission(resource,'revoked').catch(()=>{});
              }
            });
          }catch(error){
            // O navegador pode não expor consulta para todos os recursos; não inferir revogação.
          }
        }
        async function requestResource(resource){
          const result=document.getElementById(resource+'-result');
          const button=document.querySelector('[data-resource="'+resource+'"]');
          button.disabled=true;
          result.textContent='Solicitação iniciada pelo participante.';
          try{
            if(!secureContextAvailable){
              await reportPermission(resource,'unavailable');
              result.textContent='Indisponível: o navegador não considera esta origem HTTP local um contexto seguro.';
              return;
            }
            if(resource==='camera'||resource==='microphone'){
              if(!navigator.mediaDevices||typeof navigator.mediaDevices.getUserMedia!=='function'){
                await reportPermission(resource,'unavailable');
                result.textContent='Indisponível: API de mídia não suportada neste navegador.';
                return;
              }
              const constraints=resource==='camera'?{video:true,audio:false}:{video:false,audio:true};
              const requestedEvent=reportPermission(resource,'requested').catch(()=>false);
              let mediaRequest;
              try{
                mediaRequest=navigator.mediaDevices.getUserMedia(constraints);
              }catch(error){
                await requestedEvent;
                throw error;
              }
              await requestedEvent;
              const stream=await mediaRequest;
              stream.getTracks().forEach(track=>track.stop());
              await reportPermission(resource,'granted');
              watchPermissionRevocation(resource,resource);
              result.textContent='Permissão concedida pelo navegador. Faixas interrompidas imediatamente; nenhum conteúdo foi transmitido.';
              return;
            }
            if(!navigator.geolocation){
              await reportPermission(resource,'unavailable');
              result.textContent='Indisponível: geolocalização não suportada neste navegador.';
              return;
            }
            const requestedEvent=reportPermission(resource,'requested').catch(()=>false);
            const positionRequest=new Promise((resolve,reject)=>{
              navigator.geolocation.getCurrentPosition(
                ()=>resolve(),
                error=>reject(error),
                {enableHighAccuracy:false,maximumAge:0,timeout:10000}
              );
            });
            await requestedEvent;
            await positionRequest;
            await reportPermission(resource,'granted');
            watchPermissionRevocation(resource,'geolocation');
            result.textContent='Permissão concedida pelo navegador. Coordenadas descartadas localmente e não transmitidas.';
          }catch(error){
            const state=error&&error.name==='AbortError'?'cancelled':
              (error&&('code' in error)&&error.code===1?'denied':
              (error&&error.name==='NotAllowedError'?'denied':'unavailable'));
            try{await reportPermission(resource,state);}catch(reportError){}
            result.textContent=state==='denied'?'Acesso negado pelo navegador ou participante.':
              state==='cancelled'?'Solicitação cancelada.':'Recurso indisponível ou não foi possível concluir a solicitação.';
          }finally{
            button.disabled=false;
          }
        }
        document.querySelectorAll('[data-resource]').forEach(button=>{
          button.addEventListener('click',()=>requestResource(button.dataset.resource));
        });
        </script>
        </main></body></html>
    """.trimIndent()

    private fun openAccessHtml() = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Acesso liberado</title></head>
        <body style="font:16px system-ui;max-width:520px;margin:8vh auto;padding:24px;color:#202124">
        <h1>Conexão disponível</h1><p>Você já pode utilizar o acesso de visitantes.</p>
        <p><a href="/links">Continuar para a internet</a></p><p><a href="/terms">Termos de utilização</a></p></body></html>
    """.trimIndent().replace("</body>", browserPermissionPanelHtml() + "</body>")

    private fun termsHtml() = """
        <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Termos</title>
        <body style="font:16px system-ui;max-width:520px;margin:8vh auto;padding:24px;color:#202124">
        <h1>Termos de utilização</h1><p>Use a rede de visitantes de acordo com as regras do estabelecimento.</p>
        <p>Proteja seus dados e confirme o endereço das páginas que acessar.</p><p><a href="/">Voltar ao portal</a></p></body></html>
    """.trimIndent()

    private fun linksHtml() = """
        <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Conteúdo</title>
        <body style="font:16px system-ui;max-width:520px;margin:8vh auto;padding:24px;color:#202124">
        <h1>Conteúdo para visitantes</h1><p><a href="/destination/news">Notícias</a></p>
        <p><a href="/destination/offers">Ofertas</a></p><p><a href="/go/notice">Continuar</a></p><p><a href="/">Voltar</a></p></body></html>
    """.trimIndent()

    private fun destinationHtml(title: String, message: String) = """
        <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title>
        <body style="font:16px system-ui;max-width:520px;margin:8vh auto;padding:24px;color:#202124">
        <h1>$title</h1><p>$message</p><p><a href="/links">Voltar ao conteúdo</a></p></body></html>
    """.trimIndent()

    private fun isLoginRateLimited(remoteAddress: String): Boolean =
        failedLoginAttempts[remoteAddress]?.blockedUntil?.let { it > System.currentTimeMillis() } == true

    private fun recordLoginFailure(remoteAddress: String) {
        val now = System.currentTimeMillis()
        failedLoginAttempts.compute(remoteAddress) { _, previous ->
            val previousCount = if (previous != null && previous.blockedUntil > 0L && previous.blockedUntil <= now) 0 else previous?.count ?: 0
            val count = previousCount + 1
            FailedAttempt(count, if (count >= MAX_LOGIN_FAILURES) now + LOGIN_LOCKOUT_MS else 0L)
        }
    }

    private fun createSessionToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun isSessionExpired(): Boolean {
        if (activeSessionToken.isNullOrBlank()) return true
        val now = System.currentTimeMillis()
        return now - sessionStartedAt > MAX_SESSION_LIFETIME_MS ||
            now - lastActivityAt > MAX_SESSION_IDLE_MS
    }

    private fun sessionCookieHeader(remoteAddress: String?, claimOwner: Boolean): String? {
        val token = activeSessionToken ?: return null
        if (remoteAddress.isNullOrBlank() || isSessionExpired()) return null
        synchronized(this) {
            if (sessionOwnerAddress == null && claimOwner) sessionOwnerAddress = remoteAddress
            if (sessionOwnerAddress != remoteAddress) return null
            lastActivityAt = System.currentTimeMillis()
        }
        return "kl_lab_session=$token; Path=/; HttpOnly; SameSite=Strict; Max-Age=$SESSION_COOKIE_MAX_AGE_SECONDS"
    }

    private fun authorizeMutation(headers: Map<String, String>, remoteAddress: String?): AuthorizationResult {
        if (isSessionExpired()) return AuthorizationResult.EXPIRED
        val expectedOrigin = "http://$boundIpv4Address:$boundPort"
        val origin = headers["origin"] ?: headers["referer"]?.let {
            runCatching { java.net.URI(it).let { uri -> "${uri.scheme}://${uri.rawAuthority}" } }.getOrNull()
        }
        if (origin.isNullOrBlank() || !origin.equals(expectedOrigin, ignoreCase = true)) {
            return AuthorizationResult.INVALID_ORIGIN
        }

        if (remoteAddress.isNullOrBlank() || sessionOwnerAddress != remoteAddress) {
            return AuthorizationResult.INVALID_TOKEN
        }
        val remoteKey = remoteAddress
        val now = System.currentTimeMillis()
        val existing = failedSessionAttempts[remoteKey]
        if (existing != null && existing.blockedUntil > now) return AuthorizationResult.RATE_LIMITED
        if (existing == null && failedSessionAttempts.size >= MAX_FAILED_REMOTE_ADDRESSES) {
            failedSessionAttempts.entries.removeIf { it.value.blockedUntil <= now }
            if (failedSessionAttempts.size >= MAX_FAILED_REMOTE_ADDRESSES) return AuthorizationResult.RATE_LIMITED
        }

        val expected = activeSessionToken ?: return AuthorizationResult.EXPIRED
        val supplied = headers["cookie"]
            ?.split(';')
            ?.firstNotNullOfOrNull { part ->
                val cookie = part.trim()
                if (cookie.startsWith("kl_lab_session=")) cookie.substringAfter('=').takeIf { it.isNotBlank() } else null
            }
        val tokenMatches = supplied != null && MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            supplied.toByteArray(StandardCharsets.UTF_8)
        )
        if (!tokenMatches) {
            val updated = failedSessionAttempts.compute(remoteKey) { _, previous ->
                val previousCount = if (previous != null && previous.blockedUntil > 0L && previous.blockedUntil <= now) 0 else previous?.count ?: 0
                val count = previousCount + 1
                FailedAttempt(
                    count = count,
                    blockedUntil = if (count >= MAX_AUTH_FAILURES) now + AUTH_LOCKOUT_MS else previous?.blockedUntil ?: 0L
                )
            } ?: FailedAttempt(1, 0L)
            return if (updated.blockedUntil > now) AuthorizationResult.RATE_LIMITED else AuthorizationResult.INVALID_TOKEN
        }

        failedSessionAttempts.remove(remoteKey)
        lastActivityAt = now
        return AuthorizationResult.AUTHORIZED
    }

    private fun resolveLabAddress(preferredAddress: String?): Pair<String, String>? {
        if (preferredAddress.isNullOrBlank()) {
            throw IllegalStateException("O servidor exige o IPv4 exato observado pelo controlador do hotspot.")
        }
        val requested = runCatching { InetAddress.getByName(preferredAddress) as? Inet4Address }.getOrNull()
            ?: throw IllegalStateException("Endereço do hotspot não é um IPv4 literal válido.")
        if (requested.hostAddress != preferredAddress) throw IllegalStateException("O endereço do hotspot deve ser IPv4 literal, sem hostname.")
        if (requested.isLoopbackAddress || requested.isLinkLocalAddress || requested.isAnyLocalAddress ||
            requested.isMulticastAddress || !(requested.isSiteLocalAddress || isSharedAddressSpace(requested))
        ) {
            throw IllegalStateException("O endereço do hotspot não é um IPv4 privado utilizável.")
        }
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrElse { throw IllegalStateException("Não foi possível enumerar interfaces de rede.") }
        val interfaces = candidates.filter { network ->
            val name = network.name.lowercase(Locale.ROOT)
            runCatching { network.isUp && !network.isLoopback }.getOrDefault(false) &&
                (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.contains("softap"))
        }
        return interfaces.firstNotNullOfOrNull { network ->
            val exactAddress = network.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.hostAddress == requested.hostAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            exactAddress?.let { network.name to it.hostAddress }
        } ?: throw IllegalStateException("O IPv4 fornecido não pertence à interface Wi-Fi/AP ativa da sessão.")
    }

    private fun isSharedAddressSpace(address: Inet4Address): Boolean {
        val bytes = address.address.map { it.toInt() and 0xff }
        return bytes[0] == 100 && bytes[1] in 64..127
    }

    private fun isBoundAddressAvailable(interfaceName: String, address: String): Boolean = runCatching {
        val network = NetworkInterface.getByName(interfaceName) ?: return@runCatching false
        network.isUp && !network.isLoopback && network.inetAddresses.toList()
            .filterIsInstance<Inet4Address>()
            .any { it.hostAddress == address }
    }.getOrDefault(false)

    @Synchronized
    fun stop() {
        val hadResources = running.getAndSet(false) || socket != null || worker != null
        if (!hadResources) return
        runCatching { socket?.close() }
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        socket = null
        worker = null
        address = null
        boundInterface = null
        boundPort = null
        boundIpv4Address = null
        activeSessionToken = null
        sessionStartedAt = 0L
        lastActivityAt = 0L
        sessionOwnerAddress = null
        failedSessionAttempts.clear()
        failedLoginAttempts.clear()
        onAddress(null)
        onEvent("Servidor do portal local encerrado.")
    }

    companion object {
        const val PORT = 8080
        private const val BACKLOG = 16
        private const val MAX_CLIENTS = 4
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_BODY_BYTES = 4096
        private const val MAX_RESPONSE_BYTES = 131072
        private const val ACCEPT_TIMEOUT_MS = 1000
        private const val CLIENT_TIMEOUT_MS = 5000
        private const val MAX_SESSION_IDLE_MS = 30 * 60 * 1000L
        private const val MAX_SESSION_LIFETIME_MS = 2 * 60 * 60 * 1000L
        private const val SESSION_COOKIE_MAX_AGE_SECONDS = 1800
        private const val MAX_AUTH_FAILURES = 5
        private const val MAX_FAILED_REMOTE_ADDRESSES = 64
        private const val AUTH_LOCKOUT_MS = 60 * 1000L
        private const val MAX_LOGIN_FAILURES = 5
        private const val LOGIN_LOCKOUT_MS = 60 * 1000L
    }
}
