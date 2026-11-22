package com.khalinux.runtime.labs

import com.khalinux.runtime.packages.PackageCatalogParser
import com.khalinux.runtime.toolkit.MiniJson
import com.khalinux.runtime.toolkit.ToolkitRegistryParser
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Valida o `labs.json` real com o parser, o toolkit, a documentação e as fixtures dos serviços. */
class LabsDataTest {
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "distribution/labs/labs.json").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "raiz do repositório não encontrada" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun json(file: File): Map<String, Any?> = MiniJson(file.readText(Charsets.UTF_8)).parse() as Map<String, Any?>

    private fun catalog(): LabCatalog {
        val root = repoRoot()
        val packages = PackageCatalogParser.parse(json(File(root, "distribution/packages/catalog/catalog.json")))
        val toolkit = ToolkitRegistryParser.parse(json(File(root, "distribution/toolkit/tools.json")), packages)
        return LabCatalogParser.parse(json(File(root, "distribution/labs/labs.json")), toolkit)
    }

    @Test fun realCatalogParsesAndCoversAllTracks() {
        val catalog = catalog()
        assertEquals(LabTrack.values().toSet(), catalog.labs.map { it.track }.toSet())
        assertTrue(catalog.labs.size >= 12)
        assertTrue(catalog.labs.all { it.exercises.isNotEmpty() })
    }

    @Test fun docsAndFixturesExist() {
        val root = repoRoot()
        for (lab in catalog().labs) {
            assertTrue(File(root, lab.doc).isFile, "doc ausente: ${lab.doc}")
            for (service in lab.services) {
                // Fixtures: "python3 /opt/khalinux-labs/<trilha>/<script> <porta>". Serviços sem fixture
                // (ex.: `python3 -m http.server 8080 --bind 127.0.0.1`) não têm arquivo a conferir.
                val script = service.command.split(' ').firstOrNull { it.startsWith("/opt/khalinux-labs/") }
                if (script != null) {
                    val relative = script.removePrefix("/opt/khalinux-labs/")
                    assertTrue(File(root, "distribution/labs/fixtures/$relative").isFile, "fixture ausente: $relative")
                    assertTrue(service.command.endsWith(" ${service.port}"), "porta do comando difere da declarada em ${lab.id}")
                } else {
                    assertTrue(service.command.contains("--bind ${service.bindAddress}"), "serviço sem --bind em ${lab.id}")
                    assertTrue(service.command.contains(" ${service.port} ") || service.command.contains(" ${service.port}"), "porta ausente em ${lab.id}")
                }
            }
        }
    }

    @Test fun servicesNeverBindOutsideLoopback() {
        assertTrue(catalog().labs.flatMap { it.services }.all { it.bindAddress in setOf("127.0.0.1", "::1", "localhost") })
    }

    @Test fun answerKeysMatchStoredHashes() {
        val exercises = catalog().labs.flatMap { it.exercises }.associateBy { it.id }
        val keys = mapOf(
            "ls-ocultos" to "-a", "umask-diretorio" to "750", "hash-prefixo" to "70ddf8f7",
            "flag-segredo" to "KHA{http-basico-7c21}", "bypass-admin" to "KHA{sqli-local-3d9f}", "ip-top" to "203.0.113.9"
        )
        for ((id, answer) in keys) {
            val ex = exercises.getValue(id)
            assertEquals(ex.answerSha256, AnswerHasher.sha256(answer, ex.ignoreCase), "resposta de $id")
        }
    }
}
