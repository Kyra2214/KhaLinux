package com.khalinux.runtime.operations

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Armazena evidências brutas e retorna referências relativas com SHA-256. */
class LabEvidenceStore(private val root: File) {
    @Synchronized
    fun save(executionId: String, stream: String, source: String, bytes: ByteArray): LabEvidenceReference? {
        require(SAFE_ID.matches(executionId))
        require(stream == "stdout" || stream == "stderr")
        if (bytes.isEmpty()) return null
        val directory = File(root, "evidence/$executionId").apply { mkdirs() }
        val target = File(directory, "$stream.bin")
        val temporary = File(directory, ".$stream.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes); output.flush(); output.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("Não foi possível persistir evidência $stream")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return LabEvidenceReference(
            id = java.util.UUID.randomUUID().toString(), executionId = executionId,
            kind = "process.$stream", source = source,
            relativePath = "evidence/$executionId/$stream.bin", sha256 = digest,
            sizeBytes = bytes.size.toLong(), createdAtEpochMillis = System.currentTimeMillis()
        )
    }

    fun open(reference: LabEvidenceReference): InputStream {
        val base = root.canonicalFile
        val file = File(base, reference.relativePath).canonicalFile
        require(file.path.startsWith(base.path + File.separator)) { "Referência de evidência fora do armazenamento" }
        require(file.isFile) { "Evidência não encontrada: " + reference.id }
        return FileInputStream(file)
    }

    fun read(reference: LabEvidenceReference): ByteArray {
        val bytes = open(reference).use { it.readBytes() }
        reference.sha256?.let { expected ->
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            require(actual.equals(expected, ignoreCase = true)) { "Hash da evidência não confere: " + reference.id }
        }
        return bytes
    }

    private companion object { val SAFE_ID = Regex("^[A-Za-z0-9-]{1,80}$") }
}
