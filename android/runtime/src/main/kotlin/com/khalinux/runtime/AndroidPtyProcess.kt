package com.khalinux.runtime

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AndroidPtyProcessLauncher : TerminalProcessLauncher {
    override fun launch(arguments: List<String>, environment: Map<String, String>): Process =
        launch(arguments, environment, rows = 24, columns = 80)

    override fun launch(
        arguments: List<String>,
        environment: Map<String, String>,
        rows: Int,
        columns: Int
    ): Process {
        require(arguments.isNotEmpty()) { "lista de argumentos vazia" }
        val child = NativePty.spawn(
            arguments.toTypedArray(),
            environment.toSortedMap().map { (key, value) -> "$key=$value" }.toTypedArray(),
            rows,
            columns
        )
        check(child.size == 2) { "resposta inválida ao criar PTY" }
        return AndroidPtyProcess(masterFd = child[0], processId = child[1])
    }
}

private class AndroidPtyProcess(masterFd: Int, private val processId: Int) : Process(), ResizableProcess {
    private val descriptor = AtomicInteger(masterFd)
    private val exited = CountDownLatch(1)
    @Volatile private var result: Int? = null
    @Volatile private var waitFailure: IOException? = null

    private val input = object : InputStream() {
        private val streamClosed = AtomicBoolean(false)

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length) {
                "intervalo de leitura inválido"
            }
            if (length == 0) return 0
            if (streamClosed.get()) return -1
            val fd = descriptor.get()
            if (fd < 0) return -1
            val count = NativePty.read(fd, buffer, offset, length)
            if (count < 0) closeMaster()
            return count
        }

        override fun close() {
            if (streamClosed.compareAndSet(false, true)) closeMaster()
        }
    }

    private val output = object : OutputStream() {
        private val streamClosed = AtomicBoolean(false)

        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length) {
                "intervalo de escrita inválido"
            }
            if (length == 0) return
            check(!streamClosed.get()) { "stream de entrada fechado" }
            val fd = descriptor.get()
            if (fd < 0) throw IOException("PTY encerrado")
            val written = NativePty.write(fd, buffer, offset, length)
            if (written != length) throw IOException("escrita parcial no PTY: $written/$length")
        }

        override fun close() {
            // PTY não oferece half-close: preservar o master para leitura até o processo terminar.
            streamClosed.set(true)
        }
    }

    init {
        Thread({
            try {
                result = NativePty.waitFor(processId)
            } catch (error: IOException) {
                waitFailure = error
            } finally {
                exited.countDown()
            }
        }, "khalinux-pty-wait-$processId").apply {
            isDaemon = true
            start()
        }
    }

    override fun getOutputStream(): OutputStream = output

    override fun getInputStream(): InputStream = input

    override fun getErrorStream(): InputStream = InputStream.nullInputStream()

    override fun waitFor(): Int {
        exited.await()
        return exitResult()
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exited.await(timeout, unit)

    override fun exitValue(): Int {
        if (exited.count != 0L) throw IllegalThreadStateException("processo ainda está em execução")
        return exitResult()
    }

    override fun destroy() {
        if (isAlive()) {
            try {
                NativePty.signal(processId, false)
            } catch (_: IOException) {
                // O processo pode ter encerrado simultaneamente.
            }
        }
    }

    override fun destroyForcibly(): Process {
        if (isAlive()) {
            try {
                NativePty.signal(processId, true)
            } catch (_: IOException) {
                // O processo pode ter encerrado simultaneamente.
            }
        }
        return this
    }

    override fun isAlive(): Boolean = exited.count != 0L

    override fun resize(rows: Int, columns: Int): Boolean {
        require(rows in 1..65535) { "rows fora do intervalo suportado" }
        require(columns in 1..65535) { "columns fora do intervalo suportado" }
        val fd = descriptor.get()
        return fd >= 0 && isAlive() && NativePty.resize(fd, rows, columns)
    }

    private fun closeMaster() {
        val fd = descriptor.getAndSet(-1)
        if (fd >= 0) NativePty.close(fd)
    }

    private fun exitResult(): Int {
        waitFailure?.let { throw IllegalStateException("falha aguardando processo PTY", it) }
        return result ?: throw IllegalStateException("processo PTY terminou sem código de saída")
    }
}

internal interface ResizableProcess {
    fun resize(rows: Int, columns: Int): Boolean
}
