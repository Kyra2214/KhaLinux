package com.khalinux.runtime

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProotLocatorTest {
    private val dir = Files.createTempDirectory("khalinux-locator").toFile()

    @After fun cleanup() { dir.deleteRecursively() }

    private fun executable(name: String): File = File(dir, name).apply { writeText("#!/bin/sh\n"); setExecutable(true) }

    @Test fun filesDirLocatorFindsExecutableOnly() {
        assertNull(FilesDirProotLocator(File(dir, "proot")).locate())
        File(dir, "proot").writeText("x") // sem permissão de execução
        File(dir, "proot").setExecutable(false)
        assertNull(FilesDirProotLocator(File(dir, "proot")).locate())
        val found = FilesDirProotLocator(executable("proot")).locate()
        assertNotNull(found)
        assertEquals(FilesDirProotLocator.SOURCE, found.source)
    }

    @Test fun nativeLocatorSetsLoaderWhenPresent() {
        assertNull(NativeLibProotLocator(dir).locate())
        executable(NativeLibProotLocator.PROOT_LIBRARY)
        assertTrue(NativeLibProotLocator(dir).locate()!!.environment.isEmpty())
        val loader = executable(NativeLibProotLocator.LOADER_LIBRARY)
        assertEquals(loader.absolutePath, NativeLibProotLocator(dir).locate()!!.environment["PROOT_LOADER"])
    }

    @Test fun fallbackPrefersFirstAndDescribesAll() {
        val nativeDir = File(dir, "lib").apply { mkdirs() }
        File(nativeDir, NativeLibProotLocator.PROOT_LIBRARY).apply { writeText("x"); setExecutable(true) }
        val missing = FilesDirProotLocator(File(dir, "proot"))
        val chain = FallbackProotLocator(listOf(missing, NativeLibProotLocator(nativeDir)))
        assertEquals(NativeLibProotLocator.SOURCE, chain.locate()!!.source)
        executable("proot")
        assertEquals(FilesDirProotLocator.SOURCE, chain.locate()!!.source)
        assertTrue(chain.describe().contains("filesDir") && chain.describe().contains("nativeLibraryDir"))
    }
}
