package com.khalinux.runtime.packages

/**
 * Fachada do gerenciador de pacotes: catálogo + dependências + instalação + remoção + atualização,
 * registrando cada operação (inclusive falhas) no [PackageHistoryStore].
 * A UI e o app falam apenas com esta classe.
 */
class PackageManager(
    val catalog: PackageCatalog,
    private val store: InstalledPackageStore,
    private val installer: PackageInstaller,
    private val remover: PackageRemover,
    private val updater: PackageUpdater,
    private val history: PackageHistoryStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun search(query: PackageQuery = PackageQuery()): List<PackageManifest> = catalog.search(query)
    fun categories(): List<Pair<PackageCategory, Int>> = catalog.categories()
    fun installed(): List<InstalledPackage> = store.all().values.sortedBy { it.name }
    fun isInstalled(name: String): Boolean = name.lowercase() in store.all()
    fun availableUpdates(): List<PackageUpdate> = updater.available()
    fun history(limit: Int = 50): List<PackageHistoryEntry> = history.recent(limit)

    fun install(
        names: List<String>,
        isCancelled: () -> Boolean = { false },
        onEvent: (PackageInstallEvent) -> Unit = {}
    ): List<InstalledPackage> = audited(PackageAction.INSTALL, names) {
        installer.install(names, isCancelled, onEvent)
    }

    fun update(
        names: List<String>? = null,
        isCancelled: () -> Boolean = { false },
        onEvent: (PackageInstallEvent) -> Unit = {}
    ): List<InstalledPackage> = audited(PackageAction.UPDATE, names ?: updater.available().map { it.name }) {
        updater.update(names, isCancelled, onEvent)
    }

    fun remove(name: String, onEvent: (PackageInstallEvent) -> Unit = {}) {
        val key = name.lowercase()
        val version = store.all()[key]?.version
        try {
            remover.remove(key, onEvent)
            history.append(PackageHistoryEntry(clock(), PackageAction.REMOVE, key, version, true, ""))
        } catch (e: Exception) {
            history.append(PackageHistoryEntry(clock(), PackageAction.REMOVE, key, version, false, e.message ?: e.javaClass.simpleName))
            throw e
        }
    }

    private fun audited(action: PackageAction, requested: List<String>, block: () -> List<InstalledPackage>): List<InstalledPackage> {
        try {
            val done = block()
            done.forEach { history.append(PackageHistoryEntry(clock(), action, it.name, it.version, true, "")) }
            return done
        } catch (e: Exception) {
            // Pacotes concluídos antes da falha já estão no registro; o histórico registra a falha da operação.
            val target = requested.joinToString(",").ifEmpty { "-" }
            history.append(PackageHistoryEntry(clock(), action, target, null, false, e.message ?: e.javaClass.simpleName))
            throw e
        }
    }
}
