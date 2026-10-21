package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.ui.gridColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page's columns and density, and the cards the server hides (labhud 0.1.2), as the app reads them. */
class PageLookTest {
    private val state = """
        {"title": "Lab", "now": 1, "problems": [], "alerts": [{"id": "r1", "text": "B is down", "card": "b", "since": 1}],
         "pages": [{"id": "main", "title": "MAIN", "columns": 3, "density": "compact",
                    "groups": [{"id": "g", "title": "G", "cards": ["a"]}]},
                   {"id": "other", "title": "OTHER", "groups": []}],
         "cards": [{"id": "a", "name": "A", "page": "main", "group": "g", "status": "up", "bad": false}]}
    """

    @Test fun pageColumnsAndDensity() {
        val st = LabJson.decodeFromString<LabState>(state)
        val main = st.pages[0]
        assertEquals(3, main.columns)
        assertTrue(main.compact)
        assertEquals(0, st.pages[1].columns)
        assertEquals("", st.pages[1].density)
        assertFalse(st.pages[1].compact)
    }

    @Test fun anOlderServerSetsNeither() {
        val st = LabJson.decodeFromString<LabState>(state.replace(""", "columns": 3, "density": "compact"""", ""))
        assertEquals(0, st.pages[0].columns)
        assertFalse(st.pages[0].compact)
    }

    @Test fun aHiddenCardIsNotInTheStateButItsAlertIs() {
        val st = LabJson.decodeFromString<LabState>(state)
        assertNull(st.byId["b"])
        assertEquals("b", st.alerts.single().card)
        // every id a group names is a card
        st.pages.flatMap { p -> p.groups.flatMap { it.cards } }.forEach { assertTrue(it in st.byId) }
    }

    @Test fun gridColumns() {
        // 158 px cells with 10 px between: 3 fit in 500 px, 2 in 340 px, always at least one
        assertEquals(3, gridColumns(500, 10, 158, 0))
        assertEquals(2, gridColumns(340, 10, 158, 0))
        assertEquals(1, gridColumns(100, 10, 158, 0))
        // a page's cap only lowers the number, never raises it
        assertEquals(2, gridColumns(500, 10, 158, 2))
        assertEquals(3, gridColumns(500, 10, 158, 6))
        assertEquals(1, gridColumns(500, 10, 158, 1))
    }
}
