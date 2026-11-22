package com.khalinux.runtime

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Delegates process I/O and exit handling while routing termination to a
 * separately identified process group.
 */
internal class ProcessGroupManagedProcess(
    private val delegate: Process,
    private val processGroupId: Int,
    private val signalGroup: (processGroupId: Int, force: Boolean) -> Boolean
) : Process() {
    override fun getOutputStream(): OutputStream = delegate.outputStream
    override fun getInputStream(): InputStream = delegate.inputStream
    override fun getErrorStream(): InputStream = delegate.errorStream
    override fun waitFor(): Int = delegate.waitFor()
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = delegate.waitFor(timeout, unit)
    override fun exitValue(): Int = delegate.exitValue()
    override fun isAlive(): Boolean = delegate.isAlive

    override fun destroy() {
        if (isAlive) signalGroup(processGroupId, false)
    }

    override fun destroyForcibly(): Process {
        if (isAlive) signalGroup(processGroupId, true)
        return this
    }
}
