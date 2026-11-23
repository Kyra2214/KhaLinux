package com.khalinux.runtime.packages

/**
 * Converte uma estrutura já decodificada (Map/List/String/Number/Boolean, como produzida por
 * qualquer leitor JSON) em [PackageManifest]. O módulo runtime não depende de biblioteca JSON;
 * o adaptador do app (org.json) é responsável por decodificar o texto e chamar [parse].
 *
 * Campos desconhecidos são rejeitados para que erros de digitação não passem despercebidos.
 */
object PackageManifestParser {
    private val ROOT_KEYS = setOf(
        "schema", "name", "version", "title", "description", "category", "architecture", "source",
        "download_bytes", "installed_bytes", "verification", "validation", "dependencies",
        "limitations", "requires_network", "tags"
    )
    private val SOURCE_KEYS = setOf("type", "url", "apt_name", "apt_suite", "apt_uri")
    private val VERIFICATION_KEYS = setOf("sha256", "evidence")
    private val DEPENDENCY_KEYS = setOf("name", "min_version")

    fun parse(root: Map<String, Any?>): PackageManifest {
        rejectUnknown(root, ROOT_KEYS, "manifesto")
        val source = map(root, "source")
        rejectUnknown(source, SOURCE_KEYS, "source")
        val verification = map(root, "verification")
        rejectUnknown(verification, VERIFICATION_KEYS, "verification")
        return PackageManifest(
            schema = int(root, "schema"),
            name = string(root, "name"),
            version = string(root, "version"),
            title = string(root, "title"),
            description = string(root, "description"),
            category = PackageCategory.fromId(string(root, "category")),
            architecture = PackageArchitecture.fromId(string(root, "architecture")),
            source = PackageSource(
                type = PackageSourceType.fromId(string(source, "type")),
                url = optionalString(source, "url"),
                aptName = optionalString(source, "apt_name"),
                aptSuite = optionalString(source, "apt_suite"),
                aptUri = optionalString(source, "apt_uri")
            ),
            downloadBytes = optionalLong(root, "download_bytes"),
            installedBytes = optionalLong(root, "installed_bytes"),
            verification = PackageVerification(
                sha256 = optionalString(verification, "sha256"),
                evidence = optionalString(verification, "evidence")
            ),
            validation = PackageValidation.fromId(string(root, "validation")),
            dependencies = list(root, "dependencies").map { entry ->
                val dependency = entry as? Map<*, *> ?: throw PackageManifestException("Dependência malformada")
                @Suppress("UNCHECKED_CAST")
                val typed = dependency as Map<String, Any?>
                rejectUnknown(typed, DEPENDENCY_KEYS, "dependência")
                PackageDependency(string(typed, "name"), optionalString(typed, "min_version"))
            },
            limitations = list(root, "limitations").map {
                it as? String ?: throw PackageManifestException("Limitação deve ser texto")
            },
            requiresNetwork = optionalBoolean(root, "requires_network") ?: false,
            tags = list(root, "tags").map { it as? String ?: throw PackageManifestException("Tag deve ser texto") }
        )
    }

    private fun rejectUnknown(map: Map<String, Any?>, allowed: Set<String>, where: String) {
        val unknown = map.keys - allowed
        if (unknown.isNotEmpty()) throw PackageManifestException("Campos desconhecidos em $where: ${unknown.sorted()}")
    }

    private fun string(map: Map<String, Any?>, key: String): String =
        map[key] as? String ?: throw PackageManifestException("Campo obrigatório ausente ou inválido: '$key'")

    private fun optionalString(map: Map<String, Any?>, key: String): String? {
        val value = map[key] ?: return null
        return value as? String ?: throw PackageManifestException("Campo '$key' deve ser texto")
    }

    private fun int(map: Map<String, Any?>, key: String): Int {
        val value = map[key] as? Number ?: throw PackageManifestException("Campo numérico ausente: '$key'")
        val long = value.toLong()
        if (long.toDouble() != value.toDouble() || long !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw PackageManifestException("Campo '$key' deve ser inteiro")
        }
        return long.toInt()
    }

    private fun optionalLong(map: Map<String, Any?>, key: String): Long? {
        val value = map[key] ?: return null
        val number = value as? Number ?: throw PackageManifestException("Campo '$key' deve ser numérico")
        val long = number.toLong()
        if (long.toDouble() != number.toDouble()) throw PackageManifestException("Campo '$key' deve ser inteiro")
        return long
    }

    private fun optionalBoolean(map: Map<String, Any?>, key: String): Boolean? {
        val value = map[key] ?: return null
        return value as? Boolean ?: throw PackageManifestException("Campo '$key' deve ser booleano")
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(map: Map<String, Any?>, key: String): Map<String, Any?> =
        map[key] as? Map<String, Any?> ?: throw PackageManifestException("Objeto obrigatório ausente: '$key'")

    private fun list(map: Map<String, Any?>, key: String): List<Any?> {
        val value = map[key] ?: return emptyList()
        return value as? List<Any?> ?: throw PackageManifestException("Campo '$key' deve ser lista")
    }
}
