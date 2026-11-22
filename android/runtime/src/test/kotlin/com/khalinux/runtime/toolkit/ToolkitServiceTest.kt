package com.khalinux.runtime.toolkit

import com.khalinux.runtime.packages.InstalledPackage
import com.khalinux.runtime.packages.InstalledPackageStore
import com.khalinux.runtime.packages.PackageCatalog
import com.khalinux.runtime.packages.PackageCommandOutcome
import com.khalinux.runtime.packages.PackageCommandRunner
import com.khalinux.runtime.packages.PackageSourceType
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolkitServiceTest {
    private val dir: File = Files.createTempDirectory("toolkit-test").toFile()
    private val ran = mutableListOf<String>()
    private var exit = 0
    private val runner = PackageCommandRunner { cmd, _: Duration -> ran += cmd; PackageCommandOutcome(exit, "saida\tde\nteste") }

    private fun descriptor(id: String, auth: Boolean) = ToolDescriptor(
        id = id, packageName = id, title = id.uppercase(), group = ToolGroup.WEB, summary = "resumo",
        commands = listOf(id), checkCommand = "$id --version", requiresAuthorization = auth,
        rootLimitations = emptyList(), doc = "docs/toolkit/$id.md"
    )

    private fun service(clock: () -> Long = { 1_000L }): Pair<ToolkitService, InstalledPackageStore> {
        val catalog = PackageCatalog("t", listOf(com.khalinux.runtime.packages.apt("openx"), com.khalinux.runtime.packages.apt("safex")))
        val registry = ToolkitRegistry(listOf(descriptor("openx", true), descriptor("safex", false)), catalog)
        val guard = ToolkitGuard(registry, AuthorizationStore(File(dir, "auth.tsv")), Duration.ofHours(1), clock)
        val installed = InstalledPackageStore(File(dir, "installed.tsv"))
        return ToolkitService(registry, guard, runner, installed, VerificationStore(File(dir, "ver.tsv")), clock) to installed
    }

    private fun install(store: InstalledPackageStore, name: String) =
        store.put(InstalledPackage(name, "1.0", PackageSourceType.DEBIAN_APT, null, 1L))

    @Test fun verifyNeedsInstalledPackageAndRecordsResult() {
        val (svc, store) = service()
        assertFailsWith<ToolkitBlockedException> { svc.verify("safex") }
        install(store, "safex")
        val v = svc.verify("safex")
        assertTrue(v.success)
        assertEquals("safex --version", ran.single())
        assertFalse(v.output.contains('\t') || v.output.contains('\n'))
        assertEquals(v, svc.status().first { it.tool.id == "safex" }.lastVerification)
        exit = 1
        assertFalse(svc.verify("safex").success)
    }

    @Test fun runIsBlockedUntilScopeIsAcknowledged() {
        val (svc, store) = service()
        install(store, "openx")
        assertFailsWith<ToolkitBlockedException> { svc.run("openx", "openx -p 80 alvo.local") }
        assertTrue(ran.isEmpty())
        svc.acknowledge("openx", "meu laboratorio local")
        assertTrue(svc.status().first { it.tool.id == "openx" }.allowed)
        svc.run("openx", "openx -p 80 alvo.local")
        assertEquals(listOf("openx -p 80 alvo.local"), ran)
        svc.revoke("openx")
        assertFailsWith<ToolkitBlockedException> { svc.run("openx", "openx x") }
    }

    @Test fun acknowledgementExpires() {
        var now = 1_000L
        val (svc, store) = service { now }
        install(store, "openx")
        svc.acknowledge("openx", "meu laboratorio")
        now += Duration.ofHours(2).toMillis()
        assertFailsWith<ToolkitBlockedException> { svc.run("openx", "openx x") }
    }

    @Test fun runRejectsForeignCommandsAndShellMetacharacters() {
        val (svc, store) = service()
        install(store, "safex"); install(store, "openx")
        assertFailsWith<ToolkitBlockedException> { svc.run("safex", "rm -rf /") }
        assertFailsWith<ToolkitBlockedException> { svc.run("safex", "safex x; rm y") }
        assertFailsWith<ToolkitBlockedException> { svc.run("safex", "safex \$(id)") }
        assertFailsWith<IllegalArgumentException> { svc.run("nada", "nada") }
        assertTrue(ran.isEmpty())
        svc.run("safex", "safex -h")
        assertEquals(1, ran.size)
    }

    @Test fun verificationStoreRoundTrips() {
        val store = VerificationStore(File(dir, "v2.tsv"))
        assertNull(store.all()["a"])
        store.put(ToolVerification("a", 5L, true, "ok"))
        store.put(ToolVerification("a", 6L, false, "falhou"))
        assertEquals(6L, assertNotNull(store.all()["a"]).epochMillis)
        assertEquals(1, store.all().size)
    }
}
