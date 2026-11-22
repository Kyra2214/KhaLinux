package com.khalinux.runtime.toolkit

import com.khalinux.runtime.packages.PackageCatalogParser
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Valida os arquivos reais `catalog.json` e `tools.json` com o parser e as invariantes do runtime. */
class ToolkitDataTest {
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "distribution/toolkit/tools.json").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "raiz do repositório não encontrada" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun json(file: File): Map<String, Any?> = MiniJson(file.readText(Charsets.UTF_8)).parse() as Map<String, Any?>

    @Test fun realCatalogAndToolkitAreConsistent() {
        val root = repoRoot()
        val catalog = PackageCatalogParser.parse(json(File(root, "distribution/packages/catalog/catalog.json")))
        val registry = ToolkitRegistryParser.parse(json(File(root, "distribution/toolkit/tools.json")), catalog)
        assertTrue(registry.tools.size >= 15)
        for (tool in registry.tools) assertTrue(File(root, tool.doc).isFile, "doc ausente: ${tool.doc}")
        val groups = registry.groups().map { it.first }.toSet()
        assertEquals(setOf(ToolGroup.RECON, ToolGroup.NETWORK, ToolGroup.WEB, ToolGroup.FORENSICS, ToolGroup.AUDIT), groups)
        // Nenhuma ferramenta pode declarar-se verificada sem evidência.
        assertTrue(registry.tools.none { it.verified })
    }

    @Test fun networkInteractingToolsRequireAuthorization() {
        val root = repoRoot()
        val catalog = PackageCatalogParser.parse(json(File(root, "distribution/packages/catalog/catalog.json")))
        val registry = ToolkitRegistryParser.parse(json(File(root, "distribution/toolkit/tools.json")), catalog)
        for (id in listOf("nmap", "openssh-client", "whatweb", "sqlmap", "wfuzz")) {
            assertTrue(registry.find(id)!!.requiresAuthorization, "$id deve exigir confirmação de escopo")
        }
    }
}

/** Leitor JSON mínimo (somente para testes no host; o app usa org.json). */
internal class MiniJson(private val s: String) {
    private var i = 0
    fun parse(): Any? { val v = value(); ws(); check(i == s.length) { "JSON com conteúdo extra" }; return v }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun value(): Any? {
        ws()
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> { check(c == '-' || c.isDigit()); num() }
        }
    }
    private fun obj(): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>(); i++; ws()
        if (s[i] == '}') { i++; return m }
        while (true) { ws(); val k = str(); ws(); check(s[i++] == ':'); m[k] = value(); ws(); if (s[i] == ',') i++ else { check(s[i++] == '}'); return m } }
    }
    private fun arr(): List<Any?> {
        val l = mutableListOf<Any?>(); i++; ws()
        if (s[i] == ']') { i++; return l }
        while (true) { l += value(); ws(); if (s[i] == ',') i++ else { check(s[i++] == ']'); return l } }
    }
    private fun str(): String {
        check(s[i++] == '"'); val b = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') {
                i++
                when (val e = s[i++]) {
                    'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                    'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> b.append(e)
                }
            } else b.append(s[i++])
        }
        i++; return b.toString()
    }
    private fun num(): Any {
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        val t = s.substring(st, i)
        return t.toLongOrNull() ?: t.toDouble()
    }
}
