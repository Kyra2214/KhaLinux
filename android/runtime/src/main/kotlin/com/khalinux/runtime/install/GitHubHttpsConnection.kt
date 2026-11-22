package com.khalinux.runtime.install

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTPS-only redirect handling for immutable GitHub release assets. */
object GitHubHttpsConnection {
    private const val MAX_REDIRECTS = 5

    fun open(
        url: URL,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        rangeStart: Long = 0L
    ): HttpURLConnection {
        require(rangeStart >= 0L) { "rangeStart inválido" }
        require(url.protocol.equals("https", ignoreCase = true)) { "A conexão exige HTTPS" }
        var current = url
        var redirects = 0
        while (true) {
            require(isAllowedHost(current.host)) { "Host de download não autorizado: ${current.host}" }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                if (rangeStart > 0L) setRequestProperty("Range", "bytes=$rangeStart-")
            }
            connection.connect()
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                require(!location.isNullOrBlank()) { "Redirecionamento sem destino" }
                redirects += 1
                require(redirects <= MAX_REDIRECTS) { "Redirecionamentos demais no download" }
                current = URL(current, location)
                require(current.protocol.equals("https", ignoreCase = true)) {
                    "Redirecionamento para conexão não HTTPS recusado"
                }
                continue
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw HttpStatusException(code)
            }
            return connection
        }
    }

    private fun isAllowedHost(host: String): Boolean =
        host.equals("github.com", ignoreCase = true) ||
            host.equals("release-assets.githubusercontent.com", ignoreCase = true) ||
            host.equals("objects.githubusercontent.com", ignoreCase = true)
}

/** Resposta HTTP inesperada; 4xx não é repetido, exceto 416 tratado pelo downloader. */
class HttpStatusException(val code: Int) : IOException("Download falhou: HTTP $code")
