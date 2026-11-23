package com.khalinux.runtime.operations

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

/** Registro persistente de ativos observados pelo laboratório. */
class LabAssetStore(private val directory: File) {
    @Synchronized
    fun upsert(asset: LabAsset): LabAsset {
        val previous = get(asset.id)
        val saved = asset.copy(
            firstSeenAtEpochMillis = previous?.firstSeenAtEpochMillis ?: asset.firstSeenAtEpochMillis,
            lastSeenAtEpochMillis = maxOf(asset.lastSeenAtEpochMillis, previous?.lastSeenAtEpochMillis ?: 0L)
        )
        val properties = Properties().apply {
            setProperty("id", saved.id)
            setProperty("kind", saved.kind.name)
            setProperty("displayName", saved.displayName)
            saved.primaryAddress?.let { setProperty("primaryAddress", it) }
            setProperty("attributes", packMap(saved.attributes))
            setProperty("capabilities", pack(saved.capabilities))
            setProperty("source", saved.source)
            setProperty("firstSeen", saved.firstSeenAtEpochMillis.toString())
            setProperty("lastSeen", saved.lastSeenAtEpochMillis.toString())
        }
        write(saved.id, properties)
        return saved
    }

    @Synchronized
    fun get(id: String): LabAsset? {
        if (!SAFE_ID.matches(id)) return null
        val file = File(directory, "$id.properties")
        if (!file.isFile) return null
        val properties = runCatching {
            val value = Properties()
            FileInputStream(file).use { value.load(it) }
            value
        }.getOrNull() ?: return null
        return runCatching {
            LabAsset(
                id = properties.getProperty("id"),
                kind = LabTargetKind.valueOf(properties.getProperty("kind")),
                displayName = properties.getProperty("displayName"),
                primaryAddress = properties.getProperty("primaryAddress"),
                attributes = unpackMap(properties.getProperty("attributes", "")),
                capabilities = unpack(properties.getProperty("capabilities", "")).toSet(),
                source = properties.getProperty("source"),
                firstSeenAtEpochMillis = properties.getProperty("firstSeen").toLong(),
                lastSeenAtEpochMillis = properties.getProperty("lastSeen").toLong()
            )
        }.getOrNull()
    }

    @Synchronized
    fun all(): List<LabAsset> =
        directory.listFiles()?.filter { it.isFile && it.extension == "properties" }
            .orEmpty().mapNotNull { get(it.nameWithoutExtension) }
            .sortedBy { it.displayName.lowercase() }

    @Synchronized
    fun remove(id: String): Boolean {
        if (!SAFE_ID.matches(id)) return false
        val file = File(directory, "$id.properties")
        return !file.exists() || file.delete()
    }

    private fun write(id: String, properties: Properties) {
        require(SAFE_ID.matches(id)) { "Id de ativo inválido" }
        directory.mkdirs()
        val target = File(directory, "$id.properties")
        val temporary = File(directory, ".$id.tmp")
        FileOutputStream(temporary).use { output -> properties.store(output, null); output.fd.sync() }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { if (temporary.exists()) temporary.delete() }
    }

    private fun pack(values: Collection<String>): String = values.joinToString(".") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
    }
    private fun unpack(value: String): List<String> = value.split('.').filter { it.isNotEmpty() }.map {
        String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
    }
    private fun packMap(values: Map<String, String>): String =
        pack(values.toSortedMap().flatMap { listOf(it.key, it.value) })
    private fun unpackMap(value: String): Map<String, String> {
        val entries = unpack(value)
        require(entries.size % 2 == 0) { "Mapa de atributos de ativo corrompido" }
        return entries.chunked(2).associate { it[0] to it[1] }
    }

    private companion object { val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,80}$") }
}
