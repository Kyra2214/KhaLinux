package com.khalinux.android

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.khalinux.runtime.AndroidPtyProcessLauncher
import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.ProotConfig
import com.khalinux.runtime.FallbackProotLocator
import com.khalinux.runtime.FilesDirProotLocator
import com.khalinux.runtime.NativeLibProotLocator
import com.khalinux.runtime.ProotLocator
import com.khalinux.runtime.diagnostics.DiagnosticReport
import com.khalinux.runtime.diagnostics.RuntimeDiagnostics
import com.khalinux.runtime.AndroidBindMountPolicy
import com.khalinux.runtime.ProotBindMountPreparer
import com.khalinux.runtime.ProotLauncher
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.SessionManager
import com.khalinux.runtime.install.RootfsInstaller
import com.khalinux.runtime.install.ProotInstaller
import com.khalinux.runtime.install.GitHubHttpsConnection
import com.khalinux.runtime.install.InstallCancelledException
import com.khalinux.runtime.install.InstallPhase
import com.khalinux.runtime.install.InstallProgress
import com.khalinux.runtime.install.InstallProgressCalculator
import com.khalinux.runtime.install.InstallStateStore
import com.khalinux.runtime.install.RootfsFinalizer
import com.khalinux.runtime.SessionSnapshot
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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

    interface EnvironmentInstallListener {
        fun onProgress(progress: InstallProgress)
        fun onFinished(runtimeReady: Boolean)
        fun onError(message: String)
    }

    inner class LocalBinder : Binder() {
        fun service(): KhaLinuxRuntimeService = this@KhaLinuxRuntimeService
    }

    private val binder = LocalBinder()
    private val callbacks: ExecutorService = Executors.newCachedThreadPool()
    private val installerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var runtime: LinuxRuntime? = null
    private val installRunning = AtomicBoolean(false)
    @Volatile private var installCancelled: Boolean = false
    @Volatile private var installListener: EnvironmentInstallListener? = null
    @Volatile private var lastInstallProgress: InstallProgress? = null
    @Volatile private var lastNotificationKey: Int = -1
    // Várias abas interativas podem coexistir (7.1): o foreground vale enquanto houver ao menos uma.
    private val activeInteractiveSessionIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val interactiveSessionsStarting = java.util.concurrent.atomic.AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        // 8.5: restaura a cópia anterior se uma instalação morreu entre as duas trocas (antes de procurar o runtime).
        runCatching {
            com.khalinux.runtime.install.InstallRecovery.recover(
                File(filesDir, "runtime"),
                InstallStateStore(File(filesDir, "runtime/install-state.properties")),
                installRunning.get()
            )
        }
        runtime = createRuntimeIfInstalled()
        // 8.1: limpa resíduos de instalações interrompidas; roda na fila do instalador (serial com a instalação).
        installerExecutor.submit { runCatching { storage().cleanup(installRunning.get()) } }
        // 8.3: aquece catálogos/toolkit/laboratórios em segundo plano para a primeira abertura das telas não pagar o parse.
        installerExecutor.submit { runCatching { packageManager(); toolkit(); labs(); operationCatalog(); refreshLocalDeviceInventory() } }
    }

    /** Higiene de armazenamento (8.1): relatório de espaço e limpeza de resíduos; nunca toca em dados do usuário. */
    fun storage(): com.khalinux.runtime.storage.StorageHygiene = com.khalinux.runtime.storage.StorageHygiene(
        runtimeDirectory = File(filesDir, "runtime"),
        homeDirectory = File(filesDir, "home"),
        packageTempDirectory = File(filesDir, "runtime/packages/tmp")
    )

    /** Plano A (filesDir) primeiro; plano B (libproot.so em jniLibs) só se o A não existir. */
    private val prootLocator: ProotLocator by lazy {
        FallbackProotLocator(
            listOf(
                FilesDirProotLocator(File(filesDir, "runtime/proot")),
                NativeLibProotLocator(File(applicationInfo.nativeLibraryDir))
            )
        )
    }

    private fun createRuntimeIfInstalled(): LinuxRuntime? {
        val runtimeDirectory = File(filesDir, "runtime")
        val prootLocation = prootLocator.locate()
        val rootfs = File(runtimeDirectory, "rootfs-arm64")
        if (prootLocation == null || !rootfs.isDirectory) return null
        runCatching { RootfsFinalizer.apply(rootfs) }
        // Home fora do RootFS: reinstalar o Debian não apaga os arquivos do usuário.
        val homeDirectory = File(filesDir, "home").apply { mkdirs() }
        val bindMounts = AndroidBindMountPolicy.defaultMounts() +
            com.khalinux.runtime.BindMount(homeDirectory, "/home/khalinux")
        runCatching { ProotBindMountPreparer.prepare(rootfs, bindMounts) }
            .getOrElse { return null }
        return SessionManager(
            ProotLauncher(
                ProotConfig(
                    prootExecutable = prootLocation.executable,
                    rootfsDirectory = rootfs,
                    qemuExecutable = null,
                    bindMounts = bindMounts,
                    tempDirectory = File(runtimeDirectory, "tmp"),
                    prootEnvironment = prootLocation.environment
                ),
                interactiveProcessLauncher = AndroidPtyProcessLauncher()
            )
        )
    }

    fun isRuntimeInstalled(): Boolean = runtime != null

    @Volatile private var operationCatalogCache: com.khalinux.runtime.operations.LabOperationCatalog? = null
    @Volatile private var operationEngineInstance: com.khalinux.runtime.operations.LabOperationEngine? = null
    @Volatile private var operationEngineRuntime: LinuxRuntime? = null
    @Volatile private var adbClientInstance: com.khalinux.runtime.operations.AdbClient? = null
    @Volatile private var adbClientRuntime: LinuxRuntime? = null

    /** Catálogo operacional LAB-0, independente da instalação do RootFS. */
    @Synchronized
    fun operationCatalog(): com.khalinux.runtime.operations.LabOperationCatalog {
        operationCatalogCache?.let { return it }
        val parsed = assets.open("operation-catalog.json").use {
            com.khalinux.android.operations.OperationCatalogJson.parse(String(it.readBytes(), Charsets.UTF_8))
        }
        operationCatalogCache = parsed
        return parsed
    }

    @Synchronized
    fun refreshLocalDeviceInventory(): com.khalinux.runtime.operations.LabAsset {
        val store = com.khalinux.runtime.operations.LabAssetStore(File(filesDir, "runtime/lab-assets"))
        return store.upsert(com.khalinux.android.devices.AndroidDeviceInventory(this).snapshot())
    }

    @Synchronized
    fun deviceAssets(): List<com.khalinux.runtime.operations.LabAsset> =
        com.khalinux.runtime.operations.LabAssetStore(File(filesDir, "runtime/lab-assets")).all()

    /**
     * Atualiza o inventário remoto por ADB e persiste os ativos retornados pelo próprio dispositivo.
     * Deve ser chamado em dispatcher de I/O; cada consulta gera execução/evidência no motor comum.
     */
    fun refreshAdbDeviceInventory(): List<com.khalinux.runtime.operations.LabAsset> {
        val engine = operationEngine() ?: throw IllegalStateException("Runtime Linux indisponível")
        val store = com.khalinux.runtime.operations.LabAssetStore(File(filesDir, "runtime/lab-assets"))
        val discoverySession = engine.createSession("ADB: descoberta de dispositivos")
        try {
            val listing = engine.submit(discoverySession.id, "android.adb.devices")
            val listed = engine.awaitExecution(listing.id, java.time.Duration.ofSeconds(20))
                ?: throw IllegalStateException("Consulta ADB não retornou resultado")
            if (listed.state != com.khalinux.runtime.operations.LabExecutionState.SUCCEEDED) {
                throw IllegalStateException(listed.errorSummary ?: "Falha ao listar dispositivos ADB")
            }
            val listingOutput = engine.evidenceForExecution(listing.id)
                .firstOrNull { it.kind == "process.stdout" }
                ?.let { engine.evidenceBytes(it.id) }
                ?.let { String(it, Charsets.UTF_8) }.orEmpty()
            val entries = com.khalinux.runtime.operations.AdbDeviceInventory.parseDevices(listingOutput)
            val baseAssets = entries.map { entry ->
                val id = com.khalinux.runtime.operations.AdbDeviceInventory.assetId(entry.serial)
                val asset = com.khalinux.runtime.operations.AdbDeviceInventory.toAsset(entry, previous = store.get(id))
                store.upsert(asset)
            }
            val connected = entries.filter { it.state == "device" }
            if (connected.isNotEmpty()) {
                val session = engine.createSession(
                    "ADB: inventário remoto",
                    connected.map { com.khalinux.runtime.operations.AdbDeviceInventory.assetId(it.serial) }.toSet()
                )
                try {
                    connected.forEach { entry ->
                        val assetId = com.khalinux.runtime.operations.AdbDeviceInventory.assetId(entry.serial)
                        val run = engine.submit(
                            session.id, "android.adb.device-properties", assetIds = setOf(assetId),
                            parameters = mapOf("serial" to entry.serial)
                        )
                        val result = engine.awaitExecution(run.id, java.time.Duration.ofSeconds(25))
                        val output = if (result?.state == com.khalinux.runtime.operations.LabExecutionState.SUCCEEDED) {
                            engine.evidenceForExecution(run.id).firstOrNull { it.kind == "process.stdout" }
                                ?.let { engine.evidenceBytes(it.id) }?.let { String(it, Charsets.UTF_8) }.orEmpty()
                        } else ""
                        val previous = store.get(assetId)
                        store.upsert(com.khalinux.runtime.operations.AdbDeviceInventory.toAsset(
                            entry, output, previous
                        ))
                    }
                } finally {
                    engine.closeSession(session.id)
                }
            }
            return baseAssets.mapNotNull { store.get(it.id) }
        } finally {
            engine.closeSession(discoverySession.id)
        }
    }

    /** Cliente ADB ARM64 com servidor persistente dentro do RootFS. */
    @Synchronized
    fun adbClient(): com.khalinux.runtime.operations.AdbClient? {
        val current = runtime ?: return null
        if (adbClientRuntime === current) adbClientInstance?.let { return it }
        closeAdbClient()
        val client = com.khalinux.runtime.operations.AdbClient(current)
        adbClientInstance = client
        adbClientRuntime = current
        return client
    }

    @Synchronized
    private fun closeAdbClient() {
        adbClientInstance?.close()
        adbClientInstance = null
        adbClientRuntime = null
    }

    /** Motor LAB-1 compartilhado pelo terminal e pelas interfaces RED/BLUE. */
    @Synchronized
    fun operationEngine(): com.khalinux.runtime.operations.LabOperationEngine? {
        val current = runtime ?: return null
        if (operationEngineRuntime === current) operationEngineInstance?.let { return it }
        closeOperationEngine()
        val catalog = runCatching { operationCatalog() }.getOrNull() ?: return null
        val directory = File(filesDir, "runtime/lab-operations").apply { mkdirs() }
        val engine = com.khalinux.runtime.operations.LabOperationEngine(
            runtime = current,
            catalog = catalog,
            store = com.khalinux.runtime.operations.LabOperationStore(directory),
            evidence = com.khalinux.runtime.operations.LabEvidenceStore(directory),
            adbClient = adbClient()
        )
        operationEngineInstance = engine
        operationEngineRuntime = current
        return engine
    }

    @Synchronized
    private fun closeOperationEngine() {
        operationEngineInstance?.close()
        operationEngineInstance = null
        operationEngineRuntime = null
    }

    private var packageManagerInstance: com.khalinux.runtime.packages.PackageManager? = null
    private var packageManagerRuntime: LinuxRuntime? = null

    /**
     * Gerenciador de pacotes ligado ao runtime atual (recriado se o runtime mudar). Nulo sem runtime
     * ou se o catálogo embarcado for inválido. Chamadas do gerenciador bloqueiam: usar thread de trabalho.
     */
    @Synchronized
    fun packageManager(): com.khalinux.runtime.packages.PackageManager? {
        val current = runtime ?: return null
        if (packageManagerRuntime === current) packageManagerInstance?.let { return it }
        val catalog = runCatching {
            assets.open("catalog.json").use {
                com.khalinux.android.packages.PackageCatalogJson.parse(String(it.readBytes(), Charsets.UTF_8))
            }
        }.getOrNull() ?: return null
        val dir = File(filesDir, "runtime/packages").apply { mkdirs() }
        val rootfs = File(filesDir, "runtime/rootfs-arm64")
        val store = com.khalinux.runtime.packages.InstalledPackageStore(File(dir, "installed.tsv"))
        val runner = com.khalinux.runtime.packages.LinuxRuntimePackageCommandRunner(current)
        val installer = com.khalinux.runtime.packages.PackageInstaller(catalog, store, runner, rootfs, File(dir, "tmp"))
        val manager = com.khalinux.runtime.packages.PackageManager(
            catalog = catalog,
            store = store,
            installer = installer,
            remover = com.khalinux.runtime.packages.PackageRemover(catalog, store, runner, rootfs),
            updater = com.khalinux.runtime.packages.PackageUpdater(catalog, store, installer),
            history = com.khalinux.runtime.packages.PackageHistoryStore(File(dir, "history.log"))
        )
        packageManagerInstance = manager
        packageManagerRuntime = current
        return manager
    }

    private var toolkitInstance: com.khalinux.runtime.toolkit.ToolkitService? = null
    private var toolkitRuntime: LinuxRuntime? = null

    /**
     * Cyber Toolkit ligado ao runtime atual: descritores (asset `tools.json`) validados contra o catálogo,
     * guarda de uso autorizado e verificações persistidas. Nulo sem runtime ou com assets inválidos.
     * Chamadas bloqueiam: usar thread de trabalho.
     */
    @Synchronized
    fun toolkit(): com.khalinux.runtime.toolkit.ToolkitService? {
        val current = runtime ?: return null
        if (toolkitRuntime === current) toolkitInstance?.let { return it }
        val catalog = runCatching {
            assets.open("catalog.json").use {
                com.khalinux.android.packages.PackageCatalogJson.parse(String(it.readBytes(), Charsets.UTF_8))
            }
        }.getOrNull() ?: return null
        val registry = runCatching {
            assets.open("tools.json").use {
                com.khalinux.android.toolkit.ToolkitJson.parse(String(it.readBytes(), Charsets.UTF_8), catalog)
            }
        }.getOrNull() ?: return null
        val packagesDir = File(filesDir, "runtime/packages").apply { mkdirs() }
        val toolkitDir = File(filesDir, "runtime/toolkit").apply { mkdirs() }
        val guard = com.khalinux.runtime.toolkit.ToolkitGuard(
            registry, com.khalinux.runtime.toolkit.AuthorizationStore(File(toolkitDir, "authorizations.tsv"))
        )
        val service = com.khalinux.runtime.toolkit.ToolkitService(
            registry = registry,
            guard = guard,
            runner = com.khalinux.runtime.packages.LinuxRuntimePackageCommandRunner(current),
            installed = com.khalinux.runtime.packages.InstalledPackageStore(File(packagesDir, "installed.tsv")),
            verifications = com.khalinux.runtime.toolkit.VerificationStore(File(toolkitDir, "verifications.tsv"))
        )
        toolkitInstance = service
        toolkitRuntime = current
        return service
    }

    private var labsInstance: com.khalinux.runtime.labs.LabProgressService? = null
    private var labsRuntime: LinuxRuntime? = null

    /**
     * Laboratórios (Fase 6): catálogo do asset `labs.json`, progresso persistido em `runtime/labs/progress.tsv`
     * e cópia das fixtures dos serviços para `/opt/khalinux-labs` no RootFS. Nulo sem runtime ou com asset inválido.
     * Chamadas bloqueiam: usar thread de trabalho.
     */
    @Synchronized
    fun labs(): com.khalinux.runtime.labs.LabProgressService? {
        val current = runtime ?: return null
        if (labsRuntime === current) labsInstance?.let { return it }
        val catalog = runCatching {
            assets.open("labs.json").use {
                com.khalinux.android.labs.LabsJson.parse(String(it.readBytes(), Charsets.UTF_8))
            }
        }.getOrNull() ?: return null
        // 8.3: as fixtures só mudam com o app; recopia apenas quando a marca (última atualização do app) muda ou o destino sumiu.
        runCatching {
            val target = File(filesDir, "runtime/rootfs-arm64/opt/khalinux-labs")
            val stampValue = packageManager.getPackageInfo(packageName, 0).lastUpdateTime.toString() + (if (target.isDirectory) "" else "-missing")
            com.khalinux.runtime.startup.AssetSyncStamp(File(filesDir, "runtime/labs/fixtures.stamp"))
                .syncIfStale(stampValue) { copyAssetTree("fixtures", target) }
        }
        val dir = File(filesDir, "runtime/labs").apply { mkdirs() }
        val service = com.khalinux.runtime.labs.LabProgressService(
            com.khalinux.runtime.labs.LabEngine(catalog),
            com.khalinux.runtime.labs.LabProgressStore(File(dir, "progress.tsv"))
        )
        labsInstance = service
        labsRuntime = current
        return service
    }

    private fun copyAssetTree(path: String, target: File) {
        val children = assets.list(path)
        if (children.isNullOrEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        target.mkdirs()
        for (name in children) copyAssetTree("$path/$name", File(target, name))
    }

    fun isRootfsInstalled(): Boolean {
        val rootfs = File(filesDir, "runtime/rootfs-arm64")
        // Validação estrita: um RootFS extraído com links quebrados (bin vazio) deve ser reinstalado.
        return com.khalinux.runtime.install.RootfsValidator.isUsable(rootfs)
    }

    fun isProotInstalled(): Boolean = prootLocator.locate() != null

    /** Executa o diagnóstico do ponto de teste físico; bloqueante, chamar fora da thread principal. */
    fun runDiagnostics(onUpdate: (DiagnosticReport) -> Unit): DiagnosticReport =
        RuntimeDiagnostics(
            locator = prootLocator,
            runtimeProvider = { runtime },
            tempDirectory = File(filesDir, "runtime/tmp")
        ).run(onUpdate)

    fun isInstallRunning(): Boolean = installRunning.get()

    /**
     * Instala PRoot e RootFS automaticamente (nessa ordem) com um único percentual global.
     * Chamadas repetidas apenas registram o novo ouvinte e reenviam o último progresso.
     */
    fun installEnvironment(listener: EnvironmentInstallListener) {
        installListener = listener
        if (!installRunning.compareAndSet(false, true)) {
            lastInstallProgress?.let(listener::onProgress)
            return
        }
        if (runtime != null) {
            installRunning.set(false)
            listener.onFinished(true)
            return
        }
        installCancelled = false
        lastNotificationKey = -1
        lastInstallProgress = null
        requestInstallForeground()
        installerExecutor.submit { runEnvironmentInstall() }
    }

    fun cancelInstall() {
        installCancelled = true
    }

    private fun runEnvironmentInstall() {
        val store = InstallStateStore(File(filesDir, "runtime/install-state.properties"))
        try {
            publishInstall(InstallPhase.CHECKING, 0.0)
            store.write(InstallPhase.CHECKING)
            if (!isProotInstalled()) {
                store.write(InstallPhase.DOWNLOADING_PROOT)
                installProotBlocking()
            }
            if (!isRootfsInstalled()) {
                store.write(InstallPhase.DOWNLOADING_ROOTFS)
                installRootfsBlocking()
            }
            publishInstall(InstallPhase.FINALIZING, 0.0)
            store.write(InstallPhase.FINALIZING)
            val rootfs = File(filesDir, "runtime/rootfs-arm64")
            RootfsFinalizer.apply(rootfs)
            publishInstall(InstallPhase.FINALIZING, 0.5)
            closeOperationEngine()
            closeAdbClient()
            runtime?.close()
            runtime = createRuntimeIfInstalled()
            val ready = runtime != null
            store.write(InstallPhase.READY)
            publishInstall(InstallPhase.READY, 1.0)
            installListener?.onFinished(ready)
        } catch (cancelled: InstallCancelledException) {
            store.write(InstallPhase.FAILED, "Instalação cancelada")
            installListener?.onError("Instalação cancelada")
        } catch (error: Throwable) {
            val message = error.message ?: "Falha ao instalar o ambiente Linux"
            store.write(InstallPhase.FAILED, message)
            installListener?.onError(message)
        } finally {
            installRunning.set(false)
            stopInstallForeground()
        }
    }

    private fun publishInstall(phase: InstallPhase, fraction: Double, done: Long = 0L, total: Long? = null) {
        val progress = InstallProgress(phase, InstallProgressCalculator.percent(phase, fraction), done, total)
        lastInstallProgress = progress
        installListener?.onProgress(progress)
        val key = phase.ordinal * 1000 + progress.percent
        if (key != lastNotificationKey) {
            lastNotificationKey = key
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, createInstallNotification(progress))
        }
    }

    private fun fetchManifest(releaseBase: String, name: String, expectedSha256: String, label: String): JSONObject {
        val connection = GitHubHttpsConnection.open(URL("$releaseBase/$name"), 15_000, 20_000)
        val manifestBytes = try {
            connection.inputStream.use { input -> input.readNBytes(64 * 1024 + 1) }
        } finally {
            connection.disconnect()
        }
        require(manifestBytes.size <= 64 * 1024) { "Manifesto $label excede 64 KiB" }
        val manifestSha = MessageDigest.getInstance("SHA-256").digest(manifestBytes)
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xff) }
        require(manifestSha == expectedSha256) { "SHA-256 do manifesto $label não confere" }
        return JSONObject(String(manifestBytes, Charsets.UTF_8))
    }

    /** Instala PRoot somente após conferir o hash fixado do manifesto e os hashes do artefato. */
    private fun installProotBlocking() {
        publishInstall(InstallPhase.DOWNLOADING_PROOT, 0.0)
        val releaseTag = PROOT_RELEASE_TAG
        val releaseBase = "https://github.com/Kyra2214/KhaLinux/releases/download/$releaseTag"
        val manifest = fetchManifest(releaseBase, "proot-arm64.json", PROOT_MANIFEST_SHA256, "PRoot")
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
        ProotInstaller().install(
            ProotInstaller.Request(expectedUrl, archiveSha, binarySha, binaryBytes, target),
            onPhase = { /* verificação do PRoot é instantânea; mantém a banda de download */ },
            isCancelled = { installCancelled }
        ) { downloaded, total ->
            val size = total ?: archiveBytes
            publishInstall(
                InstallPhase.DOWNLOADING_PROOT,
                InstallProgressCalculator.fraction(downloaded, size),
                downloaded,
                size
            )
        }
    }

    /** Busca o manifesto da release versionada e instala somente o artefato ARM64 declarado nela. */
    private fun installRootfsBlocking() {
        publishInstall(InstallPhase.DOWNLOADING_ROOTFS, 0.0)
        val releaseTag = ROOTFS_RELEASE_TAG
        val releaseBase = "https://github.com/Kyra2214/KhaLinux/releases/download/$releaseTag"
        val manifest = fetchManifest(releaseBase, "rootfs-arm64.json", ROOTFS_MANIFEST_SHA256, "RootFS")
        require(manifest.getInt("schema") == 1) { "Versão de manifesto RootFS não suportada" }
        require(manifest.getString("architecture") == "arm64") { "Este RootFS não é ARM64" }
        require(manifest.getString("release_tag") == releaseTag) { "Tag do manifesto não corresponde à release fixada" }
        require(manifest.getString("archive_name") == "rootfs-arm64.tar.gz") { "Nome de arquivo RootFS inesperado" }
        val expectedUrl = "$releaseBase/rootfs-arm64.tar.gz"
        require(manifest.getString("archive_url") == expectedUrl) { "URL do arquivo não corresponde à release fixada" }
        val archiveSha = manifest.getString("archive_sha256")
        require(archiveSha == ROOTFS_ARCHIVE_SHA256) { "Hash do arquivo RootFS não corresponde à release aprovada" }
        val expectedBytes = manifest.getLong("archive_bytes")
        require(expectedBytes in 1..1_500_000_000L) { "Tamanho do arquivo RootFS inválido" }
        val expectedRootfsBytes = manifest.getLong("rootfs_bytes")
        require(expectedRootfsBytes in 1..4_000_000_000L) { "Tamanho descompactado do RootFS inválido" }
        val target = File(filesDir, "runtime/rootfs-arm64")
        // Pico real: arquivo + RootFS novo + cópia anterior (se houver) + margem.
        val previousBytes = if (target.exists()) expectedRootfsBytes else 0L
        val requiredSpace = expectedBytes + expectedRootfsBytes + previousBytes + 32L * 1024 * 1024
        require(filesDir.usableSpace >= requiredSpace) {
            "Espaço insuficiente: necessários aproximadamente ${requiredSpace / (1024 * 1024)} MB"
        }
        RootfsInstaller().install(
            RootfsInstaller.Request(expectedUrl, archiveSha, target),
            onPhase = { phase -> if (phase == InstallPhase.VERIFYING) publishInstall(phase, 0.0) },
            onExtractProgress = { extracted ->
                publishInstall(
                    InstallPhase.EXTRACTING,
                    InstallProgressCalculator.fraction(extracted, expectedRootfsBytes),
                    extracted,
                    expectedRootfsBytes
                )
            },
            isCancelled = { installCancelled }
        ) { downloaded, total ->
            val size = total ?: expectedBytes
            publishInstall(
                InstallPhase.DOWNLOADING_ROOTFS,
                InstallProgressCalculator.fraction(downloaded, size),
                downloaded,
                size
            )
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
        interactiveSessionsStarting.incrementAndGet()
        requestInteractiveForeground()
        return try {
            val session = activeRuntime.start(request)
            activeInteractiveSessionIds.add(session.id)
            interactiveSessionsStarting.decrementAndGet()
            observe(session, listener, foregroundSession = true)
        } catch (error: Throwable) {
            interactiveSessionsStarting.decrementAndGet()
            stopInteractiveForeground()
            throw error
        }
    }

    private fun observe(
        session: com.khalinux.runtime.RuntimeSession,
        listener: EventListener,
        foregroundSession: Boolean = false
    ): String {
        callbacks.submit {
            try {
                session.events().forEach { event ->
                    listener.onEvent(event)
                    if (foregroundSession && (event is RuntimeEvent.Exited || event is RuntimeEvent.Failed)) {
                        stopInteractiveForeground(session.id)
                    }
                }
            } catch (error: Throwable) {
                listener.onEvent(RuntimeEvent.Failed(session.id, error.message ?: "erro de eventos"))
                if (foregroundSession) stopInteractiveForeground(session.id)
            }
        }
        return session.id
    }

    private fun requestInteractiveForeground() {
        val intent = Intent(this, KhaLinuxRuntimeService::class.java).setAction(ACTION_INTERACTIVE_SESSION)
        startForegroundService(intent)
    }

    private fun requestInstallForeground() {
        val intent = Intent(this, KhaLinuxRuntimeService::class.java).setAction(ACTION_INSTALL)
        // Sem foreground a instalação ainda executa, mas pode ser interrompida em segundo plano.
        runCatching { startForegroundService(intent) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INTERACTIVE_SESSION -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    createSessionNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                if (interactiveSessionsStarting.get() == 0 && activeInteractiveSessionIds.isEmpty() && !installRunning.get()) {
                    stopInteractiveForeground()
                }
            }
            ACTION_STOP_INTERACTIVE_SESSION -> {
                activeInteractiveSessionIds.toList().forEach { runtime?.cancel(it) }
                stopInteractiveForeground()
            }
            ACTION_INSTALL -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    createInstallNotification(lastInstallProgress),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                if (!installRunning.get()) stopInstallForeground()
            }
            ACTION_CANCEL_INSTALL -> installCancelled = true
        }
        return START_NOT_STICKY
    }

    private fun ensureNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_session_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_session_channel_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                INSTALL_CHANNEL_ID,
                getString(R.string.notif_install_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_install_channel_desc)
            }
        )
    }

    private fun createInstallNotification(progress: InstallProgress?): Notification {
        ensureNotificationChannels()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            2,
            Intent(this, KhaLinuxRuntimeService::class.java).setAction(ACTION_CANCEL_INSTALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val percent = progress?.percent ?: 0
        val label = progress?.phase?.label ?: InstallPhase.CHECKING.label
        return NotificationCompat.Builder(this, INSTALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_install_title))
            .setContentText("$label · $percent%")
            .setProgress(100, percent, progress == null)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_cancel), cancel)
            .build()
    }

    private fun createSessionNotification(): Notification {
        ensureNotificationChannels()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopSession = PendingIntent.getService(
            this,
            1,
            Intent(this, KhaLinuxRuntimeService::class.java).setAction(ACTION_STOP_INTERACTIVE_SESSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_session_text))
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notif_stop_session), stopSession)
            .build()
    }

    private fun stopInstallForeground() {
        if (activeInteractiveSessionIds.isNotEmpty() || interactiveSessionsStarting.get() > 0) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopInteractiveForeground(sessionId: String? = null) {
        if (sessionId != null) activeInteractiveSessionIds.remove(sessionId)
        // Ainda há abas interativas (ou uma iniciando) ou instalação: o foreground continua.
        if (activeInteractiveSessionIds.isNotEmpty() || interactiveSessionsStarting.get() > 0) return
        if (installRunning.get()) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        closeOperationEngine()
        closeAdbClient()
        runtime?.close()
        callbacks.shutdownNow()
        installerExecutor.shutdownNow()
        runtime = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_INTERACTIVE_SESSION = "com.khalinux.android.action.INTERACTIVE_SESSION"
        private const val ACTION_STOP_INTERACTIVE_SESSION = "com.khalinux.android.action.STOP_INTERACTIVE_SESSION"
        private const val ACTION_INSTALL = "com.khalinux.android.action.INSTALL_ENVIRONMENT"
        private const val ACTION_CANCEL_INSTALL = "com.khalinux.android.action.CANCEL_INSTALL"
        private const val INSTALL_CHANNEL_ID = "khalinux_install_progress"
        private const val NOTIFICATION_CHANNEL_ID = "khalinux_terminal_session"
        private const val NOTIFICATION_ID = 35
        private const val ROOTFS_RELEASE_TAG = "rootfs-bookworm-20261122-v1"
        private const val ROOTFS_MANIFEST_SHA256 = "9c9c167894ca958a627b607062d2b0b2cb61cc0edd57ed8dc17e611125ce9db2"
        private const val ROOTFS_ARCHIVE_SHA256 = "bcd272a77f6f081408e1b60d9e9ed65ad9c23678b8892d334b5e051bf61613e8"
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
