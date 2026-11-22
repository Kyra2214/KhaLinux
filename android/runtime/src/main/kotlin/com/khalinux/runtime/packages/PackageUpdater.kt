package com.khalinux.runtime.packages

data class PackageUpdate(val name: String, val installedVersion: String, val candidateVersion: String)

class PackageUpdateException(message: String) : Exception(message)

/**
 * Atualiza pacotes registrados para a versão do catálogo.
 *
 * A atualização reutiliza [PackageInstaller] (mesmas verificações de SHA-256/tamanho, troca atômica
 * e registro somente após sucesso). Dependências cuja versão mínima não é atendida entram no plano
 * automaticamente. Não há rebaixamento de versão.
 */
class PackageUpdater(
    private val catalog: PackageCatalog,
    private val store: InstalledPackageStore,
    private val installer: PackageInstaller
) {
    /** Pacotes instalados cuja versão no catálogo é estritamente maior. */
    fun available(): List<PackageUpdate> =
        store.all().values.sortedBy { it.name }.mapNotNull { installed ->
            val candidate = catalog.find(installed.name) ?: return@mapNotNull null
            if (PackageVersion.compare(candidate.version, installed.version) > 0) {
                PackageUpdate(installed.name, installed.version, candidate.version)
            } else null
        }

    /** @param names nomes a atualizar; nulo atualiza todos os disponíveis. */
    fun update(
        names: List<String>? = null,
        isCancelled: () -> Boolean = { false },
        onEvent: (PackageInstallEvent) -> Unit = {}
    ): List<InstalledPackage> {
        val available = available().associateBy { it.name }
        val targets = (names?.map { it.lowercase() } ?: available.keys.toList()).distinct()
        for (name in targets) {
            if (name !in store.all()) throw PackageUpdateException("'$name' não está instalado")
            if (name !in available) throw PackageUpdateException("'$name' já está na versão mais recente do catálogo")
        }
        if (targets.isEmpty()) return emptyList()
        return installer.install(targets, isCancelled, onEvent, forceReinstall = targets.toSet())
    }
}
