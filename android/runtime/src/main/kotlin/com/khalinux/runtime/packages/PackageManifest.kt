package com.khalinux.runtime.packages

import java.net.URI
import java.util.Locale

/** Arquitetura-alvo declarada por um pacote. Nenhuma compatibilidade é presumida. */
enum class PackageArchitecture(val id: String) {
    ARM64("arm64"),
    ALL("all");

    companion object {
        fun fromId(id: String): PackageArchitecture =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Arquitetura não suportada: '$id'")
    }
}

/** Origem permitida de um pacote. */
enum class PackageSourceType(val id: String) {
    DEBIAN_APT("debian-apt"),
    GITHUB_RELEASE("github-release"),
    KHALINUX_REPOSITORY("khalinux-repository");

    companion object {
        fun fromId(id: String): PackageSourceType =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Origem não suportada: '$id'")
    }
}

/** Categoria do catálogo (usada pela pesquisa/filtros do submódulo 4.2). */
enum class PackageCategory(val id: String) {
    RECONNAISSANCE("reconhecimento"),
    NETWORK("rede"),
    WEB("web"),
    FORENSICS("forense"),
    AUDIT("auditoria"),
    SYSTEM("sistema"),
    DEVELOPMENT("desenvolvimento"),
    OTHER("outros");

    companion object {
        fun fromId(id: String): PackageCategory =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Categoria não suportada: '$id'")
    }
}

/** Estado de validação de compatibilidade do pacote neste projeto. */
enum class PackageValidation(val id: String) {
    /** Executado e verificado em ARM64 sem root (evidência referenciada no manifesto). */
    VERIFIED("verified"),
    /** Declarado pelo upstream, mas ainda não verificado pelo KhaLinux. */
    UNVERIFIED("unverified"),
    /** Sabidamente incompatível ou degradado no ambiente sem root. */
    KNOWN_LIMITED("known-limited");

    companion object {
        fun fromId(id: String): PackageValidation =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Estado de validação inválido: '$id'")
    }
}

class PackageManifestException(message: String) : IllegalArgumentException(message)

/** Origem do artefato: sempre HTTPS (ou nome de pacote APT resolvido pelo repositório fixado). */
data class PackageSource(
    val type: PackageSourceType,
    /** URL HTTPS do artefato (github-release / khalinux-repository) ou nulo para debian-apt. */
    val url: String?,
    /** Nome do pacote no repositório APT (apenas debian-apt). */
    val aptName: String?,
    /** Suite APT adicional exigida por este pacote, ex.: bookworm-backports. */
    val aptSuite: String? = null,
    /** Snapshot HTTPS fixado da suite adicional. */
    val aptUri: String? = null
) {
    init {
        when (type) {
            PackageSourceType.DEBIAN_APT -> {
                if (aptName.isNullOrBlank() || !APT_NAME.matches(aptName)) {
                    throw PackageManifestException("Nome APT inválido: '$aptName'")
                }
                if (url != null) throw PackageManifestException("debian-apt não aceita URL direta")
                if ((aptSuite == null) != (aptUri == null)) {
                    throw PackageManifestException("aptSuite e aptUri devem ser informados juntos")
                }
                if (aptSuite != null && !APT_SUITE.matches(aptSuite)) {
                    throw PackageManifestException("Suite APT inválida: '$aptSuite'")
                }
                if (aptUri != null) {
                    val uri = runCatching { URI(aptUri) }.getOrNull()
                        ?: throw PackageManifestException("URI APT inválida: '$aptUri'")
                    if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
                        throw PackageManifestException("Suite APT adicional exige URI HTTPS sem credenciais")
                    }
                }
            }
            PackageSourceType.GITHUB_RELEASE, PackageSourceType.KHALINUX_REPOSITORY -> {
                if (url.isNullOrBlank()) throw PackageManifestException("URL ausente para origem ${type.id}")
                val uri = runCatching { URI(url) }.getOrNull()
                    ?: throw PackageManifestException("URL inválida: '$url'")
                if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrBlank()) {
                    throw PackageManifestException("A origem exige HTTPS: '$url'")
                }
                if (uri.userInfo != null) throw PackageManifestException("URL não pode conter credenciais")
                if (aptName != null) throw PackageManifestException("${type.id} não aceita aptName")
                if (aptSuite != null || aptUri != null) throw PackageManifestException("${type.id} não aceita suite APT")
            }
        }
    }

    private companion object {
        val APT_NAME = Regex("[a-z0-9][a-z0-9+.-]+")
        val APT_SUITE = Regex("[a-z0-9][a-z0-9-]{0,31}")
    }
}

/** Dados de verificação de integridade. SHA-256 é obrigatório para artefatos baixados diretamente. */
data class PackageVerification(
    val sha256: String?,
    /** Evidência de validação (ex.: caminho de doc/relatório em `docs/` ou URL de execução do CI). */
    val evidence: String?
) {
    init {
        if (sha256 != null && !SHA256.matches(sha256)) {
            throw PackageManifestException("SHA-256 inválido")
        }
    }

    private companion object {
        val SHA256 = Regex("[a-fA-F0-9]{64}")
    }
}

/** Dependência de outro pacote do catálogo (resolução no submódulo 4.3). */
data class PackageDependency(val name: String, val minVersion: String?) {
    init {
        if (!PackageManifest.NAME.matches(name)) throw PackageManifestException("Dependência inválida: '$name'")
    }
}

/**
 * Manifesto de um pacote do catálogo KhaLinux.
 *
 * Invariantes validadas na construção:
 * - nome, versão e arquitetura declarados;
 * - origem HTTPS (ou APT do repositório fixado);
 * - artefato direto exige SHA-256 e tamanho > 0;
 * - estado `verified` exige evidência; limitações são obrigatórias para `known-limited`;
 * - nenhum pacote pode depender de si mesmo.
 */
data class PackageManifest(
    val schema: Int,
    val name: String,
    val version: String,
    val title: String,
    val description: String,
    val category: PackageCategory,
    val architecture: PackageArchitecture,
    val source: PackageSource,
    val downloadBytes: Long?,
    val installedBytes: Long?,
    val verification: PackageVerification,
    val validation: PackageValidation,
    val dependencies: List<PackageDependency> = emptyList(),
    val limitations: List<String> = emptyList(),
    /** Requer rede em tempo de execução, privilégios de root ou capacidades indisponíveis sem root. */
    val requiresNetwork: Boolean = false,
    val tags: List<String> = emptyList()
) {
    init {
        if (schema != SCHEMA_VERSION) throw PackageManifestException("Schema não suportado: $schema")
        if (!NAME.matches(name)) throw PackageManifestException("Nome de pacote inválido: '$name'")
        if (!VERSION.matches(version)) throw PackageManifestException("Versão inválida: '$version'")
        if (title.isBlank() || title.length > 80) throw PackageManifestException("Título inválido")
        if (description.isBlank() || description.length > 500) throw PackageManifestException("Descrição inválida")
        if (downloadBytes != null && downloadBytes <= 0) throw PackageManifestException("downloadBytes inválido")
        if (installedBytes != null && installedBytes <= 0) throw PackageManifestException("installedBytes inválido")
        if (source.type != PackageSourceType.DEBIAN_APT) {
            if (verification.sha256 == null) throw PackageManifestException("Artefato direto exige SHA-256")
            if (downloadBytes == null) throw PackageManifestException("Artefato direto exige downloadBytes")
        }
        if (validation == PackageValidation.VERIFIED && verification.evidence.isNullOrBlank()) {
            throw PackageManifestException("Estado 'verified' exige evidência de validação")
        }
        if (validation == PackageValidation.KNOWN_LIMITED && limitations.isEmpty()) {
            throw PackageManifestException("Estado 'known-limited' exige ao menos uma limitação")
        }
        if (limitations.any { it.isBlank() }) throw PackageManifestException("Limitação vazia")
        if (dependencies.any { it.name == name }) throw PackageManifestException("Pacote depende de si mesmo")
        if (dependencies.map { it.name }.toSet().size != dependencies.size) {
            throw PackageManifestException("Dependências duplicadas")
        }
        if (tags.any { !TAG.matches(it) }) throw PackageManifestException("Tag inválida")
    }

    /** Verdadeiro se o pacote pode ser instalado em um RootFS da arquitetura informada. */
    fun isCompatibleWith(deviceArchitecture: PackageArchitecture): Boolean =
        architecture == PackageArchitecture.ALL || architecture == deviceArchitecture

    /** Chave estável usada por catálogo, histórico e dependências. */
    val key: String get() = "$name@$version".lowercase(Locale.ROOT)

    companion object {
        const val SCHEMA_VERSION = 1
        internal val NAME = Regex("[a-z0-9][a-z0-9+._-]{0,63}")
        internal val VERSION = Regex("[0-9][A-Za-z0-9.+~:_-]{0,63}")
        internal val TAG = Regex("[a-z0-9][a-z0-9-]{0,31}")
    }
}
