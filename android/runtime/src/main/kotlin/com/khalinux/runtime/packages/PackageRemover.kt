package com.khalinux.runtime.packages

import java.io.File
import java.time.Duration

class PackageRemoveException(val packageName: String, message: String, cause: Throwable? = null) :
    Exception("Falha ao remover '$packageName': $message", cause)

/**
 * Remove pacotes registrados em [InstalledPackageStore].
 *
 * - Só remove o que o gerenciador instalou (presente no registro).
 * - Recusa remover pacotes protegidos do sistema base e pacotes dos quais outros pacotes
 *   instalados dependem (a lista é informada na exceção).
 * - `debian-apt`: `apt-get remove` (sem `purge` nem `autoremove`, para não apagar configuração
 *   ou pacotes que o usuário possa usar); origem direta: apaga `/opt/khalinux/<nome>` com checagem
 *   de que o caminho canônico permanece dentro do diretório do gerenciador.
 * - O registro só é alterado depois que a remoção do arquivo/pacote termina com sucesso.
 */
class PackageRemover(
    private val catalog: PackageCatalog,
    private val store: InstalledPackageStore,
    private val runner: PackageCommandRunner,
    private val rootfsDirectory: File,
    private val protectedPackages: Set<String> = DEFAULT_PROTECTED,
    private val aptTimeout: Duration = Duration.ofMinutes(10)
) {
    fun remove(name: String, onEvent: (PackageInstallEvent) -> Unit = {}) {
        val key = name.lowercase()
        if (key in protectedPackages) throw PackageRemoveException(key, "pacote protegido do sistema base")
        val record = store.all()[key] ?: throw PackageRemoveException(key, "não foi instalado pelo gerenciador")
        val dependents = DependencyResolver(catalog).dependents(key, store.versions())
        if (dependents.isNotEmpty()) {
            throw PackageRemoveException(key, "necessário para: ${dependents.joinToString(", ")}")
        }
        try {
            when (record.sourceType) {
                PackageSourceType.DEBIAN_APT -> {
                    val aptName = catalog.find(key)?.source?.aptName ?: key
                    val outcome = runner.run("DEBIAN_FRONTEND=noninteractive apt-get remove -y '$aptName'", aptTimeout)
                    if (outcome.exitCode != 0) {
                        throw PackageRemoveException(key, "apt-get remove falhou (${outcome.exitCode}): ${outcome.output.takeLast(300)}")
                    }
                }
                else -> deleteDirect(key)
            }
            store.remove(key)
        } catch (e: PackageRemoveException) {
            throw e
        } catch (e: Exception) {
            throw PackageRemoveException(key, e.message ?: e.javaClass.simpleName, e)
        }
        onEvent(PackageInstallEvent(PackageInstallStep.RECORDED, key))
    }

    private fun deleteDirect(name: String) {
        val base = File(rootfsDirectory, "opt/khalinux").canonicalFile
        val target = File(base, name).canonicalFile
        if (target.parentFile != base) throw PackageRemoveException(name, "caminho fora do diretório do gerenciador")
        if (target.exists() && !target.deleteRecursively()) throw PackageRemoveException(name, "não foi possível apagar ${target.name}")
    }

    companion object {
        /** Núcleo do RootFS: remover quebraria o runtime ou o próprio gerenciador. */
        val DEFAULT_PROTECTED: Set<String> = setOf("apt", "bash", "coreutils", "ca-certificates", "dpkg")
    }
}
