package com.khalinux.runtime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalKeysTest {
    @Test fun rawControlBytes() {
        assertEquals("\u0003", TerminalKeys.encode(TerminalKey.CTRL_C))
        assertEquals("\u0004", TerminalKeys.encode(TerminalKey.CTRL_D))
        assertEquals("\u001b", TerminalKeys.encode(TerminalKey.ESC))
        assertEquals("\t", TerminalKeys.encode(TerminalKey.TAB))
    }

    @Test fun arrowsAndModifiers() {
        assertEquals("\u001b[A", TerminalKeys.encode(TerminalKey.UP))
        assertEquals("\u001b[1;5C", TerminalKeys.encode(TerminalKey.RIGHT, ctrl = true))
        assertEquals("\u001b[1;3D", TerminalKeys.encode(TerminalKey.LEFT, alt = true))
        assertEquals("\u001b[1;7B", TerminalKeys.encode(TerminalKey.DOWN, ctrl = true, alt = true))
        assertEquals("\u001b\t", TerminalKeys.encode(TerminalKey.TAB, alt = true))
    }

    @Test fun controlChars() {
        assertEquals('\u0001', TerminalKeys.controlChar('a'))
        assertEquals('\u001a', TerminalKeys.controlChar('Z'))
        assertEquals('\u001b', TerminalKeys.controlChar('['))
        assertNull(TerminalKeys.controlChar('1'))
    }

    @Test fun modifiersApplyOnlyToFirstCharacter() {
        assertEquals("\u0003", TerminalKeys.applyModifiers("c", ctrl = true, alt = false))
        assertEquals("\u001bxyz", TerminalKeys.applyModifiers("xyz", ctrl = false, alt = true))
        assertEquals("\u001b\u0004rest", TerminalKeys.applyModifiers("drest", ctrl = true, alt = true))
        assertEquals("abc", TerminalKeys.applyModifiers("abc", ctrl = false, alt = false))
    }
}
