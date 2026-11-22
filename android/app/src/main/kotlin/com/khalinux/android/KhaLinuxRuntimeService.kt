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
import com.khalinux.runtime.install.ProotInstaller
import com.khalinux.runtime.install.GitHubHttpsConnection
import com.khalinux.runtime.SessionSnapshot
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.net.URL
import org.json.JSONObject
import java.security.MessageDigest

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

    interface ProotInstallListener {
        fun onProgress(downloadedBytes: Long, totalBytes: Long?)
        fun onInstalled(prootExecutable: File, runtimeReady: Boolean)
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

    fun isProotInstalled(): Boolean {
        val proot = File(filesDir, "runtime/proot")
        return proot.isFile && proot.canExecute()
    }

    /** Instala PRoot somente após conferir o hash fixado do manifesto e os hashes do artefato. */
    fun installLatestProot(listener: ProotInstallListener) {
        installerExecutor.submit {
            try {
                val releaseTag = PROOT_RELEASE_TAG
                val releaseBase = "https://github.com/Kyra2214/KhaLinux/releases/download/$releaseTag"
                val manifestUrl = URL("$releaseBase/proot-arm64.json")
                val connection = GitHubHttpsConnection.open(manifestUrl, 15_000, 20_000)
                val manifestBytes = try {
                    connection.inputStream.use { input -> input.readNBytes(64 * 1024 + 1) }
                } finally {
                    connection.disconnect()
                }
                require(manifestBytes.size <= 64 * 1024) { "Manifesto PRoot excede 64 KiB" }
                val manifestSha = MessageDigest.getInstance("SHA-256").digest(manifestBytes)
                    .joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xff) }
                require(manifestSha == PROOT_MANIFEST_SHA256) { "SHA-256 do manifesto PRoot não confere" }

                val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
                require(manifest.getInt("schema") == 1) { "Versão de manifesto PRoot não suportada" }
                require(manifest.getString("architecture") == "arm64") { "Este PRoot não é ARM64" }
                require(manifest.getString("runtime") == "Android Bionic") { "Runtime PRoot incompatível" }
                require(manifest.getString("release_tag") == releaseTag) { "Tag do manifesto PRoot não corresponde à release fixada" }
                require(manifest.getString("proot_version") == PROOT_VERSION) { "Versão PRoot inesperada" }
                require(manifest.getString("proot_source") == "https://github.com/termux/proot") { "Origem PRoot inesperada" }
                require(manifest.getString("proot_source_sha256") == PROOT_SOURCE_SHA256) { "Fonte PRoot não corresponde à versão aprovada" }
                require(manifest.getString("talloc_source_sha256") == TALLOC_SOURCE_SHA256) { "Fonte talloc não corresponde à versão aprovada" }
                require(manifest.getString("libandroid_shmem_source_sha256") == SHMEM_SOURCE_SHA256) { "Fonte libandroid-shmem não corresponde à versão aprovada" }
                require(manifest.getInt("android_api") == 35) { "API Android do PRoot não corresponde ao build aprovado" }
                require(manifest.getString("archive_name") == "proot-arm64.tar.gz") { "Nome do pacote PRoot inesperado" }
                val expectedUrl = "$releaseBase/proot-arm64.tar.gz"
                require(manifest.getString("archive_url") == expectedUrl) { "URL do pacote PRoot não corresponde à release fixada" }
                val archiveSha = manifest.getString("archive_sha256")
                val binarySha = manifest.getString("binary_sha256")
                require(archiveSha == PROOT_ARCHIVE_SHA256) { "Hash do pacote PRoot não corresponde à release aprovada" }
                require(binarySha == PROOT_BINARY_SHA256) { "Hash do executável PRoot não corresponde à release aprovada" }

                val archiveBytes = manifest.getLong("archive_bytes")
                val binaryBytes = manifest.getLong("binary_bytes")
                require(archiveBytes in 1..(64L * 1024 * 1024)) { "Tamanho do pacote PRoot inválido" }
                require(binaryBytes in 64..(32L * 1024 * 1024)) { "Tamanho do executável PRoot inválido" }
                val requiredSpace = archiveBytes + binaryBytes + 8L * 1024 * 1024
                require(filesDir.usableSpace >= requiredSpace) {
                    "Espaço insuficiente: necessários aproximadamente ${requiredSpace / (1024 * 1024)} MB"
                }

                val target = File(filesDir, "runtime/proot")
                val result = ProotInstaller().install(
                    ProotInstaller.Request(expectedUrl, archiveSha, binarySha, target)
                ) { downloaded, total -> listener.onProgress(downloaded, total ?: archiveBytes) }
                require(result.binaryBytes == binaryBytes) { "Tamanho do executável PRoot divergente do manifesto" }
                runtime?.close()
                runtime = createRuntimeIfInstalled()
                listener.onInstalled(result.installedFile, runtime != null)
            } catch (error: Throwable) {
                listener.onError(error.message ?: "Falha ao instalar PRoot")
            }
        }
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
        private const val PROOT_RELEASE_TAG = "proot-android-5.1.107.102-v1"
        private const val PROOT_VERSION = "5.1.107.102"
        private const val PROOT_MANIFEST_SHA256 = "dafb56b19bc015c7c7bbc397b570a7c1192073eb2adfc59d0ec19e89acec06e9"
        private const val PROOT_ARCHIVE_SHA256 = "0249c0e901e2c711e270623dbb217f3ad0e547f4c69363d591a83b46925d8dc0"
        private const val PROOT_BINARY_SHA256 = "c2f7fac3f92ed73d088b1101f131257f7b5c76ded670da8c6afc762d25ae4fa0"
        private const val PROOT_SOURCE_SHA256 = "30d12a6e2cc4147123756ff58edea262d3083bbc238c12d0ab9b39e3d1c40852"
        private const val TALLOC_SOURCE_SHA256 = "912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007"
        private const val SHMEM_SOURCE_SHA256 = "1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867"
    }
}
