package com.khalinux.runtime.install

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/** Ações executadas pela recuperação (texto para registro/diagnóstico). */
data class RecoveryResult(val actions: List<String>) {
    val recovered: Boolean get() = actions.isNotEmpty()
}

/**
 * Recuperação após instalação interrompida (8.5). Os instaladores trocam o destino assim: destino → `.previous`,
 * `.staging` → destino. Se o processo morre entre os dois passos, o destino some e a única versão utilizável fica em
 * `.previous`; sem esta recuperação o app reinstalaria tudo do zero (e o instalador apagaria a cópia anterior).
 *
 * Regras: nada é feito durante uma instalação em andamento; só restaura quando o destino NÃO existe e a cópia
 * anterior é válida; nunca sobrescreve um destino existente; downloads parciais ficam intactos para retomada.
 */
object InstallRecovery {
    fun recover(runtimeDirectory: File, store: InstallStateStore? = null, installRunning: Boolean = false): RecoveryResult {
        if (installRunning) return RecoveryResult(emptyList())
        val actions = mutableListOf<String>()

        val rootfs = File(runtimeDirectory, "rootfs-arm64")
        val rootfsPrevious = File(runtimeDirectory, ".rootfs-arm64.previous")
        if (!exists(rootfs) && validRootfs(rootfsPrevious) && move(rootfsPrevious, rootfs)) {
            actions += "RootFS restaurado da cópia anterior"
        }

        val proot = File(runtimeDirectory, "proot")
        val prootPrevious = File(runtimeDirectory, ".proot.previous")
        if (!exists(proot) && validProotFile(prootPrevious) && move(prootPrevious, proot)) {
            actions += "PRoot restaurado da cópia anterior"
        }

        // O estado persistido só termina em READY ou FAILED; qualquer outro valor significa que o processo morreu no meio.
        val stored = store?.read()
        if (stored != null && stored.phase != InstallPhase.READY && stored.phase != InstallPhase.FAILED) {
            store.write(InstallPhase.FAILED, "Instalação interrompida (${stored.phase.label}); será retomada")
            actions += "Instalação interrompida registrada (${stored.phase.name})"
        }
        return RecoveryResult(actions)
    }

    internal fun validRootfs(dir: File): Boolean =
        Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            RootfsValidator.isUsable(dir)

    private fun validProotFile(file: File): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() >= MIN_PROOT_BYTES

    private fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun move(from: File, to: File): Boolean = try {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
        true
    } catch (_: IOException) {
        false
    }

    private const val MIN_PROOT_BYTES = 64L
}
