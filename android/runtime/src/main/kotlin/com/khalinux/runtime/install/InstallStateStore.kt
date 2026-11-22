package com.khalinux.runtime.install

import java.io.File
import java.util.Properties

/** Estado persistido da última instalação; sobrevive ao fechamento do app. */
data class StoredInstallState(val phase: InstallPhase, val message: String?, val updatedAtEpochMillis: Long)

class InstallStateStore(private val file: File) {
    fun read(): StoredInstallState? {
        if (!file.isFile) return null
        return runCatching {
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            StoredInstallState(
                phase = InstallPhase.valueOf(properties.getProperty("phase")),
                message = properties.getProperty("message"),
                updatedAtEpochMillis = properties.getProperty("updatedAt")?.toLongOrNull() ?: 0L
            )
        }.getOrNull()
    }

    fun write(phase: InstallPhase, message: String? = null, now: Long = System.currentTimeMillis()) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            val properties = Properties().apply {
                setProperty("phase", phase.name)
                if (message != null) setProperty("message", message)
                setProperty("updatedAt", now.toString())
            }
            temporary.outputStream().use { properties.store(it, "KhaLinux install state") }
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        }
    }
}
