package com.khalinux.runtime.install

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class ProotInstallerTest {
    private val installer = ProotInstaller()

    @Test
    fun acceptsAndroidArm64PieWithBionicLinker() {
        val file = Files.createTempFile("khalinux-proot-elf", ".bin").toFile()
        try {
            file.writeBytes(androidElf())
            installer.validateAndroidArm64Elf(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsWrongElfMachine() {
        val file = Files.createTempFile("khalinux-proot-elf", ".bin").toFile()
        try {
            file.writeBytes(androidElf(machine = 62))
            assertThrows(IllegalArgumentException::class.java) {
                installer.validateAndroidArm64Elf(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsGlibcInterpreter() {
        val file = Files.createTempFile("khalinux-proot-elf", ".bin").toFile()
        try {
            file.writeBytes(androidElf(interpreter = "/lib/ld-linux-aarch64.so.1"))
            assertThrows(IllegalArgumentException::class.java) {
                installer.validateAndroidArm64Elf(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun extractsOnlyTheExpectedProotExecutable() {
        val directory = Files.createTempDirectory("khalinux-proot-tar").toFile()
        try {
            val archive = File(directory, "proot.tar.gz")
            val output = File(directory, "proot")
            val elf = androidElf()
            createArchive(archive, listOf("proot" to elf))

            assertEquals(elf.size.toLong(), installer.extractBinarySafely(archive, output))
            assertTrue(output.isFile)
            assertTrue(output.readBytes().contentEquals(elf))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathTraversalEntry() {
        val directory = Files.createTempDirectory("khalinux-proot-tar").toFile()
        try {
            val archive = File(directory, "proot.tar.gz")
            val output = File(directory, "proot")
            createArchive(archive, listOf("../escape" to androidElf()))

            assertThrows(IllegalArgumentException::class.java) {
                installer.extractBinarySafely(archive, output)
            }
            assertFalse(output.exists())
            assertFalse(File(directory.parentFile, "escape").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsAdditionalTarEntries() {
        val directory = Files.createTempDirectory("khalinux-proot-tar").toFile()
        try {
            val archive = File(directory, "proot.tar.gz")
            val output = File(directory, "proot")
            createArchive(archive, listOf("proot" to androidElf(), "extra" to byteArrayOf(1, 2, 3)))

            assertThrows(IllegalArgumentException::class.java) {
                installer.extractBinarySafely(archive, output)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun androidElf(
        machine: Int = 183,
        interpreter: String = "/system/bin/linker64"
    ): ByteArray {
        val bytes = ByteArray(256)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2 // ELFCLASS64
        bytes[5] = 1 // ELFDATA2LSB
        bytes[6] = 1 // EV_CURRENT

        val elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        elf.putShort(16, 3) // ET_DYN / PIE
        elf.putShort(18, machine.toShort())
        elf.putLong(32, 64) // e_phoff
        elf.putShort(54, 56) // e_phentsize
        elf.putShort(56, 1) // e_phnum

        elf.putInt(64, 3) // PT_INTERP
        elf.putLong(72, 128) // p_offset
        val interpreterBytes = interpreter.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        elf.putLong(96, interpreterBytes.size.toLong()) // p_filesz
        interpreterBytes.copyInto(bytes, 128)
        return bytes
    }

    private fun createArchive(file: File, entries: List<Pair<String, ByteArray>>) {
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(file))).use { tar ->
            entries.forEach { (name, bytes) ->
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                entry.mode = 0b111101101
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }
}
