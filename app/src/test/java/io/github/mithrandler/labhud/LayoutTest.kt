package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.HookAnswer
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LayoutCard
import io.github.mithrandler.labhud.data.LayoutGroup
import io.github.mithrandler.labhud.data.LayoutPage
import io.github.mithrandler.labhud.data.LayoutView
import io.github.mithrandler.labhud.data.UpdateWindow
import io.github.mithrandler.labhud.data.editCard
import io.github.mithrandler.labhud.data.editGroup
import io.github.mithrandler.labhud.data.freeGroupId
import io.github.mithrandler.labhud.data.kept
import io.github.mithrandler.labhud.data.layoutBody
import io.github.mithrandler.labhud.data.moveCard
import io.github.mithrandler.labhud.data.moveCardTo
import io.github.mithrandler.labhud.data.moveGroup
import io.github.mithrandler.labhud.data.movePage
import io.github.mithrandler.labhud.data.moved
import io.github.mithrandler.labhud.data.words
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private fun card(id: String) = LayoutCard(id = id, name = id.uppercase())

    private val pages = listOf(
        LayoutPage(
            id = "home", title = "Home", groups = listOf(
                LayoutGroup("g1", "One", cards = listOf(card("a"), card("b"), card("c"))),
                LayoutGroup("g2", "Two", cards = listOf(card("d"))),
            )
        ),
        LayoutPage(id = "lab", title = "Lab", groups = listOf(LayoutGroup("g3", "Three", cards = listOf(card("e"))))),
    )

    private fun ids(p: List<LayoutPage>, page: String, group: String) =
        p.first { it.id == page }.groups.first { it.id == group }.cards.map { it.id }

    @Test fun movedShiftsAndStaysAtTheEdges() {
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(0, 1))
        assertEquals(listOf(1, 3, 2), listOf(1, 2, 3).moved(2, -1))
        val l = listOf(1, 2, 3)
        assertSame(l, l.moved(0, -1))
        assertSame(l, l.moved(2, 1))
        assertSame(l, l.moved(-1, 1))
        assertSame(l, l.moved(5, -1))
    }

    @Test fun movePageAndGroupAndCard() {
        assertEquals(listOf("lab", "home"), pages.movePage("home", 1).map { it.id })
        assertSame(pages, pages.movePage("missing", 1))
        assertEquals(listOf("g2", "g1"), pages.moveGroup("home", "g1", 1)[0].groups.map { it.id })
        assertEquals(listOf("b", "a", "c"), ids(pages.moveCard("g1", "a", 1), "home", "g1"))
        assertEquals(listOf("a", "b", "c"), ids(pages.moveCard("g1", "a", -1), "home", "g1"))
    }

    @Test fun editsTouchOnlyTheirTarget() {
        val e = pages.editCard("b") { it.copy(size = "xl", hidden = true) }
        val b = e[0].groups[0].cards[1]
        assertEquals("xl", b.size); assertTrue(b.hidden)
        assertEquals("", e[0].groups[0].cards[0].size)
        val g = pages.editGroup("g3") { it.copy(title = "Renamed") }
        assertEquals("Renamed", g[1].groups[0].title)
        assertEquals("One", g[0].groups[0].title)
    }

    @Test fun freeGroupIdSkipsTheTakenOnes() {
        assertEquals("group-1", pages.freeGroupId())
        val taken = pages.editPageGroups { it + LayoutGroup("group-1", "x") + LayoutGroup("group-2", "y") }
        assertEquals("group-3", taken.freeGroupId())
    }

    private fun List<LayoutPage>.editPageGroups(f: (List<LayoutGroup>) -> List<LayoutGroup>) =
        mapIndexed { i, p -> if (i == 0) p.copy(groups = f(p.groups)) else p }

    @Test fun moveCardToAnExistingGroupOnAnotherPage() {
        val m = pages.moveCardTo("a", "lab", "g3", null)
        assertEquals(listOf("e", "a"), ids(m, "lab", "g3"))
        assertEquals(listOf("b", "c"), ids(m, "home", "g1"))
    }

    @Test fun moveCardToANewGroupGetsAFreeIdAndTheTitle() {
        val m = pages.moveCardTo("d", "lab", null, "  Fresh  ")
        val g = m[1].groups.last()
        assertEquals("group-1", g.id); assertEquals("Fresh", g.title)
        assertEquals(listOf("d"), g.cards.map { it.id })
        // the source group is left empty here: kept() drops it
        assertTrue(m[0].groups.first { it.id == "g2" }.cards.isEmpty())
        assertEquals(listOf("g1"), m.kept()[0].groups.map { it.id })
    }

    @Test fun moveCardToANewGroupWithoutATitleOrAnUnknownGroup() {
        assertEquals("New group", pages.moveCardTo("a", "lab", null, "  ").let { it[1].groups.last().title })
        // a group id that is not on that page makes a new group instead of losing the card
        val m = pages.moveCardTo("a", "lab", "nope", null)
        assertTrue(m[1].groups.any { g -> g.cards.any { it.id == "a" } })
        assertEquals(5, m.sumOf { p -> p.groups.sumOf { it.cards.size } })
    }

    @Test fun moveCardToWithAnUnknownCardChangesNothing() {
        assertSame(pages, pages.moveCardTo("zzz", "lab", "g3", null))
    }

    @Test fun keptDropsEmptyGroupsAndPagesButNotAutoOnesOrGames() {
        val p = listOf(
            LayoutPage("a", "A", groups = listOf(LayoutGroup("x", "X"), LayoutGroup("y", "Y", cards = listOf(card("c"))))),
            LayoutPage("b", "B", groups = listOf(LayoutGroup("z", "Z"))),
            LayoutPage("c", "C", groups = listOf(LayoutGroup("pve", "PVE", auto = true))),
            LayoutPage("d", "D", games = true),
        )
        val k = p.kept()
        assertEquals(listOf("a", "c", "d"), k.map { it.id })
        assertEquals(listOf("y"), k[0].groups.map { it.id })
        assertEquals(listOf("pve"), k[1].groups.map { it.id })
    }

    @Test fun layoutBodyCarriesEveryValueEvenWhenCleared() {
        val p = listOf(
            LayoutPage("home", "Home", icon = "", columns = 0, density = "compact", groups = listOf(
                LayoutGroup("g1", "One", hidden = true, cards = listOf(LayoutCard("a", "A", icon = "", size = "l", hidden = true)))
            ))
        )
        val o = Json.parseToJsonElement(layoutBody(p)).jsonObject
        val page = o["pages"]!!.jsonArray[0].jsonObject
        assertEquals("", page["icon"]!!.jsonPrimitive.content)
        assertEquals(0, page["columns"]!!.jsonPrimitive.int)
        assertEquals("compact", page["density"]!!.jsonPrimitive.content)
        val g = page["groups"]!!.jsonArray[0].jsonObject
        assertTrue(g["hidden"]!!.jsonPrimitive.boolean)
        val c = g["cards"]!!.jsonArray[0].jsonObject
        assertEquals("a", c["id"]!!.jsonPrimitive.content)
        assertEquals("", c["icon"]!!.jsonPrimitive.content)
        assertEquals("l", c["size"]!!.jsonPrimitive.content)
        // names and kinds are the server's: not sent back
        assertFalse(c.containsKey("name")); assertFalse(c.containsKey("kind"))
    }

    @Test fun layoutViewReadsTheServerAnswerAndAnError() {
        val v = LabJson.decodeFromString<LayoutView>(
            """{"ok":true,"pages":[{"id":"home","title":"Home","extra":1,"groups":[{"id":"g","title":"G","auto":true,"cards":[{"id":"a","name":"A","guess":"nas"}]}]}]}"""
        )
        assertTrue(v.ok)
        assertEquals("nas", v.pages[0].groups[0].cards[0].guess)
        assertTrue(v.pages[0].groups[0].auto)
        val bad = LabJson.decodeFromString<LayoutView>("""{"ok":false,"error":"no config"}""")
        assertEquals("no config", bad.error); assertTrue(bad.pages.isEmpty())
    }

    // --- Containers: webhook answer and update window wording ---

    @Test fun hookAnswerCarriesTheUrlOnceOrAnError() {
        val ok = LabJson.decodeFromString<HookAnswer>("""{"ok":true,"url":"https://h/hook/abc"}""")
        assertTrue(ok.ok); assertEquals("https://h/hook/abc", ok.url)
        val no = LabJson.decodeFromString<HookAnswer>("""{"ok":false,"error":"unknown target"}""")
        assertFalse(no.ok); assertEquals("", no.url); assertEquals("unknown target", no.error)
    }

    @Test fun updateWindowWording() {
        assertEquals("Every day at 04:00", UpdateWindow((0..6).toList(), "04:00").words())
        assertEquals("Sat, Sun at 02:15", UpdateWindow(listOf(6, 5), "02:15").words())
    }
}
