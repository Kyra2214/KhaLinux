package com.khalinux.runtime.favorites

import java.io.File

enum class FavoriteKind { COMMAND, TOOL }

data class Favorite(val kind: FavoriteKind, val value: String)

/**
 * Favoritos do usuário (comandos e ferramentas) em arquivo TSV (`TIPO<TAB>valor`), gravado de forma atômica.
 * Valores com quebra de linha, tabulação ou outros caracteres de controle são recusados; não há segredos
 * automáticos: o usuário não deve favoritar comandos com senhas.
 */
class FavoritesStore(
    private val file: File,
    private val maxPerKind: Int = MAX_PER_KIND
) {
    private val lock = Any()

    init {
        require(maxPerKind > 0) { "maxPerKind deve ser maior que zero" }
    }

    fun list(kind: FavoriteKind? = null): List<Favorite> = synchronized(lock) {
        load().filter { kind == null || it.kind == kind }
    }

    fun contains(kind: FavoriteKind, value: String): Boolean = synchronized(lock) {
        val clean = normalize(value) ?: return false
        load().any { it.kind == kind && it.value == clean }
    }

    /** Adiciona ao topo; false se inválido, duplicado ou se o limite do tipo foi atingido. */
    fun add(kind: FavoriteKind, value: String): Boolean = synchronized(lock) {
        val clean = normalize(value) ?: return false
        val current = load()
        if (current.any { it.kind == kind && it.value == clean }) return false
        if (current.count { it.kind == kind } >= maxPerKind) return false
        save(listOf(Favorite(kind, clean)) + current)
        true
    }

    fun remove(kind: FavoriteKind, value: String): Boolean = synchronized(lock) {
        val clean = normalize(value) ?: return false
        val current = load()
        val next = current.filterNot { it.kind == kind && it.value == clean }
        if (next.size == current.size) return false
        save(next)
        true
    }

    /** Alterna o favorito; devolve o novo estado (true = favorito). */
    fun toggle(kind: FavoriteKind, value: String): Boolean = synchronized(lock) {
        if (contains(kind, value)) {
            remove(kind, value)
            false
        } else {
            add(kind, value)
        }
    }

    private fun load(): List<Favorite> {
        if (!file.isFile) return emptyList()
        return try {
            file.readLines(Charsets.UTF_8).mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val kind = FavoriteKind.values().firstOrNull { it.name == line.substring(0, tab) }
                    ?: return@mapNotNull null
                val value = normalize(line.substring(tab + 1)) ?: return@mapNotNull null
                Favorite(kind, value)
            }.distinct()
        } catch (_: java.io.IOException) {
            emptyList()
        }
    }

    private fun save(items: List<Favorite>) {
        file.absoluteFile.parentFile?.mkdirs()
        val temp = File(file.absolutePath + ".tmp")
        temp.writeText(items.joinToString("") { "${it.kind.name}\t${it.value}\n" }, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "não foi possível gravar os favoritos" }
        }
    }

    companion object {
        const val MAX_PER_KIND = 50
        const val MAX_VALUE_LENGTH = 500

        fun normalize(raw: String): String? {
            val clean = raw.trim()
            if (clean.isEmpty() || clean.length > MAX_VALUE_LENGTH) return null
            if (clean.any { it.isISOControl() }) return null
            return clean
        }
    }
}
