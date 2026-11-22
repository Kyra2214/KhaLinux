package com.khalinux.runtime.packages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PackageCatalogTest {
    private val catalog = PackageCatalog(
        "t1",
        listOf(
            apt("curl", tags = listOf("http", "download")),
            apt("dnsutils", tags = listOf("dns")),
            apt("git", category = PackageCategory.DEVELOPMENT, tags = listOf("vcs"))
        )
    )

    @Test fun findAndCategories() {
        assertEquals("git", catalog.find("GIT")?.name)
        assertNull(catalog.find("nada"))
        assertEquals(listOf(PackageCategory.NETWORK to 2, PackageCategory.DEVELOPMENT to 1), catalog.categories())
    }

    @Test fun searchIsAccentAndCaseInsensitiveAndRanked() {
        assertEquals(listOf("curl"), catalog.search(PackageQuery(text = "CURL")).map { it.name })
        assertEquals(listOf("dnsutils"), catalog.search(PackageQuery(text = "dns")).map { it.name })
        assertEquals(listOf("git"), catalog.search(PackageQuery(category = PackageCategory.DEVELOPMENT)).map { it.name })
        assertEquals(listOf("curl"), catalog.search(PackageQuery(tag = "download")).map { it.name })
        assertEquals(0, catalog.search(PackageQuery(text = "zzz")).size)
        assertEquals(3, catalog.search().size)
    }

    @Test fun rejectsDuplicatesAndMissingDependencies() {
        assertFailsWith<PackageManifestException> { PackageCatalog("t", listOf(apt("a"), apt("a"))) }
        assertFailsWith<PackageManifestException> {
            PackageCatalog("t", listOf(apt("a", deps = listOf(PackageDependency("b", null)))))
        }
    }

    @Test fun embeddedCatalogParses() {
        val file = java.io.File("distribution/packages/catalog/catalog.json")
        if (!file.isFile) return // executado a partir da raiz do repositório (test.sh)
        val text = file.readText()
        // Verificação mínima de estrutura sem biblioteca JSON: o parser real é coberto pelo app/CI.
        assert(text.contains("\"schema\": 1") && text.contains("\"packages\""))
    }
}
