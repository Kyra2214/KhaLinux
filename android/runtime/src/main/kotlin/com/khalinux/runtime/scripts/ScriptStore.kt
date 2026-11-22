package com.khalinux.runtime.scripts

import java.io.File

/**
 * Biblioteca de scripts do usuário: arquivos `.sh` num diretório do host que, no RootFS, aparece como
 * [GUEST_DIRECTORY] (dentro do home persistente). Nomes validados impedem path traversal e injeção no comando.
 */
class ScriptStore(private val directory: File) {
    private val lock = Any()

    fun list(): List<String> = synchronized(lock) {
        directory.listFiles { file -> file.isFile && isValidName(file.name) }
            ?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()
    }

    fun read(name: String): String? = synchronized(lock) {
        val file = fileFor(name) ?: return null
        if (!file.isFile) return null
        try { file.readText(Charsets.UTF_8) } catch (_: java.io.IOException) { null }
    }

    /** Salva o script (cria ou substitui) e devolve o nome final; nulo se nome/conteúdo inválidos. */
    fun save(name: String, content: String): String? = synchronized(lock) {
        val finalName = normalizeName(name) ?: return null
        if (content.length > MAX_CONTENT_LENGTH || content.contains('\u0000')) return null
        val file = fileFor(finalName) ?: return null
        directory.mkdirs()
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val temp = File(directory, ".$finalName.tmp")
        temp.writeText(if (normalized.endsWith("\n") || normalized.isEmpty()) normalized else normalized + "\n", Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "não foi possível gravar o script" }
        }
        finalName
    }

    fun delete(name: String): Boolean = synchronized(lock) {
        val file = fileFor(name) ?: return false
        file.isFile && file.delete()
    }

    /** Comando Bash (executado dentro do RootFS) que roda o script; nulo se o nome é inválido. */
    fun command(name: String): String? {
        val valid = normalizeName(name) ?: return null
        return "bash $GUEST_DIRECTORY/$valid"
    }

    private fun fileFor(name: String): File? {
        val valid = normalizeName(name) ?: return null
        return File(directory, valid)
    }

    companion object {
        const val GUEST_DIRECTORY = "/home/khalinux/scripts"
        const val MAX_CONTENT_LENGTH = 65_536
        private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

        fun isValidName(name: String): Boolean = NAME.matches(name) && !name.contains("..")

        /** Acrescenta `.sh` se o nome não tem extensão; recusa nomes inválidos. */
        fun normalizeName(raw: String): String? {
            val trimmed = raw.trim()
            val withExtension = if (trimmed.contains('.')) trimmed else "$trimmed.sh"
            return withExtension.takeIf { isValidName(it) }
        }
    }
}
