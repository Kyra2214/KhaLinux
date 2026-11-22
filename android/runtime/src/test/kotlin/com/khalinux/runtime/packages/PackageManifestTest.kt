package com.khalinux.runtime.packages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageManifestTest {
    private fun root(): MutableMap<String, Any?> = mutableMapOf(
        "schema" to 1, "name" to "nmap", "version" to "7.93", "title" to "Nmap", "description" to "Scanner",
        "category" to "rede", "architecture" to "arm64",
        "source" to mapOf("type" to "debian-apt", "apt_name" to "nmap"),
        "verification" to emptyMap<String, Any?>(), "validation" to "unverified",
        "limitations" to listOf("Sem raw sockets"), "tags" to listOf("scanner")
    )

    @Test fun parsesValidManifest() {
        val m = PackageManifestParser.parse(root())
        assertEquals("nmap@7.93", m.key)
        assertTrue(m.isCompatibleWith(PackageArchitecture.ARM64))
    }

    @Test fun rejectsUnknownFields() {
        assertFailsWith<PackageManifestException> { PackageManifestParser.parse(root().apply { put("extra", 1) }) }
    }

    @Test fun rejectsNonHttpsDirectSource() {
        assertFailsWith<PackageManifestException> {
            PackageSource(PackageSourceType.GITHUB_RELEASE, "http://example.com/a.tar.gz", null)
        }
        assertFailsWith<PackageManifestException> {
            PackageSource(PackageSourceType.GITHUB_RELEASE, "https://user:pw@example.com/a.tar.gz", null)
        }
    }

    @Test fun directArtifactRequiresShaAndSize() {
        val source = PackageSource(PackageSourceType.GITHUB_RELEASE, "https://example.com/a.tar.gz", null)
        assertFailsWith<PackageManifestException> {
            apt("tool").copy(source = source, verification = PackageVerification(null, null), downloadBytes = 10)
        }
        assertFailsWith<PackageManifestException> {
            apt("tool").copy(source = source, verification = PackageVerification("a".repeat(64), null), downloadBytes = null)
        }
        apt("tool").copy(source = source, verification = PackageVerification("a".repeat(64), null), downloadBytes = 10)
    }

    @Test fun verifiedNeedsEvidenceAndKnownLimitedNeedsLimitations() {
        assertFailsWith<PackageManifestException> { apt("tool", validation = PackageValidation.VERIFIED) }
        assertFailsWith<PackageManifestException> { apt("tool", validation = PackageValidation.KNOWN_LIMITED) }
        apt("tool", validation = PackageValidation.KNOWN_LIMITED, limitations = listOf("x"))
    }

    @Test fun rejectsSelfAndDuplicateDependencies() {
        assertFailsWith<PackageManifestException> { apt("tool", deps = listOf(PackageDependency("tool", null))) }
        assertFailsWith<PackageManifestException> {
            apt("tool", deps = listOf(PackageDependency("a", null), PackageDependency("a", null)))
        }
    }

    @Test fun architectureCompatibility() {
        assertFalse(apt("tool").copy(architecture = PackageArchitecture.ARM64).isCompatibleWith(PackageArchitecture.ALL))
        assertTrue(apt("tool").copy(architecture = PackageArchitecture.ALL).isCompatibleWith(PackageArchitecture.ARM64))
    }
}
