package com.khalinux.runtime

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Delegates the process streams and lifecycle while routing termination to the
 * process group created by Android's setsid launcher.
 */
internal class ProcessGroupManagedProcess(
    private val delegate: Process,
    private val signalGroup: (processId: Int, force: Boolean) -> Boolean
) : Process() {
    private val processId = Math.toIntExact(delegate.pid())

    override fun getOutputStream(): OutputStream = delegate.outputStream
    override fun getInputStream(): InputStream = delegate.inputStream
    override fun getErrorStream(): InputStream = delegate.errorStream
    override fun waitFor(): Int = delegate.waitFor()
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = delegate.waitFor(timeout, unit)
    override fun exitValue(): Int = delegate.exitValue()
    override fun isAlive(): Boolean = delegate.isAlive
    override fun pid(): Long = delegate.pid()

    override fun destroy() {
        if (isAlive) signalGroup(processId, false)
    }

    override fun destroyForcibly(): Process {
        if (isAlive) signalGroup(processId, true)
        return this
    }
}
