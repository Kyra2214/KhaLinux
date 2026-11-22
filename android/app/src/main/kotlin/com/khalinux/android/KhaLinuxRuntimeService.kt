package com.khalinux.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.khalinux.runtime.AndroidPtyProcessLauncher
import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.ProotConfig
import com.khalinux.runtime.ProotLauncher
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.SessionManager
import com.khalinux.runtime.install.RootfsInstaller
import com.khalinux.runtime.install.GitHubHttpsConnection
import com.khalinux.runtime.SessionSnapshot
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.net.URL
import org.json.JSONObject

/**
 * Fronteira Android entre Activity/UI e processos Linux.
 * A UI não cria ProcessBuilder nem conhece os argumentos PRoot.
 */
class KhaLinuxRuntimeService : Service() {
    interface EventListener {
        fun onEvent(event: RuntimeEvent)
    }

    interface RootfsInstallListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long?)
        fun onInstalled(rootfsDirectory: File, runtimeReady: Boolean)
        fun onError(message: String)
    }

    inner class LocalBinder : Binder() {
        fun service(): KhaLinuxRuntimeService = this@KhaLinuxRuntimeService
    }

    private val binder = LocalBinder()
    private val callbacks: ExecutorService = Executors.newCachedThreadPool()
    private val installerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
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
                ),
                interactiveProcessLauncher = AndroidPtyProcessLauncher()
            )
        )
    }

    fun isRuntimeInstalled(): Boolean = runtime != null

    fun isRootfsInstalled(): Boolean {
        val rootfs = File(filesDir, "runtime/rootfs-arm64")
        return rootfs.isDirectory && (File(rootfs, "bin/bash").exists() || File(rootfs, "usr/bin/bash").exists())
    }

    /** Busca o manifesto da release versionada e instala somente o artefato ARM64 declarado nela. */
    fun installLatestRootfs(listener: RootfsInstallListener) {
        installerExecutor.submit {
            try {
                val releaseTag = ROOTFS_RELEASE_TAG
                val releaseBase = "https://github.com/Kyra2214/KhaLinux/releases/download/$releaseTag"
                val manifestUrl = URL("$releaseBase/rootfs-arm64.json")
                val connection = GitHubHttpsConnection.open(manifestUrl, 15_000, 20_000)
                val manifestText = try {
                    connection.inputStream.use { input ->
                        val bytes = input.readNBytes(64 * 1024 + 1)
                        require(bytes.size <= 64 * 1024) { "Manifesto RootFS excede 64 KiB" }
                        String(bytes, Charsets.UTF_8)
                    }
                } finally {
                    connection.disconnect()
                }
                val manifest = JSONObject(manifestText)
                require(manifest.getInt("schema") == 1) { "Versão de manifesto RootFS não suportada" }
                require(manifest.getString("architecture") == "arm64") { "Este RootFS não é ARM64" }
                require(manifest.getString("release_tag") == releaseTag) { "Tag do manifesto não corresponde à release fixada" }
                require(manifest.getString("archive_name") == "rootfs-arm64.tar.gz") { "Nome de arquivo RootFS inesperado" }
                val expectedUrl = "$releaseBase/rootfs-arm64.tar.gz"
                require(manifest.getString("archive_url") == expectedUrl) { "URL do arquivo não corresponde à release fixada" }
                val expectedBytes = manifest.getLong("archive_bytes")
                require(expectedBytes in 1..1_500_000_000L) { "Tamanho do arquivo RootFS inválido" }
                val expectedRootfsBytes = manifest.getLong("rootfs_bytes")
                require(expectedRootfsBytes in 1..4_000_000_000L) { "Tamanho descompactado do RootFS inválido" }
                val requiredSpace = expectedBytes + expectedRootfsBytes + 32L * 1024 * 1024
                require(filesDir.usableSpace >= requiredSpace) {
                    "Espaço insuficiente: necessários aproximadamente ${requiredSpace / (1024 * 1024)} MB"
                }
                val target = File(filesDir, "runtime/rootfs-arm64")
                val result = RootfsInstaller().install(
                    RootfsInstaller.Request(expectedUrl, manifest.getString("archive_sha256"), target)
                ) { downloaded, total -> listener.onProgress(downloaded, total ?: expectedBytes) }
                runtime?.close()
                runtime = createRuntimeIfInstalled()
                listener.onInstalled(result.installedDirectory, runtime != null)
            } catch (error: Throwable) {
                listener.onError(error.message ?: "Falha ao instalar RootFS")
            }
        }
    }

    /** Instala o RootFS verificado em diretório privado; não executa comandos durante a extração. */
    fun installRootfs(archiveUrl: String, expectedSha256: String, listener: RootfsInstallListener) {
        installerExecutor.submit {
            try {
                val target = File(filesDir, "runtime/rootfs-arm64")
                val result = RootfsInstaller().install(
                    RootfsInstaller.Request(archiveUrl, expectedSha256, target)
                ) { downloaded, total -> listener.onProgress(downloaded, total) }
                runtime?.close()
                runtime = createRuntimeIfInstalled()
                listener.onInstalled(result.installedDirectory, runtime != null)
            } catch (error: Throwable) {
                listener.onError(error.message ?: "Falha ao instalar RootFS")
            }
        }
    }

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
        return observe(activeRuntime.start(request), listener)
    }

    fun startInteractiveShell(
        rows: Int = 24,
        columns: Int = 80,
        listener: EventListener
    ): String {
        val activeRuntime = requireNotNull(runtime) {
            "Runtime Linux ainda não foi instalado neste dispositivo"
        }
        val request = CommandRequest(
            shellCommand = "",
            interactive = true,
            initialRows = rows,
            initialColumns = columns
        )
        return observe(activeRuntime.start(request), listener)
    }

    private fun observe(session: com.khalinux.runtime.RuntimeSession, listener: EventListener): String {
        callbacks.submit {
            try {
                session.events().forEach(listener::onEvent)
            } catch (error: Throwable) {
                listener.onEvent(RuntimeEvent.Failed(session.id, error.message ?: "erro de eventos"))
            }
        }
        return session.id
    }

    fun writeInput(sessionId: String, text: String): Boolean =
        runtime?.writeInput(sessionId, text.toByteArray(Charsets.UTF_8)) == true

    fun resize(sessionId: String, rows: Int, columns: Int): Boolean =
        runtime?.resize(sessionId, rows, columns) == true

    fun cancel(sessionId: String): Boolean = runtime?.cancel(sessionId) == true

    fun snapshot(sessionId: String): SessionSnapshot? = runtime?.snapshot(sessionId)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean = true

    override fun onDestroy() {
        runtime?.close()
        callbacks.shutdownNow()
        installerExecutor.shutdownNow()
        runtime = null
        super.onDestroy()
    }

    companion object {
        private const val ROOTFS_RELEASE_TAG = "rootfs-bookworm-20261122-v1"
    }
}
