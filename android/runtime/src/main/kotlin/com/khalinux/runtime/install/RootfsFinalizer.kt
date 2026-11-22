package com.khalinux.runtime.install

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Ajustes pós-extração do RootFS necessários para uso em Android.
 * O resolv.conf do tarball pode apontar para um resolvedor local do host de build
 * (inalcançável no Android), então é sempre reescrito.
 */
object RootfsFinalizer {
    val DEFAULT_NAMESERVERS: List<String> = listOf("1.1.1.1", "8.8.8.8")

    fun apply(rootfs: File, nameservers: List<String> = DEFAULT_NAMESERVERS) {
        require(rootfs.isDirectory) { "RootFS ausente: ${rootfs.path}" }
        writeText(rootfs, "etc/resolv.conf", nameservers.joinToString("\n", postfix = "\n") { "nameserver $it" }, replace = true)
        writeText(rootfs, "etc/hosts", "127.0.0.1 localhost\n::1 localhost\n", replace = false)
        ensureDirectory(rootfs, "home/khalinux")
        ensureDirectory(rootfs, "tmp")
    }

    private fun writeText(rootfs: File, relative: String, content: String, replace: Boolean) {
        val target = File(rootfs, relative)
        val parent = target.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val path = target.toPath()
        if (Files.isSymbolicLink(path)) Files.delete(path)
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !replace) return
        target.writeText(content, Charsets.UTF_8)
    }

    private fun ensureDirectory(rootfs: File, relative: String) {
        val target = File(rootfs, relative)
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) target.mkdirs()
    }
}
