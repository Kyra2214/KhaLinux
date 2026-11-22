package com.khalinux.runtime.labs

import com.khalinux.runtime.packages.PackageManifestException
import com.khalinux.runtime.toolkit.ToolkitRegistry

/** Conjunto validado de laboratórios, consistente com o Cyber Toolkit e sem ciclos de pré-requisitos. */
class LabCatalog(val catalogVersion: String, val labs: List<LabDescriptor>, toolkit: ToolkitRegistry? = null) {
    init {
        if (catalogVersion.isBlank()) throw PackageManifestException("labs: catalog_version ausente")
        if (labs.map { it.id }.toSet().size != labs.size) throw PackageManifestException("Ids de laboratório duplicados")
        val ids = labs.map { it.id }.toSet()
        for (lab in labs) {
            lab.prerequisites.firstOrNull { it !in ids }?.let {
                throw PackageManifestException("Laboratório '${lab.id}' tem pré-requisito inexistente: $it")
            }
            if (toolkit != null) lab.requiredTools.firstOrNull { toolkit.find(it) == null }?.let {
                throw PackageManifestException("Laboratório '${lab.id}' exige ferramenta ausente do toolkit: $it")
            }
        }
        detectCycles()
    }

    fun find(id: String): LabDescriptor? = labs.firstOrNull { it.id == id.lowercase() }
    fun byTrack(track: LabTrack): List<LabDescriptor> =
        labs.filter { it.track == track }.sortedWith(compareBy({ it.difficulty.ordinal }, { it.id }))

    fun tracks(): List<Pair<LabTrack, Int>> {
        val counts = labs.groupingBy { it.track }.eachCount()
        return LabTrack.values().mapNotNull { t -> counts[t]?.let { t to it } }
    }

    /** Laboratórios cujos pré-requisitos estão todos em [completed] e que ainda não foram concluídos. */
    fun available(completed: Set<String>): List<LabDescriptor> =
        labs.filter { it.id !in completed && completed.containsAll(it.prerequisites) }
            .sortedWith(compareBy({ it.track.ordinal }, { it.difficulty.ordinal }, { it.id }))

    private fun detectCycles() {
        val state = HashMap<String, Int>() // 1 = visitando, 2 = concluído
        val byId = labs.associateBy { it.id }
        fun visit(id: String) {
            when (state[id]) {
                2 -> return
                1 -> throw PackageManifestException("Ciclo de pré-requisitos envolvendo '$id'")
            }
            state[id] = 1
            byId.getValue(id).prerequisites.forEach(::visit)
            state[id] = 2
        }
        labs.forEach { visit(it.id) }
    }
}

/** Converte a estrutura decodificada de `labs.json` (campos desconhecidos são rejeitados). */
object LabCatalogParser {
    private val ROOT = setOf("schema", "catalog_version", "labs")
    private val LAB = setOf("id", "track", "title", "summary", "difficulty", "required_tools", "prerequisites", "services", "exercises", "doc")
    private val EXERCISE = setOf("id", "title", "instructions", "difficulty", "answer_sha256", "ignore_case", "hints")
    private val SERVICE = setOf("name", "command", "bind", "port")
    private val HINT = setOf("text", "cost")

    fun parse(root: Map<String, Any?>, toolkit: ToolkitRegistry? = null): LabCatalog {
        val unknown = root.keys - ROOT
        if (unknown.isNotEmpty()) throw PackageManifestException("Campos desconhecidos em labs: ${unknown.sorted()}")
        if ((root["schema"] as? Number)?.toInt() != 1) throw PackageManifestException("Schema de labs não suportado")
        val version = root["catalog_version"] as? String ?: throw PackageManifestException("catalog_version ausente em labs")
        val labs = objects(root, "labs").map(::lab)
        return LabCatalog(version, labs, toolkit)
    }

    private fun lab(m: Map<String, Any?>): LabDescriptor {
        checkKeys(m, LAB, "laboratório")
        return LabDescriptor(
            id = str(m, "id"),
            track = LabTrack.fromId(str(m, "track")),
            title = str(m, "title"),
            summary = str(m, "summary"),
            difficulty = LabDifficulty.fromId(str(m, "difficulty")),
            requiredTools = strings(m, "required_tools"),
            prerequisites = strings(m, "prerequisites"),
            services = objects(m, "services").map(::service),
            exercises = objects(m, "exercises").map(::exercise),
            doc = str(m, "doc")
        )
    }

    private fun exercise(m: Map<String, Any?>): LabExercise {
        checkKeys(m, EXERCISE, "exercício")
        return LabExercise(
            id = str(m, "id"),
            title = str(m, "title"),
            instructions = str(m, "instructions"),
            difficulty = LabDifficulty.fromId(str(m, "difficulty")),
            answerSha256 = str(m, "answer_sha256"),
            ignoreCase = m["ignore_case"] as? Boolean ?: false,
            hints = objects(m, "hints").map { h ->
                checkKeys(h, HINT, "dica")
                LabHint(str(h, "text"), (h["cost"] as? Number)?.toInt() ?: throw PackageManifestException("Custo de dica ausente"))
            }
        )
    }

    private fun service(m: Map<String, Any?>): LabService {
        checkKeys(m, SERVICE, "serviço")
        return LabService(
            name = str(m, "name"),
            command = str(m, "command"),
            bindAddress = str(m, "bind"),
            port = (m["port"] as? Number)?.toInt() ?: throw PackageManifestException("Porta ausente no serviço")
        )
    }

    private fun checkKeys(m: Map<String, Any?>, allowed: Set<String>, what: String) {
        val extra = m.keys - allowed
        if (extra.isNotEmpty()) throw PackageManifestException("Campos desconhecidos em $what: ${extra.sorted()}")
    }

    private fun str(m: Map<String, Any?>, key: String): String =
        m[key] as? String ?: throw PackageManifestException("Campo obrigatório ausente ou inválido: '$key'")

    private fun strings(m: Map<String, Any?>, key: String): List<String> {
        val value = m[key] ?: return emptyList()
        val list = value as? List<*> ?: throw PackageManifestException("Campo '$key' deve ser lista")
        return list.map { it as? String ?: throw PackageManifestException("Itens de '$key' devem ser texto") }
    }

    private fun objects(m: Map<String, Any?>, key: String): List<Map<String, Any?>> {
        val value = m[key] ?: return emptyList()
        val list = value as? List<*> ?: throw PackageManifestException("Campo '$key' deve ser lista")
        return list.map {
            @Suppress("UNCHECKED_CAST")
            it as? Map<String, Any?> ?: throw PackageManifestException("Entrada malformada em '$key'")
        }
    }
}
