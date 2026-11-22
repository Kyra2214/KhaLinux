package com.khalinux.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Cria somente os pontos de montagem allowlisted dentro do RootFS privado. */
object ProotBindMountPreparer {
    fun prepare(rootfsDirectory: File, bindMounts: List<BindMount>) {
        require(rootfsDirectory.isDirectory && !Files.isSymbolicLink(rootfsDirectory.toPath())) {
            "RootFS inválido para preparar bind mounts"
        }
        val root = rootfsDirectory.canonicalFile.toPath().normalize()

        bindMounts.forEach { mount ->
            mount.validate()
            val relative = mount.guestPath.removePrefix("/")
            val target = root.resolve(relative).normalize()
            require(target != root && target.startsWith(root)) {
                "Destino de bind escapa do RootFS: ${mount.guestPath}"
            }

            val segments = relative.split('/')
            var current = root
            segments.forEachIndexed { index, segment ->
                current = current.resolve(segment)
                require(!Files.isSymbolicLink(current)) {
                    "Destino de bind atravessa link simbólico: ${mount.guestPath}"
                }
                val isFinal = index == segments.lastIndex
                if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    if (!isFinal || mount.hostPath.isDirectory) {
                        Files.createDirectory(current)
                    } else {
                        Files.createFile(current)
                    }
                }
                if (!isFinal || mount.hostPath.isDirectory) {
                    require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                        "Destino de bind deveria ser diretório: ${mount.guestPath}"
                    }
                } else {
                    require(
                        Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) ||
                            Files.isOther(current, LinkOption.NOFOLLOW_LINKS)
                    ) { "Destino de bind deveria ser arquivo ou dispositivo: ${mount.guestPath}" }
                }
            }
        }
    }
}
