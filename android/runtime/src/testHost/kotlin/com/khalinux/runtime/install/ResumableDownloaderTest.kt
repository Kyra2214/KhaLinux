package com.khalinux.runtime.install

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResumableDownloaderTest {
    private val dir = Files.createTempDirectory("khalinux-dl").toFile()
    private val payload = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val requests = AtomicInteger()
    private val missingHits = AtomicInteger()
    @Volatile private var ranges = mutableListOf<String?>()
    @Volatile private var failFirstAfterBytes: Int? = null
    private val sha = MessageDigest.getInstance("SHA-256").digest(payload)

    init {
        server.createContext("/file") { ex -> handle(ex) }
        server.createContext("/missing") { ex -> missingHits.incrementAndGet(); ex.sendResponseHeaders(404, -1); ex.close() }
        server.start()
    }

    @After fun cleanup() { server.stop(0); dir.deleteRecursively() }

    private fun handle(ex: HttpExchange) {
        val n = requests.incrementAndGet()
        val range = ex.requestHeaders.getFirst("Range")
        synchronized(ranges) { ranges.add(range) }
        val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
        val body = payload.copyOfRange(start, payload.size)
        if (start > 0) {
            ex.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
            ex.sendResponseHeaders(206, body.size.toLong())
        } else {
            ex.sendResponseHeaders(200, body.size.toLong())
        }
        val cut = failFirstAfterBytes
        if (n == 1 && cut != null) {
            ex.responseBody.write(body, 0, cut)
            ex.responseBody.flush()
            ex.close() // fecha antes de completar o Content-Length
            return
        }
        ex.responseBody.write(body)
        ex.close()
    }

    private fun downloader(maxAttempts: Int = 3) = ResumableDownloader(
        connectTimeoutMillis = 2000,
        readTimeoutMillis = 2000,
        maxBytes = 10_000_000,
        maxAttempts = maxAttempts,
        sleeper = { },
        opener = { url, connect, read, start ->
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connect
                readTimeout = read
                if (start > 0) setRequestProperty("Range", "bytes=$start-")
                // Como o abridor real: status de erro vira HttpStatusException (4xx não é repetido).
                val code = responseCode
                if (code != 200 && code != 206) throw HttpStatusException(code)
            }
        }
    )

    private fun url(path: String = "/file") = URL("http://127.0.0.1:${server.address.port}$path")

    @Test(timeout = 30000) fun downloadsAndReturnsSha256() {
        val part = File(dir, "a.download")
        var last = 0L
        val digest = downloader().download(url(), part) { done, total ->
            last = done
            assertEquals(payload.size.toLong(), total)
        }
        assertTrue(sha.contentEquals(digest))
        assertEquals(payload.size.toLong(), last)
    }

    @Test(timeout = 30000) fun resumesFromExistingPartWithRange() {
        val part = File(dir, "b.download")
        part.writeBytes(payload.copyOf(120_000))
        val digest = downloader().download(url(), part) { _, _ -> }
        assertTrue(sha.contentEquals(digest), "hash do arquivo completo deve conferir após retomar")
        assertEquals("bytes=120000-", ranges.first())
        assertEquals(payload.size.toLong(), part.length())
    }

    @Test(timeout = 30000) fun retriesAndResumesAfterInterruptedTransfer() {
        failFirstAfterBytes = 100_000
        val part = File(dir, "c.download")
        val digest = downloader().download(url(), part) { _, _ -> }
        assertTrue(sha.contentEquals(digest))
        assertEquals(2, requests.get())
        assertTrue(ranges[1]?.startsWith("bytes=") == true, "segunda tentativa deve usar Range")
    }

    @Test(timeout = 30000) fun givesUpAfterMaxAttempts() {
        failFirstAfterBytes = 1000
        assertFailsWith<java.io.IOException> {
            downloader(maxAttempts = 1).download(url(), File(dir, "d.download")) { _, _ -> }
        }
    }

    @Test(timeout = 30000) fun clientErrorsAreNotRetried() {
        assertFailsWith<Exception> { downloader().download(url("/missing"), File(dir, "e.download")) { _, _ -> } }
        assertEquals(1, missingHits.get(), "erro 4xx não deve ser repetido")
    }

    @Test(timeout = 30000) fun cancellationStopsDownload() {
        assertFailsWith<InstallCancelledException> {
            downloader().download(url(), File(dir, "f.download"), isCancelled = { true }) { _, _ -> }
        }
    }
}
