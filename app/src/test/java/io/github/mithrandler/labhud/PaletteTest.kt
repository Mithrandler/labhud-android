package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Action
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Group
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.Page
import io.github.mithrandler.labhud.ui.HitKind
import io.github.mithrandler.labhud.ui.paletteHits
import io.github.mithrandler.labhud.ui.paletteItems
import io.github.mithrandler.labhud.ui.paletteNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    private val st = LabState(
        pages = listOf(
            Page("infra", "Infrastructure", listOf(Group("docker", "Docker", listOf("jellyfin", "rețea")))),
            Page("games", "Games", games = true),
        ),
        cards = listOf(
            Card(id = "jellyfin", name = "Jellyfin", actions = listOf(Action("jellyfin-restart", "Restart"), Action("jellyfin-stop", "Stop", danger = true))),
            Card(id = "rețea", name = "Rețea locală"),
        ),
    )

    @Test
    fun `diacritics and case do not matter`() {
        assertEquals("retea locala", paletteNorm("Rețea Locală"))
        assertEquals(listOf("rețea"), paletteHits(paletteItems(st, admin = false), "retea").map { it.target })
    }

    @Test
    fun `a viewer sees no buttons and no settings`() {
        val kinds = paletteItems(st, admin = false).map { it.kind }.toSet()
        assertEquals(setOf(HitKind.Page, HitKind.Card), kinds)
        assertTrue(paletteHits(paletteItems(st, admin = false), ">").isEmpty())
    }

    @Test
    fun `the admin gets the buttons, and the dangerous one asks first`() {
        val items = paletteItems(st, admin = true)
        val stop = items.single { it.kind == HitKind.Action && it.action == "jellyfin-stop" }
        assertEquals("stop · Jellyfin", stop.label)
        assertEquals("asks first", stop.hint)
        assertEquals("jellyfin", stop.target)
        assertTrue(items.any { it.kind == HitKind.Section && it.target == "containers" })
    }

    @Test
    fun `a lone angle bracket lists the buttons, a word after it only the commands`() {
        val items = paletteItems(st, admin = true)
        assertEquals(listOf("jellyfin-restart", "jellyfin-stop"), paletteHits(items, ">").map { it.action })
        val hits = paletteHits(items, "> jelly")
        assertTrue(hits.isNotEmpty() && hits.none { it.kind == HitKind.Card })
        assertTrue(paletteHits(items, "jelly").any { it.kind == HitKind.Card })
    }

    @Test
    fun `a label that starts with the word comes first`() {
        val hits = paletteHits(paletteItems(st, admin = true), "restart")
        assertEquals("restart · Jellyfin", hits.first().label)
    }

    @Test
    fun `every word must be there`() {
        val items = paletteItems(st, admin = false)
        assertEquals(listOf("jellyfin"), paletteHits(items, "jelly docker").map { it.target })
        assertTrue(paletteHits(items, "jelly nothing").isEmpty())
    }

    @Test
    fun `a games page is offered, without cards of its own`() {
        val hits = paletteHits(paletteItems(st, admin = false), "games")
        assertEquals(listOf(HitKind.Page), hits.map { it.kind })
    }
}
