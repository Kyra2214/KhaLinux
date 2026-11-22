package com.khalinux.runtime.install

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Verifica se um diretório é um RootFS Debian utilizável pelo PRoot.
 *
 * O Debian moderno usa "usrmerge": `/bin`, `/sbin` e `/lib` são links simbólicos para `/usr/...`.
 * Por isso a checagem segue links (`File.isFile`): se `/bin` virou um arquivo vazio (extração incorreta) ou
 * um link quebrado, `bin/bash` não resolve e o RootFS é considerado inválido, mesmo que `usr/bin/bash` exista.
 */
object RootfsValidator {
    /** Retorna a descrição do problema ou `null` se o RootFS estiver utilizável. */
    fun problem(rootfs: File): String? {
        if (!rootfs.isDirectory) return "diretório do RootFS ausente"
        val bin = File(rootfs, "bin")
        if (Files.exists(bin.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(bin.toPath()) && !bin.isDirectory
        ) {
            return "/bin é um arquivo em vez de diretório/link (extração de links simbólicos falhou)"
        }
        if (!File(rootfs, "bin/bash").isFile) {
            return if (File(rootfs, "usr/bin/bash").isFile) {
                "/bin/bash não resolve (link /bin → usr/bin ausente ou quebrado)"
            } else {
                "bash ausente"
            }
        }
        return null
    }

    fun isUsable(rootfs: File): Boolean = problem(rootfs) == null
}
