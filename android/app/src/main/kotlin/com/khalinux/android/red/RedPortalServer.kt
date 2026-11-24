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
    private val onRequest: (RedRequestObservation) -> Unit = {},
    private val onSyntheticSubmission: (RedSyntheticSubmission) -> Unit = {},
    private val onCookieConsent: (RedCookieConsent) -> Unit = {},
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
            var observedHeaders: Map<String, String> = emptyMap()
            try {
                val headerBytes = readHeader(input) ?: return
                receivedHeader = true
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
                val rawPath = rawTarget.substringBefore('?')
                val query = rawTarget.substringAfter('?', "")
                if (rawPath.length > 512 || query.length > 1024) {
                    status = 414
                    writeResponse(output, status, "URI acima do limite permitido.")
                    return
                }
                path = rawPath
                if (query.isNotEmpty()) {
                    status = 400
                    writeResponse(output, status, "Query string não é aceita por este portal local.")
                    return
                }
                if (path.isBlank() || !path.startsWith("/") || path.contains("..") || path.contains('\\')) {
                    status = 400
                    writeResponse(output, status, "Caminho inválido.")
                    return
                }
                val headers = parseHeaders(lines.drop(1))
                observedHeaders = safeRequestHeaders(headers)
                val expectedHost = "${boundIpv4Address}:${boundPort}"
                val hostHeader = headers["host"]
                if ((parts[2] == "HTTP/1.1" && hostHeader.isNullOrBlank()) ||
                    (!hostHeader.isNullOrBlank() && !hostHeader.equals(expectedHost, ignoreCase = true))
                ) {
                    status = 421
                    writeResponse(output, status, "Host não corresponde ao endereço do portal local.")
                    return
                }
                contentType = headers["content-type"]
                if (method == "POST" && path in setOf("/consent", "/login") &&
                    contentType?.substringBefore(';')?.trim()
                        ?.equals("application/x-www-form-urlencoded", ignoreCase = true) != true
                ) {
                    status = 415
                    writeResponse(output, status, "Content-Type não suportado para este formulário.")
                    return
                }
                if (headers.containsKey("transfer-encoding")) {
                    status = 501
                    writeResponse(output, status, "Transfer-Encoding não disponível neste laboratório.")
                    return
                }
                val contentLength = headers["content-length"]
                if (contentLength != null && !contentLength.matches(Regex("[0-9]{1,10}"))) {
                    throw IllegalArgumentException("Content-Length inválido.")
                }
                val declaredLength = contentLength?.toIntOrNull() ?: 0
                if (declaredLength < 0 || declaredLength > MAX_BODY_BYTES) {
                    status = 413
                    writeResponse(output, status, "Corpo acima do limite permitido.")
                    return
                }
                requestBody = readBody(input, declaredLength) ?: ByteArray(0)
                bodyBytes = requestBody.size
                if (bodyBytes != declaredLength) {
                    status = 400
                    writeResponse(output, status, "Corpo incompleto.")
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
                        onEvent("Redirecionamento local executado: /go/notice → /notice.")
                        return
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
                            resultHtml("Escolha inválida", "Não foi possível validar as categorias selecionadas.")
                        } else {
                            status = 200
                            onCookieConsent(consent)
                            resultHtml("Preferências registradas", "Sua escolha de cookies foi registrada no laboratório.")
                        }
                    }
                    method == "POST" && path == "/login" && portalMode == RedPortalMode.LOCAL_LOGIN -> {
                        val result = processSyntheticLogin(requestBody, contentType)
                        status = result.first
                        result.second
                    }
                    method == "HEAD" && (
                        path == "/" || path in setOf("/terms", "/links", "/destination/news", "/destination/offers", "/notice", "/health", "/favicon.ico") ||
                            (path == "/login" && portalMode == RedPortalMode.LOCAL_LOGIN) ||
                            (path == "/open" && portalMode == RedPortalMode.OPEN_ACCESS)
                    ) -> {
                        status = if (path == "/favicon.ico") 204 else 200
                        ""
                    }
                    method == "HEAD" -> {
                        status = 404
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
                val renderedResponse = if (
                    status in 200..299 && path != "/health" && response.contains("</body>")
                ) response.replace("</body>", cookieBannerHtml() + "</body>") else response
                writeResponse(output, status, renderedResponse, contentType = if (path == "/health") "application/json" else "text/html")
                if (method == "GET" && path == "/") {
                    onEvent("Portal de treinamento acessado pelo aparelho participante.")
                } else {
                    Unit
                }
            } catch (error: Exception) {
                status = if (error is SocketTimeoutException) 408 else 400
                runCatching { writeResponse(output, status, "Não foi possível processar a requisição.") }
                onEvent("Falha ao processar requisição HTTP local: ${error.javaClass.simpleName}.")
            } finally {
                if (receivedHeader) {
                    runCatching {
                        onRequest(
                            RedRequestObservation(
                                method = method,
                                path = path,
                                statusCode = status,
                                contentType = contentType,
                                bodyBytes = bodyBytes,
                                remoteAddress = peer.inetAddress?.hostAddress,
                                safeHeaders = observedHeaders
                            )
                        )
                    }
                }
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
            200 to resultHtml("Acesso liberado", "Os dados de teste foram aceitos pelo portal local.")
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
        val allowed = setOf("host", "user-agent", "accept", "accept-language", "content-type", "referer")
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
            if (!name.matches(Regex("[a-z0-9-]{1,64}")) || value.length > 512) {
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
            414 -> "URI Too Long"
            415 -> "Unsupported Media Type"
            421 -> "Misdirected Request"
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
            append("X-Content-Type-Options: nosniff\r\n")
            append("Referrer-Policy: same-origin\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Permissions-Policy: camera=(self), microphone=(self), geolocation=(self)\\r\\n")
            append("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'\r\n")
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

    private fun openAccessHtml() = """
        <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Acesso liberado</title></head>
        <body style="font:16px system-ui;max-width:520px;margin:8vh auto;padding:24px;color:#202124">
        <h1>Conexão disponível</h1><p>Você já pode utilizar o acesso de visitantes.</p>
        <p><a href="/links">Continuar para a internet</a></p><p><a href="/terms">Termos de utilização</a></p></body></html>
    """.trimIndent()

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
    }
}
