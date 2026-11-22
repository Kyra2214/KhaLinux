package com.khalinux.runtime.packages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackageVersionTest {
    @Test fun ordering() {
        assertTrue(PackageVersion.compare("1.10", "1.9") > 0)
        assertTrue(PackageVersion.compare("1:1.0", "2.0") > 0)
        assertTrue(PackageVersion.compare("1.0~rc1", "1.0") < 0)
        assertTrue(PackageVersion.compare("1.0-2", "1.0-10") < 0)
        assertEquals(0, PackageVersion.compare("2.6.1", "2.6.1"))
        assertTrue(PackageVersion.compare("3.11.2-1+b1", "3.11.2-1") > 0)
    }
}

class DependencyResolverTest {
    private fun dep(n: String, min: String? = null) = PackageDependency(n, min)

    @Test fun ordersDependenciesFirst() {
        val catalog = PackageCatalog("t", listOf(apt("pa", deps = listOf(dep("pb"))), apt("pb", deps = listOf(dep("pc"))), apt("pc")))
        val plan = DependencyResolver(catalog).resolve(listOf("pa"))
        assertEquals(listOf("pc", "pb", "pa"), plan.toInstall.map { it.name })
    }

    @Test fun skipsSatisfiedInstalledPackages() {
        val catalog = PackageCatalog("t", listOf(apt("pa", deps = listOf(dep("pb", "1.0"))), apt("pb", "2.0")))
        val plan = DependencyResolver(catalog).resolve(listOf("pa"), mapOf("pb" to "1.5"))
        assertEquals(listOf("pa"), plan.toInstall.map { it.name })
        assertEquals(listOf("pb"), plan.alreadySatisfied)
    }

    @Test fun detectsCyclesMissingAndMinVersion() {
        val cyc = PackageCatalog("t", listOf(apt("pa", deps = listOf(dep("pb"))), apt("pb", deps = listOf(dep("pa")))))
        assertFailsWith<DependencyResolutionException> { DependencyResolver(cyc).resolve(listOf("pa")) }
        val catalog = PackageCatalog("t", listOf(apt("pa", deps = listOf(dep("pb", "3.0"))), apt("pb", "2.0")))
        assertFailsWith<DependencyResolutionException> { DependencyResolver(catalog).resolve(listOf("pa")) }
        assertFailsWith<DependencyResolutionException> { DependencyResolver(catalog).resolve(listOf("zzz")) }
    }

    @Test fun rejectsIncompatibleArchitecture() {
        val onlyAll = apt("pa").copy(architecture = PackageArchitecture.ARM64)
        val catalog = PackageCatalog("t", listOf(onlyAll))
        assertFailsWith<DependencyResolutionException> {
            DependencyResolver(catalog, PackageArchitecture.ALL).resolve(listOf("pa"))
        }
    }

    @Test fun dependentsAreTransitive() {
        val catalog = PackageCatalog("t", listOf(apt("pa", deps = listOf(dep("pb"))), apt("pb", deps = listOf(dep("pc"))), apt("pc")))
        val installed = mapOf("pa" to "1.0", "pb" to "1.0", "pc" to "1.0")
        assertEquals(setOf("pa", "pb"), DependencyResolver(catalog).dependents("pc", installed).toSet())
    }
}
