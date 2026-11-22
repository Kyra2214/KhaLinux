package com.khalinux.runtime.install

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Download HTTPS com retomada (Range), nova tentativa com backoff e SHA-256 contínuo. */
internal class ResumableDownloader(
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val maxBytes: Long,
    private val maxAttempts: Int = 3,
    private val backoffMillis: (attempt: Int) -> Long = { 1_000L shl (it - 1) },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val opener: (URL, Int, Int, Long) -> HttpURLConnection = { url, connect, read, start ->
        GitHubHttpsConnection.open(url, connect, read, start)
    }
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts deve ser positivo" }
        require(maxBytes > 0) { "maxBytes deve ser positivo" }
    }

    /** Devolve o SHA-256 do arquivo completo. O arquivo parcial é mantido em falhas de rede. */
    fun download(
        url: URL,
        partFile: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ): ByteArray {
        var attempt = 0
        while (true) {
            attempt++
            if (isCancelled()) throw InstallCancelledException()
            try {
                return attemptOnce(url, partFile, isCancelled, onProgress)
            } catch (error: HttpStatusException) {
                if (error.code == 416 && partFile.exists()) {
                    // Parcial inválido para o recurso atual: recomeça do zero.
                    partFile.delete()
                    if (attempt >= maxAttempts) throw error
                    continue
                }
                if (error.code in 400..499 || attempt >= maxAttempts) throw error
                sleeper(backoffMillis(attempt))
            } catch (error: IOException) {
                if (attempt >= maxAttempts) throw error
                sleeper(backoffMillis(attempt))
            }
        }
    }

    private fun attemptOnce(
        url: URL,
        partFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long?) -> Unit
    ): ByteArray {
        val existing = if (partFile.isFile) partFile.length() else 0L
        if (existing > maxBytes) partFile.delete()
        val resumeFrom = if (partFile.isFile) partFile.length() else 0L
        val connection = opener(url, connectTimeoutMillis, readTimeoutMillis, resumeFrom)
        try {
            val resumed = resumeFrom > 0 && connection.responseCode == 206 &&
                connection.getHeaderField("Content-Range")?.startsWith("bytes $resumeFrom-") == true
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            if (resumed) {
                FileInputStream(partFile).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        count += read
                    }
                }
            }
            val remaining = connection.contentLengthLong.takeIf { it >= 0 }
            val total = remaining?.let { count + it }
            require(total == null || total <= maxBytes) { "Arquivo excede o limite de download" }
            connection.inputStream.use { input ->
                FileOutputStream(partFile, resumed).use { sink ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (isCancelled()) throw InstallCancelledException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= maxBytes) { "Download excedeu o limite permitido" }
                        digest.update(buffer, 0, read)
                        sink.write(buffer, 0, read)
                        onProgress(count, total)
                    }
                }
            }
            if (total != null && count != total) throw IOException("Download incompleto: $count/$total bytes")
            require(count > 0) { "Download vazio" }
            return digest.digest()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
