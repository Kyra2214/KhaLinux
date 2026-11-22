package com.khalinux.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidBindMountPolicyTest {
    @Test
    fun exposesOnlyAllowlistedDeviceNodes() {
        val devRoot = Files.createTempDirectory("khalinux-host-dev").toFile()
        try {
            listOf("null", "zero", "random", "urandom", "tty").forEach {
                File(devRoot, it).createNewFile()
            }
            File(devRoot, "pts").mkdirs()
            File(devRoot, "proc").mkdirs()
            File(devRoot, "sys").mkdirs()

            val mounts = AndroidBindMountPolicy.defaultMounts(devRoot)

            assertEquals(
                listOf("/dev/null", "/dev/zero", "/dev/random", "/dev/urandom", "/dev/tty", "/dev/pts"),
                mounts.map { it.guestPath }
            )
            assertFalse(mounts.any { it.guestPath == "/proc" || it.guestPath == "/sys" || it.guestPath == "/dev" })
        } finally {
            devRoot.deleteRecursively()
        }
    }

    @Test
    fun bindArgumentUsesOnlySupportedHostAndGuestPaths() {
        val host = Files.createTempFile("khalinux-device", ".node").toFile()
        try {
            val bind = BindMount(host, "/dev/null")
            bind.validate()
            assertEquals("${host.absolutePath}:/dev/null", bind.asArgument())
            assertFalse(bind.asArgument().endsWith(":ro"))
        } finally {
            host.delete()
        }
    }

    @Test
    fun rejectsGuestPathTraversal() {
        val host = Files.createTempFile("khalinux-device", ".node").toFile()
        try {
            val bind = BindMount(host, "/dev/../../data")
            assertThrows(IllegalArgumentException::class.java) { bind.validate() }
        } finally {
            host.delete()
        }
    }

    @Test
    fun preparerCreatesOnlyValidatedFileAndDirectoryTargets() {
        val rootfs = Files.createTempDirectory("khalinux-bind-rootfs").toFile()
        val host = Files.createTempDirectory("khalinux-bind-host").toFile()
        try {
            val hostNull = File(host, "null").apply { createNewFile() }
            val hostPts = File(host, "pts").apply { mkdirs() }
            val mounts = listOf(
                BindMount(hostNull, "/dev/null"),
                BindMount(hostPts, "/dev/pts")
            )

            ProotBindMountPreparer.prepare(rootfs, mounts)

            assertTrue(File(rootfs, "dev/null").isFile)
            assertTrue(File(rootfs, "dev/pts").isDirectory)
            assertFalse(File(rootfs, "proc").exists())
            assertFalse(File(rootfs, "sys").exists())
        } finally {
            rootfs.deleteRecursively()
            host.deleteRecursively()
        }
    }

    @Test
    fun preparerRejectsSymlinkedGuestParent() {
        val rootfs = Files.createTempDirectory("khalinux-bind-rootfs").toFile()
        val outside = Files.createTempDirectory("khalinux-bind-outside").toFile()
        val host = Files.createTempFile("khalinux-bind-host", ".node").toFile()
        try {
            Files.createSymbolicLink(File(rootfs, "dev").toPath(), outside.toPath())
            val bind = BindMount(host, "/dev/null")
            assertThrows(IllegalArgumentException::class.java) {
                ProotBindMountPreparer.prepare(rootfs, listOf(bind))
            }
            assertFalse(File(outside, "null").exists())
        } finally {
            rootfs.deleteRecursively()
            outside.deleteRecursively()
            host.delete()
        }
    }
}
