package com.khalinux.runtime.startup

import java.io.File
import java.io.IOException

/**
 * Marca de sincronização de assets copiados para o disco (8.3): evita recopiar a cada inicialização o que só muda
 * quando o app é atualizado. O valor da marca vem de quem chama (ex.: `lastUpdateTime` do pacote).
 *
 * Seguro por construção: se a marca faltar, estiver ilegível ou diferente, a cópia é refeita; a marca só é gravada
 * DEPOIS de a cópia terminar, então uma cópia interrompida nunca é dada como válida.
 */
class AssetSyncStamp(private val file: File) {
    fun isCurrent(stamp: String): Boolean = try {
        file.isFile && file.readText(Charsets.UTF_8).trim() == stamp
    } catch (_: IOException) {
        false
    }

    fun mark(stamp: String) {
        try {
            file.absoluteFile.parentFile?.mkdirs()
            val temp = File(file.absolutePath + ".tmp")
            temp.writeText(stamp, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: IOException) {
            // Sem marca, a próxima inicialização apenas copia de novo.
        }
    }

    /** Executa [copy] só se a marca estiver desatualizada; marca somente se [copy] terminar sem exceção. Devolve true se copiou. */
    inline fun syncIfStale(stamp: String, copy: () -> Unit): Boolean {
        if (isCurrent(stamp)) return false
        copy()
        mark(stamp)
        return true
    }
}
