package com.khalinux.android.red

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** HTTP local de laboratório. Descarta integralmente o corpo de formulários e registra só o evento. */
class RedPortalServer(
    private val onEvent: (String) -> Unit,
    private val onAddress: (String?) -> Unit
) {
    @Volatile private var socket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile var address: String? = null
        private set

    @Synchronized
    fun start(): String {
        if (running.get()) return address ?: error("Portal sem endereço disponível")
        val bindAddress = localIpv4() ?: error("O Android não expôs um endereço IPv4 da interface Wi-Fi do laboratório.")
        val server = ServerSocket(0, 16, java.net.InetAddress.getByName(bindAddress))
        socket = server
        running.set(true)
        val url = "http://$bindAddress:${server.localPort}/"
        address = url
        onAddress(url)
        onEvent("Servidor local iniciado na porta ${server.localPort}.")
        worker = Thread({ serve(server) }, "red-local-portal").apply { isDaemon = true; start() }
        return url ?: "Servidor ativo na porta ${server.localPort}; IP local não identificado."
    }

    private fun serve(server: ServerSocket) {
        while (running.get()) {
            val client = try { server.accept() } catch (_: Exception) { break }
            Thread({ handle(client) }, "red-portal-client").apply { isDaemon = true; start() }
        }
    }

    private fun handle(client: Socket) {
        client.use { peer ->
            try {
                peer.soTimeout = 5000
                val input = BufferedInputStream(peer.getInputStream())
                val header = StringBuilder()
                var previous = ""
                var current = -1
                while (header.length < 8192) {
                    current = input.read()
                    if (current < 0) return
                    header.append(current.toChar())
                    val value = header.toString()
                    if (value.endsWith("\r\n\r\n")) break
                    previous = value
                }
                val firstLine = header.lineSequence().firstOrNull().orEmpty()
                val method = firstLine.substringBefore(' ')
                val path = firstLine.split(' ').getOrNull(1).orEmpty()
                val length = Regex("(?im)^Content-Length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                // Consumir, mas nunca interpretar, persistir ou registrar o corpo do formulário.
                var remaining = length.coerceIn(0, 4096)
                val discard = ByteArray(512)
                while (remaining > 0) {
                    val read = input.read(discard, 0, minOf(discard.size, remaining))
                    if (read <= 0) break
                    remaining -= read
                }
                val body = when {
                    path == "/submit" && method == "POST" -> {
                        onEvent("Participante concluiu o formulário sintético. Valores digitados descartados.")
                        "<h2>Etapa concluída</h2><p>Esta foi uma simulação. Nenhuma credencial foi validada, salva ou enviada.</p><a href='/'>Voltar</a>"
                    }
                    else -> portalHtml()
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val output = peer.getOutputStream()
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
                output.write(bytes)
                output.flush()
                if (path != "/submit") onEvent("Portal de treinamento acessado por um participante.")
            } catch (_: Exception) {
                onEvent("Falha ao atender uma requisição do portal local.")
            }
        }
    }

    private fun portalHtml() = """
        <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Laboratório Wi-Fi</title><body style="font:16px system-ui;max-width:560px;margin:36px auto;padding:0 18px;background:#101820;color:#eef">
        <div style="border:2px solid #ffb74d;padding:8px;text-align:center;font-weight:bold">SIMULAÇÃO DE LABORATÓRIO · DADOS FICTÍCIOS</div>
        <h1>Portal Wi-Fi de Treinamento</h1><p>Este portal demonstra como uma rede pública pode induzir alguém a preencher formulários.</p>
        <h2>Etapa demonstrativa</h2><p>Use somente dados fictícios. Não informe senhas reais.</p>
        <form id="demo"><label>E-mail fictício<br><input autocomplete="off" value="xxx@gmail.com"></label><br><br>
        <label>Senha fictícia<br><input type="password" autocomplete="off" value="123"></label><br><br>
        <button type="submit">Concluir simulação</button></form><script>document.getElementById("demo").addEventListener("submit",async e=>{e.preventDefault();await fetch("/submit",{method:"POST",headers:{"Content-Type":"text/plain"},body:"demo=completed"});document.body.innerHTML="<h1>Etapa concluída</h1><p>Simulação encerrada. Nenhum campo foi transmitido.</p>";});</script>
        <p style="margin-top:28px;font-size:13px">Este laboratório não imita serviços reais e não valida nem armazena credenciais.</p></body></html>
    """.trimIndent()

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan", true) || it.name.startsWith("ap", true)) }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }.getOrNull()

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        worker = null
        address = null
        onAddress(null)
        onEvent("Servidor do portal local encerrado.")
    }
}
