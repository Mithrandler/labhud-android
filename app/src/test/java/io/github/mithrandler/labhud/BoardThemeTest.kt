package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.BoardTheme
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LabState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The board's theme and the role, as /api/v1/state carries them (labhud 0.1.4). */
class BoardThemeTest {
    @Test fun themeAndRoleAreRead() {
        val st = LabJson.decodeFromString<LabState>(
            """{"title": "Lab", "now": 1, "pages": [], "cards": [], "problems": [], "alerts": [],
                "theme": {"base": "grey", "accent": "green"}, "role": "viewer"}""")
        assertEquals(BoardTheme("grey", "green"), st.theme)
        assertTrue(st.theme.grey)
        assertEquals("viewer", st.role)
    }

    @Test fun anOlderServerMeansTheWallsOwnLook() {
        val st = LabJson.decodeFromString<LabState>("""{"title": "Lab", "pages": [], "cards": []}""")
        assertEquals(BoardTheme(), st.theme)
        assertFalse(st.theme.grey)
        assertEquals("", st.role)
    }

    @Test fun accentColours() {
        assertEquals(0xFF8AB4F8L to 0xFF7CC4FFL, BoardTheme("oled", "blue").accentArgb)
        assertEquals(0xFF39FF88L to 0xFF5CFFA0L, BoardTheme("oled", "green").accentArgb)
        assertEquals(0xFFAA00FFL to 0xFFAA00FFL, BoardTheme("oled", "#AA00FF").accentArgb)
    }

    @Test fun anUnknownAccentFallsBackToBlue() {
        assertEquals(BoardTheme().accentArgb, BoardTheme("oled", "teal").accentArgb)
        assertEquals(BoardTheme().accentArgb, BoardTheme("oled", "#abc").accentArgb)
    }

    @Test fun lightIsTrueOnlyForPaper() {
        assertTrue(BoardTheme("paper", "blue").light)
        assertFalse(BoardTheme("oled", "blue").light)
        assertFalse(BoardTheme("grey", "blue").light)
        assertFalse(BoardTheme("terminal", "blue").light)
        assertFalse(BoardTheme("glass", "blue").light)
    }

    @Test fun monoIsTrueOnlyForTerminal() {
        assertTrue(BoardTheme("terminal", "blue").mono)
        assertFalse(BoardTheme("paper", "blue").mono)
        assertFalse(BoardTheme("oled", "blue").mono)
        assertFalse(BoardTheme("grey", "blue").mono)
        assertFalse(BoardTheme("glass", "blue").mono)
    }

    @Test fun ownAccentPerBase() {
        assertTrue(BoardTheme("paper", "blue").ownAccent)
        assertTrue(BoardTheme("terminal", "blue").ownAccent)
        assertTrue(BoardTheme("glass", "blue").ownAccent)
        assertFalse(BoardTheme("oled", "blue").ownAccent)
        assertFalse(BoardTheme("grey", "blue").ownAccent)
    }

    @Test fun decodingTerminalTheme() {
        val theme = LabJson.decodeFromString<BoardTheme>("""{"base": "terminal", "accent": "blue"}""")
        assertEquals(BoardTheme("terminal", "blue"), theme)
        assertTrue(theme.mono)
    }
}
