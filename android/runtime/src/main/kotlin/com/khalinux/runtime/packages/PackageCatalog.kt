package com.khalinux.runtime.packages

import java.text.Normalizer
import java.util.Locale

/** Critérios de pesquisa; todos opcionais e combinados com E. */
data class PackageQuery(
    val text: String = "",
    val category: PackageCategory? = null,
    val tag: String? = null,
    val architecture: PackageArchitecture? = null,
    val validation: PackageValidation? = null
)

/**
 * Catálogo imutável de manifestos. Um manifesto por nome de pacote (uma versão por vez nesta fase).
 * A pesquisa ignora caixa e acentos e ordena por relevância (nome exato > prefixo > nome > tag > texto).
 */
class PackageCatalog(val catalogVersion: String, packages: List<PackageManifest>) {
    val packages: List<PackageManifest> = packages.sortedBy { it.name }

    init {
        if (catalogVersion.isBlank()) throw PackageManifestException("catalog_version ausente")
        val duplicated = packages.groupBy { it.name }.filterValues { it.size > 1 }.keys
        if (duplicated.isNotEmpty()) throw PackageManifestException("Pacotes duplicados no catálogo: ${duplicated.sorted()}")
        val names = packages.map { it.name }.toSet()
        for (pkg in packages) for (dep in pkg.dependencies) {
            if (dep.name !in names) throw PackageManifestException("${pkg.name} depende de '${dep.name}', ausente no catálogo")
        }
    }

    fun find(name: String): PackageManifest? = packages.firstOrNull { it.name == name.lowercase(Locale.ROOT) }

    /** Categorias com ao menos um pacote, com contagem, em ordem de declaração do enum. */
    fun categories(): List<Pair<PackageCategory, Int>> {
        val counts = packages.groupingBy { it.category }.eachCount()
        return PackageCategory.values().mapNotNull { c -> counts[c]?.let { c to it } }
    }

    fun search(query: PackageQuery = PackageQuery()): List<PackageManifest> {
        val terms = normalize(query.text).split(' ').filter { it.isNotEmpty() }
        val tag = query.tag?.lowercase(Locale.ROOT)
        return packages.asSequence()
            .filter { query.category == null || it.category == query.category }
            .filter { tag == null || tag in it.tags }
            .filter { query.architecture == null || it.isCompatibleWith(query.architecture) }
            .filter { query.validation == null || it.validation == query.validation }
            .mapNotNull { pkg -> score(pkg, terms)?.let { pkg to it } }
            .sortedWith(compareByDescending<Pair<PackageManifest, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
            .toList()
    }

    private fun score(pkg: PackageManifest, terms: List<String>): Int? {
        if (terms.isEmpty()) return 0
        val name = normalize(pkg.name)
        val title = normalize(pkg.title)
        val tags = pkg.tags.map(::normalize)
        val description = normalize(pkg.description)
        var total = 0
        for (term in terms) {
            total += when {
                name == term -> 100
                name.startsWith(term) -> 60
                name.contains(term) || title.contains(term) -> 40
                tags.any { it == term } -> 30
                tags.any { it.contains(term) } -> 20
                description.contains(term) -> 10
                else -> return null
            }
        }
        return total
    }

    companion object {
        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .trim()
    }
}

/** Converte a estrutura decodificada de `catalog.json` em [PackageCatalog]. */
object PackageCatalogParser {
    private val KEYS = setOf("schema", "catalog_version", "description", "packages")

    fun parse(root: Map<String, Any?>): PackageCatalog {
        val unknown = root.keys - KEYS
        if (unknown.isNotEmpty()) throw PackageManifestException("Campos desconhecidos no catálogo: ${unknown.sorted()}")
        val schema = (root["schema"] as? Number)?.toInt()
        if (schema != PackageManifest.SCHEMA_VERSION) throw PackageManifestException("Schema de catálogo não suportado: $schema")
        val version = root["catalog_version"] as? String ?: throw PackageManifestException("catalog_version ausente")
        val list = root["packages"] as? List<*> ?: throw PackageManifestException("Lista 'packages' ausente")
        val manifests = list.map { entry ->
            @Suppress("UNCHECKED_CAST")
            val map = entry as? Map<String, Any?> ?: throw PackageManifestException("Entrada de pacote malformada")
            PackageManifestParser.parse(map)
        }
        return PackageCatalog(version, manifests)
    }
}
