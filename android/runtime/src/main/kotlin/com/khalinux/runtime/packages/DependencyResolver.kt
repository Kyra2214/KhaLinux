package com.khalinux.runtime.packages

/** Comparação de versões no estilo Debian (epoch:upstream-revisão; `~` ordena antes de tudo). */
object PackageVersion : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val (ea, ra) = split(a)
        val (eb, rb) = split(b)
        if (ea != eb) return ea.compareTo(eb)
        val (ua, rva) = revision(ra)
        val (ub, rvb) = revision(rb)
        val upstream = compareFragment(ua, ub)
        return if (upstream != 0) upstream else compareFragment(rva, rvb)
    }

    private fun split(v: String): Pair<Long, String> {
        val i = v.indexOf(':')
        if (i < 0) return 0L to v
        return (v.substring(0, i).toLongOrNull() ?: 0L) to v.substring(i + 1)
    }

    private fun revision(v: String): Pair<String, String> {
        val i = v.lastIndexOf('-')
        return if (i < 0) v to "" else v.substring(0, i) to v.substring(i + 1)
    }

    private fun order(c: Char?): Int = when {
        c == null -> 0
        c == '~' -> -1
        c.isLetter() -> c.code
        else -> c.code + 256
    }

    private fun compareFragment(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length || j < b.length) {
            while ((i < a.length && !a[i].isDigit()) || (j < b.length && !b[j].isDigit())) {
                val oa = order(a.getOrNull(i).takeIf { it?.isDigit() != true })
                val ob = order(b.getOrNull(j).takeIf { it?.isDigit() != true })
                if (oa != ob) return oa.compareTo(ob)
                if (i < a.length && !a[i].isDigit()) i++
                if (j < b.length && !b[j].isDigit()) j++
            }
            var na = 0L
            var nb = 0L
            while (i < a.length && a[i].isDigit()) { na = na * 10 + (a[i] - '0'); i++ }
            while (j < b.length && b[j].isDigit()) { nb = nb * 10 + (b[j] - '0'); j++ }
            if (na != nb) return na.compareTo(nb)
        }
        return 0
    }
}

/** Plano de instalação: `toInstall` já está em ordem (dependências antes de dependentes). */
data class ResolutionPlan(val toInstall: List<PackageManifest>, val alreadySatisfied: List<String>)

class DependencyResolutionException(message: String) : Exception(message)

/**
 * Resolve dependências sobre um [PackageCatalog] (uma versão por pacote).
 * Falha — sem plano parcial — se faltar pacote, houver ciclo, a versão mínima não for atendida
 * ou algum pacote for incompatível com a arquitetura do dispositivo.
 */
class DependencyResolver(
    private val catalog: PackageCatalog,
    private val deviceArchitecture: PackageArchitecture = PackageArchitecture.ARM64
) {
    /** @param installed mapa nome → versão instalada. */
    fun resolve(requested: List<String>, installed: Map<String, String> = emptyMap()): ResolutionPlan {
        val order = LinkedHashMap<String, PackageManifest>()
        val satisfied = LinkedHashSet<String>()
        val visiting = ArrayList<String>()

        fun visit(name: String, minVersion: String?, requiredBy: String?) {
            val manifest = catalog.find(name)
                ?: throw DependencyResolutionException("Pacote '$name' não existe no catálogo" + (requiredBy?.let { " (exigido por $it)" } ?: ""))
            val installedVersion = installed[manifest.name]
            if (installedVersion != null && (minVersion == null || PackageVersion.compare(installedVersion, minVersion) >= 0)) {
                satisfied += manifest.name
                return
            }
            if (manifest.name in order) {
                checkMin(manifest, minVersion, requiredBy)
                return
            }
            if (manifest.name in visiting) {
                val cycle = visiting.dropWhile { it != manifest.name } + manifest.name
                throw DependencyResolutionException("Dependência circular: ${cycle.joinToString(" → ")}")
            }
            if (!manifest.isCompatibleWith(deviceArchitecture)) {
                throw DependencyResolutionException("'${manifest.name}' não é compatível com ${deviceArchitecture.id}")
            }
            checkMin(manifest, minVersion, requiredBy)
            visiting += manifest.name
            manifest.dependencies.forEach { visit(it.name, it.minVersion, manifest.name) }
            visiting.removeAt(visiting.lastIndex)
            order[manifest.name] = manifest
        }

        requested.distinct().forEach { visit(it.lowercase(), null, null) }
        return ResolutionPlan(order.values.toList(), satisfied.toList())
    }

    /** Pacotes instalados que dependem (direta ou indiretamente) de [name]; usado pela remoção (4.5). */
    fun dependents(name: String, installed: Map<String, String>): List<String> {
        val result = LinkedHashSet<String>()
        fun walk(target: String) {
            for (pkg in catalog.packages) {
                if (pkg.name in installed && pkg.dependencies.any { it.name == target } && result.add(pkg.name)) walk(pkg.name)
            }
        }
        walk(name.lowercase())
        return result.toList()
    }

    private fun checkMin(manifest: PackageManifest, minVersion: String?, requiredBy: String?) {
        if (minVersion != null && PackageVersion.compare(manifest.version, minVersion) < 0) {
            throw DependencyResolutionException(
                "'${manifest.name}' ${manifest.version} < versão mínima $minVersion" + (requiredBy?.let { " (exigida por $it)" } ?: "")
            )
        }
    }
}
