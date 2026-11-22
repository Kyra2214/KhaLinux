package com.khalinux.runtime.packages

import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageManagerFlowTest {
    private val dir: File = Files.createTempDirectory("khalinux-pkg").toFile()

    @After fun cleanup() { dir.deleteRecursively() }

    private fun parts(catalog: PackageCatalog, runner: FakeRunner): Triple<InstalledPackageStore, PackageHistoryStore, PackageManager> {
        val store = InstalledPackageStore(File(dir, "installed.tsv"))
        val history = PackageHistoryStore(File(dir, "history.log"))
        val installer = PackageInstaller(catalog, store, runner, File(dir, "rootfs"), File(dir, "tmp"), clock = { 1000L })
        val manager = PackageManager(
            catalog, store, installer, PackageRemover(catalog, store, runner, File(dir, "rootfs")),
            PackageUpdater(catalog, store, installer), history, clock = { 2000L }
        )
        return Triple(store, history, manager)
    }

    private val catalog = PackageCatalog(
        "t",
        listOf(apt("app", "1.0", deps = listOf(PackageDependency("lib", null))), apt("lib", "1.0"))
    )

    @Test fun installsInOrderAndRecords() {
        val runner = FakeRunner()
        val (store, history, manager) = parts(catalog, runner)
        val done = manager.install(listOf("app"))
        assertEquals(listOf("lib", "app"), done.map { it.name })
        assertEquals(mapOf("lib" to "1.0", "app" to "1.0"), store.versions())
        assertEquals("DEBIAN_FRONTEND=noninteractive apt-get update", runner.commands.first())
        assertTrue(runner.commands.any { it.contains("'app=1.0'") })
        assertEquals(2, history.recent().size)
        assertTrue(history.recent().all { it.success })
    }

    @Test fun failureIsAuditedAndNotRecorded() {
        val runner = FakeRunner(exitCode = 100)
        val (store, history, manager) = parts(catalog, runner)
        assertFailsWith<PackageInstallException> { manager.install(listOf("lib")) }
        assertTrue(store.all().isEmpty())
        val entry = history.recent().single()
        assertFalse(entry.success)
    }

    @Test fun removeBlocksOnDependentsAndProtected() {
        val (store, _, manager) = parts(catalog, FakeRunner())
        manager.install(listOf("app"))
        assertFailsWith<PackageRemoveException> { manager.remove("lib") }
        manager.remove("app")
        manager.remove("lib")
        assertTrue(store.all().isEmpty())
        assertFailsWith<PackageRemoveException> { manager.remove("bash") }
        assertFailsWith<PackageRemoveException> { manager.remove("nunca-instalado") }
    }

    @Test fun updateUsesNewerCatalogVersion() {
        val runner = FakeRunner()
        val (store, _, _) = parts(catalog, runner)
        store.put(InstalledPackage("lib", "0.9", PackageSourceType.DEBIAN_APT, null, 1L))
        val newer = PackageCatalog("t2", listOf(apt("lib", "1.0")))
        val s2 = InstalledPackageStore(File(dir, "installed.tsv"))
        val installer = PackageInstaller(newer, s2, runner, File(dir, "rootfs"), File(dir, "tmp"))
        val updater = PackageUpdater(newer, s2, installer)
        assertEquals(listOf(PackageUpdate("lib", "0.9", "1.0")), updater.available())
        updater.update()
        assertEquals("1.0", s2.versions()["lib"])
        assertTrue(updater.available().isEmpty())
        assertFailsWith<PackageUpdateException> { updater.update(listOf("lib")) }
    }

    @Test fun storesIgnoreCorruptLinesAndHistoryCompacts() {
        File(dir, "installed.tsv").writeText("lixo\nlib\t1.0\t-\tdebian-apt\t5\n")
        assertEquals(setOf("lib"), InstalledPackageStore(File(dir, "installed.tsv")).all().keys)
        val history = PackageHistoryStore(File(dir, "h.log"), maxEntries = 4)
        repeat(20) { history.append(PackageHistoryEntry(it.toLong(), PackageAction.INSTALL, "p$it", "1", true, "a\tb\nc")) }
        assertTrue(history.recent(100).size <= 5)
        assertEquals("p19", history.recent().first().packageName)
    }
}
