package com.khalinux.runtime.reports

import com.khalinux.runtime.files.HomeFileManager
import com.khalinux.runtime.labs.LabProgress
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
    @Test
    fun emptyReportHasSummaryAndPolicy() {
        val text = ReportBuilder.build(0L, emptyList(), LabProgress(), emptyList())
        assertTrue(text.contains("# Relatório KhaLinux"))
        assertTrue(text.contains("1970-01-01 00:00:00 UTC"))
        assertTrue(text.contains("Pontos totais: 0"))
        assertTrue(text.contains("Uso autorizado"))
    }

    @Test
    fun fileNameIsUtcStamp() {
        assertEquals("relatorio-19700101-000000.md", ReportBuilder.fileName(0L))
    }

    @Test
    fun writerDoesNotOverwriteSameSecond() {
        val root = Files.createTempDirectory("rep").toFile().also { it.deleteOnExit() }
        val writer = ReportWriter(HomeFileManager(root))
        val first = writer.write(0L, "a")
        val second = writer.write(0L, "b")
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        assertEquals("a", HomeFileManager(root).readText(first!!))
    }
}
