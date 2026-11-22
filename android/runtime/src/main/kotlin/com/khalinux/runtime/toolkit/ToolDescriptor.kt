package com.khalinux.runtime.toolkit

import com.khalinux.runtime.packages.PackageCatalog
import com.khalinux.runtime.packages.PackageManifestException

/** Grupos do Cyber Toolkit. */
enum class ToolGroup(val id: String) {
    RECON("reconhecimento"),
    NETWORK("rede"),
    WEB("web"),
    FORENSICS("forense"),
    AUDIT("auditoria");

    companion object {
        fun fromId(id: String): ToolGroup =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Grupo de ferramenta inválido: '$id'")
    }
}

/**
 * Descritor de uma ferramenta do toolkit: liga um pacote do catálogo aos comandos que ele fornece,
 * ao comando de verificação (smoke check), às limitações sem root e à política de uso autorizado.
 * Nenhuma compatibilidade é presumida: `verified` só vira verdadeiro com evidência registrada.
 */
data class ToolDescriptor(
    val id: String,
    val packageName: String,
    val title: String,
    val group: ToolGroup,
    val summary: String,
    val commands: List<String>,
    /** Comando executado no RootFS para verificar a presença/versão; deve começar por um dos [commands]. */
    val checkCommand: String,
    /** Exige confirmação de escopo/autorização antes do uso (ferramentas que interagem com terceiros). */
    val requiresAuthorization: Boolean,
    val rootLimitations: List<String>,
    /** Caminho relativo da documentação da ferramenta, em `docs/toolkit/`. */
    val doc: String,
    /** Evidência de verificação em ARM64 sem root (nulo = não verificado). */
    val verificationEvidence: String? = null
) {
    init {
        if (!ID.matches(id)) throw PackageManifestException("Id de ferramenta inválido: '$id'")
        if (title.isBlank() || title.length > 80) throw PackageManifestException("Título inválido em $id")
        if (summary.isBlank() || summary.length > 300) throw PackageManifestException("Resumo inválido em $id")
        if (commands.isEmpty() || commands.any { !COMMAND.matches(it) } || commands.toSet().size != commands.size) {
            throw PackageManifestException("Comandos inválidos em $id")
        }
        val first = checkCommand.trim().split(' ').firstOrNull().orEmpty()
        if (first !in commands || checkCommand.any { it in FORBIDDEN_IN_CHECK } || checkCommand.length > 120) {
            throw PackageManifestException("checkCommand inválido em $id")
        }
        if (!DOC.matches(doc)) throw PackageManifestException("Documentação inválida em $id: '$doc'")
        if (rootLimitations.any { it.isBlank() }) throw PackageManifestException("Limitação vazia em $id")
        if (verificationEvidence != null && verificationEvidence.isBlank()) throw PackageManifestException("Evidência vazia em $id")
    }

    val verified: Boolean get() = verificationEvidence != null

    companion object {
        internal val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
        internal val COMMAND = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,39}")
        internal val DOC = Regex("docs/toolkit/[a-z0-9][a-z0-9-]*\\.md")
        private val FORBIDDEN_IN_CHECK = setOf(';', '&', '|', '<', '>', '$', '`', '\n', '\r', '\\', '"', '\'')
    }
}

/** Conjunto validado de ferramentas, sempre consistente com um [PackageCatalog]. */
class ToolkitRegistry(val tools: List<ToolDescriptor>, catalog: PackageCatalog) {
    init {
        if (tools.map { it.id }.toSet().size != tools.size) throw PackageManifestException("Ids de ferramenta duplicados")
        val commands = tools.flatMap { it.commands }
        if (commands.toSet().size != commands.size) throw PackageManifestException("Comando fornecido por mais de uma ferramenta")
        for (tool in tools) {
            if (catalog.find(tool.packageName) == null) {
                throw PackageManifestException("Ferramenta '${tool.id}' referencia pacote ausente do catálogo: ${tool.packageName}")
            }
        }
    }

    fun find(id: String): ToolDescriptor? = tools.firstOrNull { it.id == id.lowercase() }
    fun byGroup(group: ToolGroup): List<ToolDescriptor> = tools.filter { it.group == group }.sortedBy { it.id }
    fun forPackage(packageName: String): List<ToolDescriptor> = tools.filter { it.packageName == packageName }
    fun groups(): List<Pair<ToolGroup, Int>> {
        val counts = tools.groupingBy { it.group }.eachCount()
        return ToolGroup.values().mapNotNull { g -> counts[g]?.let { g to it } }
    }
}

/** Converte a estrutura decodificada de `tools.json` em descritores (campos desconhecidos são rejeitados). */
object ToolkitRegistryParser {
    private val ROOT_KEYS = setOf("schema", "tools")
    private val TOOL_KEYS = setOf(
        "id", "package", "title", "group", "summary", "commands", "check_command",
        "requires_authorization", "root_limitations", "doc", "verification_evidence"
    )

    fun parse(root: Map<String, Any?>, catalog: PackageCatalog): ToolkitRegistry {
        val unknown = root.keys - ROOT_KEYS
        if (unknown.isNotEmpty()) throw PackageManifestException("Campos desconhecidos no toolkit: ${unknown.sorted()}")
        if ((root["schema"] as? Number)?.toInt() != 1) throw PackageManifestException("Schema do toolkit não suportado")
        val list = root["tools"] as? List<*> ?: throw PackageManifestException("Lista 'tools' ausente")
        val tools = list.map { entry ->
            @Suppress("UNCHECKED_CAST")
            val map = entry as? Map<String, Any?> ?: throw PackageManifestException("Entrada de ferramenta malformada")
            val extra = map.keys - TOOL_KEYS
            if (extra.isNotEmpty()) throw PackageManifestException("Campos desconhecidos em ferramenta: ${extra.sorted()}")
            ToolDescriptor(
                id = str(map, "id"),
                packageName = str(map, "package"),
                title = str(map, "title"),
                group = ToolGroup.fromId(str(map, "group")),
                summary = str(map, "summary"),
                commands = strings(map, "commands"),
                checkCommand = str(map, "check_command"),
                requiresAuthorization = map["requires_authorization"] as? Boolean
                    ?: throw PackageManifestException("requires_authorization ausente/ inválido"),
                rootLimitations = strings(map, "root_limitations"),
                doc = str(map, "doc"),
                verificationEvidence = (map["verification_evidence"] as? String)
            )
        }
        return ToolkitRegistry(tools, catalog)
    }

    private fun str(map: Map<String, Any?>, key: String): String =
        map[key] as? String ?: throw PackageManifestException("Campo obrigatório ausente ou inválido: '$key'")

    private fun strings(map: Map<String, Any?>, key: String): List<String> {
        val value = map[key] ?: return emptyList()
        val list = value as? List<*> ?: throw PackageManifestException("Campo '$key' deve ser lista")
        return list.map { it as? String ?: throw PackageManifestException("Itens de '$key' devem ser texto") }
    }
}
