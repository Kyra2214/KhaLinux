package com.khalinux.runtime.packages

import com.khalinux.runtime.install.InstallCancelledException
import com.khalinux.runtime.install.ResumableDownloader
import com.khalinux.runtime.install.RootfsInstaller
import java.io.File
import java.net.URL
import java.time.Duration
import java.util.Locale

/** Resultado de um comando executado dentro do RootFS. */
data class PackageCommandOutcome(val exitCode: Int, val output: String)

/** Porta para executar comandos no RootFS (o adaptador sobre `LinuxRuntime` é do app, submódulo 4.7). */
fun interface PackageCommandRunner {
    fun run(shellCommand: String, timeout: Duration): PackageCommandOutcome
}

enum class PackageInstallStep { RESOLVING, UPDATING_INDEX, DOWNLOADING, VERIFYING, EXTRACTING, INSTALLING, RECORDED }

data class PackageInstallEvent(
    val step: PackageInstallStep,
    val packageName: String?,
    /** Posição (1-based) do pacote no plano e total, quando aplicável. */
    val index: Int = 0,
    val total: Int = 0,
    val bytes: Long = 0,
    val totalBytes: Long? = null
)

class PackageInstallException(val packageName: String, message: String, cause: Throwable? = null) :
    Exception("Falha ao instalar '$packageName': $message", cause)

/**
 * Instala pacotes do catálogo na ordem do plano de dependências.
 *
 * - `debian-apt`: `apt-get install` de `nome=versão` exata no RootFS (índice atualizado uma vez por chamada).
 * - `github-release`/`khalinux-repository`: download HTTPS retomável, tamanho e SHA-256 conferidos,
 *   extração protegida (tar.gz) em `/opt/khalinux/<nome>` com troca atômica.
 * - Cada pacote só é registrado em [InstalledPackageStore] depois de concluído; em falha, os anteriores
 *   permanecem registrados e o plano é interrompido (nenhum registro para o pacote que falhou).
 */
class PackageInstaller(
    private val catalog: PackageCatalog,
    private val store: InstalledPackageStore,
    private val runner: PackageCommandRunner,
    private val rootfsDirectory: File,
    private val workDirectory: File,
    private val deviceArchitecture: PackageArchitecture = PackageArchitecture.ARM64,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val maxDownloadBytes: Long = 500_000_000L,
    private val aptTimeout: Duration = Duration.ofMinutes(30),
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun install(
        requested: List<String>,
        isCancelled: () -> Boolean = { false },
        onEvent: (PackageInstallEvent) -> Unit = {},
        /** Pacotes tratados como não instalados no plano (atualização/reinstalação, submódulo 4.6). */
        forceReinstall: Set<String> = emptySet()
    ): List<InstalledPackage> {
        onEvent(PackageInstallEvent(PackageInstallStep.RESOLVING, null))
        val plan = try {
            DependencyResolver(catalog, deviceArchitecture).resolve(requested, store.versions() - forceReinstall)
        } catch (e: DependencyResolutionException) {
            throw PackageInstallException(requested.joinToString(","), e.message ?: "dependências", e)
        }
        val installedNow = mutableListOf<InstalledPackage>()
        var indexUpdated = false
        plan.toInstall.forEachIndexed { i, manifest ->
            if (isCancelled()) throw InstallCancelledException()
            val position = i + 1
            val total = plan.toInstall.size
            try {
                val sha = when (manifest.source.type) {
                    PackageSourceType.DEBIAN_APT -> {
                        if (!indexUpdated) {
                            onEvent(PackageInstallEvent(PackageInstallStep.UPDATING_INDEX, manifest.name, position, total))
                            requireOk(runner.run("DEBIAN_FRONTEND=noninteractive apt-get update", aptTimeout), "apt-get update")
                            indexUpdated = true
                        }
                        onEvent(PackageInstallEvent(PackageInstallStep.INSTALLING, manifest.name, position, total))
                        installFromApt(manifest)
                        null
                    }
                    else -> installDirect(manifest, position, total, isCancelled, onEvent)
                }
                val record = InstalledPackage(manifest.name, manifest.version, manifest.source.type, sha, clock())
                store.put(record)
                installedNow += record
                onEvent(PackageInstallEvent(PackageInstallStep.RECORDED, manifest.name, position, total))
            } catch (e: InstallCancelledException) {
                throw e
            } catch (e: PackageInstallException) {
                throw e
            } catch (e: Exception) {
                throw PackageInstallException(manifest.name, e.message ?: e.javaClass.simpleName, e)
            }
        }
        return installedNow
    }

    private fun installDirect(
        manifest: PackageManifest,
        position: Int,
        total: Int,
        isCancelled: () -> Boolean,
        onEvent: (PackageInstallEvent) -> Unit
    ): String {
        val url = URL(manifest.source.url!!)
        val expectedSha = manifest.verification.sha256!!
        val expectedBytes = manifest.downloadBytes!!
        check(workDirectory.isDirectory || workDirectory.mkdirs()) { "Diretório de trabalho indisponível" }
        val part = File(workDirectory, ".pkg-${manifest.name}-${manifest.version.replace(':', '_')}.download")
        val digest = ResumableDownloader(connectTimeoutMillis, readTimeoutMillis, minOf(maxDownloadBytes, expectedBytes))
            .download(url, part, isCancelled) { done, size ->
                onEvent(PackageInstallEvent(PackageInstallStep.DOWNLOADING, manifest.name, position, total, done, size))
            }
        onEvent(PackageInstallEvent(PackageInstallStep.VERIFYING, manifest.name, position, total))
        val actual = digest.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            part.delete()
            throw PackageInstallException(manifest.name, "SHA-256 divergente: esperado $expectedSha, recebido $actual")
        }
        if (part.length() != expectedBytes) {
            part.delete()
            throw PackageInstallException(manifest.name, "Tamanho divergente: esperado $expectedBytes, recebido ${part.length()}")
        }
        val parent = File(rootfsDirectory, "opt/khalinux")
        check(parent.isDirectory || parent.mkdirs()) { "Não foi possível criar ${parent.path}" }
        val finalDir = File(parent, manifest.name)
        val staging = File(parent, ".${manifest.name}.staging")
        val backup = File(parent, ".${manifest.name}.previous")
        staging.deleteRecursively()
        backup.deleteRecursively()
        check(staging.mkdirs()) { "Não foi possível criar diretório temporário" }
        try {
            onEvent(PackageInstallEvent(PackageInstallStep.EXTRACTING, manifest.name, position, total))
            RootfsInstaller().extractSafely(part, staging, {}, isCancelled)
            if (finalDir.exists()) check(finalDir.renameTo(backup)) { "Não foi possível preservar a versão anterior" }
            if (!staging.renameTo(finalDir)) {
                if (backup.exists()) backup.renameTo(finalDir)
                error("Não foi possível ativar o pacote")
            }
            backup.deleteRecursively()
        } finally {
            staging.deleteRecursively()
            part.delete()
        }
        return actual
    }

    /**
     * `apt-get install nome=versão` exata. Pacotes com binários por arquitetura (hashdeep, chkrootkit, sleuthkit…)
     * costumam ter no Debian um sufixo de recompilação (`+b1`, `+b6`) que não consta da versão do catálogo; nesse caso
     * o apt responde "Version ... was not found". Só nessa situação tentamos a mesma versão com sufixo `+b*`
     * (recompilação da mesma versão; nunca outra versão do pacote).
     */
    private fun installFromApt(manifest: PackageManifest) {
        val spec = "${manifest.source.aptName}=${manifest.version}"
        val base = "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends"
        var outcome = runner.run("$base '$spec'", aptTimeout)
        if (outcome.exitCode != 0 && outcome.output.contains("was not found")) {
            outcome = runner.run("$base '$spec+b*'", aptTimeout)
        }
        requireOk(outcome, "apt-get install $spec")
    }

    private fun requireOk(outcome: PackageCommandOutcome, what: String) {
        if (outcome.exitCode != 0) {
            throw PackageInstallException(what, "código de saída ${outcome.exitCode}: ${outcome.output.takeLast(300)}")
        }
    }
}
