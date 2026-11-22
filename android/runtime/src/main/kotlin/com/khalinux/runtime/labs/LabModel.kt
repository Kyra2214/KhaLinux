package com.khalinux.runtime.labs

import com.khalinux.runtime.packages.PackageManifestException
import java.security.MessageDigest

/** Trilhas de laboratório (Fase 6). */
enum class LabTrack(val id: String) {
    LINUX("linux"),
    NETWORK("rede"),
    WEB("web"),
    FORENSICS("forense");

    companion object {
        fun fromId(id: String): LabTrack =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Trilha de laboratório inválida: '$id'")
    }
}

/** Dificuldade progressiva; define a pontuação base do exercício (aplicada no motor, submódulo 6.2/6.3). */
enum class LabDifficulty(val id: String, val basePoints: Int) {
    BEGINNER("iniciante", 10),
    INTERMEDIATE("intermediario", 20),
    ADVANCED("avancado", 40);

    companion object {
        fun fromId(id: String): LabDifficulty =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: throw PackageManifestException("Dificuldade inválida: '$id'")
    }
}

/** Dica opcional; revelar uma dica pode custar pontos (regra aplicada pelo motor). */
data class LabHint(val text: String, val cost: Int) {
    init {
        if (text.isBlank() || text.length > 400) throw PackageManifestException("Texto de dica inválido")
        if (cost !in 0..100) throw PackageManifestException("Custo de dica inválido: $cost")
    }
}

/**
 * Exercício com resposta verificável. A resposta nunca é guardada em texto claro: apenas o SHA-256 da
 * resposta normalizada (ver [AnswerHasher]), o que não impede descobri-la por tentativa, apenas evita
 * entregá-la junto com o enunciado.
 */
data class LabExercise(
    val id: String,
    val title: String,
    val instructions: String,
    val difficulty: LabDifficulty,
    val answerSha256: String,
    val ignoreCase: Boolean,
    val hints: List<LabHint>
) {
    init {
        if (!ID.matches(id)) throw PackageManifestException("Id de exercício inválido: '$id'")
        if (title.isBlank() || title.length > 80) throw PackageManifestException("Título inválido no exercício $id")
        if (instructions.isBlank() || instructions.length > 2000) throw PackageManifestException("Enunciado inválido no exercício $id")
        if (!SHA256.matches(answerSha256)) throw PackageManifestException("SHA-256 da resposta inválido no exercício $id")
        if (hints.size > 5) throw PackageManifestException("Máximo de 5 dicas no exercício $id")
    }

    companion object {
        internal val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
        internal val SHA256 = Regex("[a-f0-9]{64}")
    }
}

/**
 * Serviço local exposto por um laboratório. Só é permitido escutar em loopback e em porta não privilegiada;
 * o comando roda no RootFS e não pode conter metacaracteres de shell.
 */
data class LabService(val name: String, val command: String, val bindAddress: String, val port: Int) {
    init {
        if (!NAME.matches(name)) throw PackageManifestException("Nome de serviço inválido: '$name'")
        if (bindAddress !in LOOPBACK) {
            throw PackageManifestException("Serviço '$name' deve escutar apenas em loopback (127.0.0.1, ::1 ou localhost)")
        }
        if (port !in 1024..65535) throw PackageManifestException("Porta inválida no serviço '$name': $port")
        if (command.isBlank() || command.length > 300 || command.any { it in FORBIDDEN }) {
            throw PackageManifestException("Comando inválido no serviço '$name'")
        }
    }

    companion object {
        internal val NAME = Regex("[a-z0-9][a-z0-9-]{0,39}")
        internal val LOOPBACK = setOf("127.0.0.1", "::1", "localhost")
        private val FORBIDDEN = setOf(';', '&', '|', '<', '>', '$', '`', '\n', '\r', '\\', '"', '\'')
    }
}

/**
 * Descritor de laboratório. Escopo sempre local: alvos externos não existem no modelo e os serviços
 * só escutam em loopback. Ferramentas exigidas devem existir no Cyber Toolkit (validado pelo catálogo).
 */
data class LabDescriptor(
    val id: String,
    val track: LabTrack,
    val title: String,
    val summary: String,
    val difficulty: LabDifficulty,
    val requiredTools: List<String>,
    /** Laboratórios que devem estar concluídos antes (progressão). */
    val prerequisites: List<String>,
    val services: List<LabService>,
    val exercises: List<LabExercise>,
    /** Caminho relativo da documentação do laboratório, em `docs/labs/`. */
    val doc: String
) {
    init {
        if (!LabExercise.ID.matches(id)) throw PackageManifestException("Id de laboratório inválido: '$id'")
        if (title.isBlank() || title.length > 80) throw PackageManifestException("Título inválido no laboratório $id")
        if (summary.isBlank() || summary.length > 300) throw PackageManifestException("Resumo inválido no laboratório $id")
        if (exercises.isEmpty()) throw PackageManifestException("Laboratório $id sem exercícios")
        if (exercises.map { it.id }.toSet().size != exercises.size) throw PackageManifestException("Exercícios duplicados em $id")
        if (services.map { it.name }.toSet().size != services.size) throw PackageManifestException("Serviços duplicados em $id")
        if (services.map { it.port }.toSet().size != services.size) throw PackageManifestException("Portas duplicadas em $id")
        if (id in prerequisites) throw PackageManifestException("Laboratório $id depende de si mesmo")
        if (requiredTools.toSet().size != requiredTools.size || prerequisites.toSet().size != prerequisites.size) {
            throw PackageManifestException("Listas com itens duplicados em $id")
        }
        if (!DOC.matches(doc)) throw PackageManifestException("Documentação inválida em $id: '$doc'")
    }

    companion object {
        internal val DOC = Regex("docs/labs/[a-z0-9][a-z0-9-]*\\.md")
    }
}

/** Normalização e hash de respostas (usado por autores para gerar `answer_sha256` e pelo motor para conferir). */
object AnswerHasher {
    /** Remove espaços nas pontas e colapsa espaços internos; opcionalmente ignora maiúsculas/minúsculas. */
    fun normalize(answer: String, ignoreCase: Boolean): String {
        val collapsed = answer.trim().replace(Regex("\\s+"), " ")
        return if (ignoreCase) collapsed.lowercase() else collapsed
    }

    fun sha256(answer: String, ignoreCase: Boolean): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalize(answer, ignoreCase).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
