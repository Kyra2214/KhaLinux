package com.khalinux.runtime.settings

/** Tema de cores padrão do terminal (cores ARGB). Só altera o fundo/primeiro plano padrão; a paleta ANSI vem do emulador. */
data class TerminalTheme(
    val index: Int,
    val label: String,
    val background: Int,
    val foreground: Int
)

object TerminalThemes {
    val all: List<TerminalTheme> = listOf(
        TerminalTheme(0, "Padrão", 0xFF0B0F12.toInt(), 0xFFE8F0F2.toInt()),
        TerminalTheme(1, "Claro", 0xFFFAFAFA.toInt(), 0xFF1B1F23.toInt()),
        TerminalTheme(2, "Alto contraste", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        TerminalTheme(3, "Verde clássico", 0xFF000000.toInt(), 0xFF33FF66.toInt()),
        TerminalTheme(4, "Solarized escuro", 0xFF002B36.toInt(), 0xFF93A1A1.toInt())
    )

    const val DEFAULT_INDEX = 0

    /** Índice inválido (ex.: vindo de um banco futuro) volta ao tema padrão. */
    fun byIndex(index: Int): TerminalTheme = all.firstOrNull { it.index == index } ?: all[DEFAULT_INDEX]
}
