package com.khalinux.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.ProotConfig
import com.khalinux.runtime.ProotLauncher
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.SessionManager
import com.khalinux.runtime.SessionSnapshot
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fronteira Android entre Activity/UI e processos Linux.
 * A UI não cria ProcessBuilder nem conhece os argumentos PRoot.
 */
class KhaLinuxRuntimeService : Service() {
    interface EventListener {
        fun onEvent(event: RuntimeEvent)
    }

    inner class LocalBinder : Binder() {
        fun service(): KhaLinuxRuntimeService = this@KhaLinuxRuntimeService
    }

    private val binder = LocalBinder()
    private val callbacks: ExecutorService = Executors.newCachedThreadPool()
    private var runtime: LinuxRuntime? = null

    override fun onCreate() {
        super.onCreate()
        runtime = createRuntimeIfInstalled()
    }

    private fun createRuntimeIfInstalled(): LinuxRuntime? {
        val runtimeDirectory = File(filesDir, "runtime")
        val proot = File(runtimeDirectory, "proot")
        val rootfs = File(runtimeDirectory, "rootfs-arm64")
        if (!proot.isFile || !proot.canExecute() || !rootfs.isDirectory) return null
        return SessionManager(
            ProotLauncher(
                ProotConfig(
                    prootExecutable = proot,
                    rootfsDirectory = rootfs,
                    qemuExecutable = null
                )
            )
        )
    }

    fun isRuntimeInstalled(): Boolean = runtime != null

    fun startShell(
        shellCommand: String,
        timeoutMillis: Long? = null,
        listener: EventListener
    ): String {
        val activeRuntime = requireNotNull(runtime) {
            "Runtime Linux ainda não foi instalado neste dispositivo"
        }
        val request = CommandRequest(
            shellCommand = shellCommand,
            timeout = timeoutMillis?.let { Duration.ofMillis(it) }
        )
        val session = activeRuntime.start(request)
        callbacks.submit {
            try {
                session.events().forEach(listener::onEvent)
            } catch (error: Throwable) {
                listener.onEvent(RuntimeEvent.Failed(session.id, error.message ?: "erro de eventos"))
            }
        }
        return session.id
    }

    fun cancel(sessionId: String): Boolean = runtime?.cancel(sessionId) == true

    fun snapshot(sessionId: String): SessionSnapshot? = runtime?.snapshot(sessionId)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean = true

    override fun onDestroy() {
        runtime?.close()
        callbacks.shutdownNow()
        runtime = null
        super.onDestroy()
    }
}
