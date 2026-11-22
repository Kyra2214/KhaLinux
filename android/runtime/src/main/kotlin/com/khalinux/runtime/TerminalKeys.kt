package com.khalinux.runtime

/** Teclas da barra extra e sua codificação em bytes brutos para o PTY. */
enum class TerminalKey(val label: String) {
    ESC("ESC"), TAB("Tab"), UP("↑"), DOWN("↓"), RIGHT("→"), LEFT("←"),
    HOME("Home"), END("End"), CTRL_C("Ctrl+C"), CTRL_D("Ctrl+D")
}

object TerminalKeys {
    private const val ESC = "\u001b"

    /** Sequência enviada ao PTY. Ctrl+C = 0x03, Ctrl+D = 0x04; Alt prefixa ESC; setas usam o parâmetro xterm de modificador. */
    fun encode(key: TerminalKey, ctrl: Boolean = false, alt: Boolean = false): String {
        val modifier = 1 + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
        fun csi(final: Char): String =
            if (modifier == 1) "$ESC[$final" else "$ESC[1;$modifier$final"
        return when (key) {
            TerminalKey.ESC -> if (alt) "$ESC$ESC" else ESC
            TerminalKey.TAB -> if (alt) "$ESC\t" else "\t"
            TerminalKey.UP -> csi('A')
            TerminalKey.DOWN -> csi('B')
            TerminalKey.RIGHT -> csi('C')
            TerminalKey.LEFT -> csi('D')
            TerminalKey.HOME -> csi('H')
            TerminalKey.END -> csi('F')
            TerminalKey.CTRL_C -> if (alt) "$ESC\u0003" else "\u0003"
            TerminalKey.CTRL_D -> if (alt) "$ESC\u0004" else "\u0004"
        }
    }

    /** Caractere de controle para [char] com Ctrl (A–Z, [ \ ] ^ _, @, espaço e ?); nulo se não houver. */
    fun controlChar(char: Char): Char? = when (char) {
        in 'a'..'z' -> (char.code - 'a'.code + 1).toChar()
        in 'A'..'Z' -> (char.code - 'A'.code + 1).toChar()
        '@', ' ' -> '\u0000'
        '[' -> '\u001b'
        '\\' -> '\u001c'
        ']' -> '\u001d'
        '^' -> '\u001e'
        '_' -> '\u001f'
        '?' -> '\u007f'
        else -> null
    }

    /** Aplica Ctrl/Alt ao primeiro caractere do texto digitado; o restante segue sem alteração. */
    fun applyModifiers(text: String, ctrl: Boolean, alt: Boolean): String {
        if (text.isEmpty() || (!ctrl && !alt)) return text
        val first = text[0]
        val head = if (ctrl) controlChar(first)?.toString() ?: first.toString() else first.toString()
        return (if (alt) "$ESC$head" else head) + text.substring(1)
    }
}
