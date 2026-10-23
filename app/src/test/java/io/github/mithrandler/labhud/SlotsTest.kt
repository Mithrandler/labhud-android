package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Group
import io.github.mithrandler.labhud.data.Guest
import io.github.mithrandler.labhud.data.Metric
import io.github.mithrandler.labhud.data.Page
import io.github.mithrandler.labhud.ui.Slot
import io.github.mithrandler.labhud.ui.TITLE_H
import io.github.mithrandler.labhud.ui.buildSlots
import io.github.mithrandler.labhud.ui.headline
import io.github.mithrandler.labhud.ui.keepTitlesWithCards
import io.github.mithrandler.labhud.ui.packGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a small tile says, and how a Status page is cut into titles, tiles and screens. */
class SlotsTest {
    private fun pct(label: String, v: Double, sev: String = "") = Metric(key = "p.$label", label = label, value = "${v.toInt()}%", pct = v, sev = sev)
    private fun card(id: String, vararg m: Metric, bad: Boolean? = null, type: String = "") = Card(id = id, name = id, metrics = m.toList(), bad = bad, type = type)

    @Test fun theHeadlineIsTheWorstOfCpuRamAndDiskAndTheCpuWhenAllAreFine() {
        assertEquals("CPU", card("a", pct("Disk", 40.0), pct("CPU", 10.0), pct("RAM", 20.0)).headline()?.label)
        assertEquals("RAM", card("a", pct("CPU", 10.0), pct("RAM", 85.0, "warn"), pct("Disk", 40.0)).headline()?.label)
        assertEquals("Disk", card("a", pct("CPU", 95.0, "warn"), pct("RAM", 20.0), pct("Disk", 97.0, "crit")).headline()?.label)
        assertEquals("CPU", card("a", pct("CPU", 95.0, "crit"), pct("RAM", 20.0), pct("Disk", 97.0, "crit")).headline()?.label)   // equal: the CPU first
    }

    @Test fun aGuestsRamCountsOnlyByPressure() {
        val g = Card(id = "vm", guest = Guest(cpu = 10.0, mem = 97.0, disk = 30.0, memSev = ""))
        assertEquals("CPU", g.headline()?.label)
        val stalled = Card(id = "vm", guest = Guest(cpu = 10.0, mem = 50.0, disk = 30.0, memSev = "crit"))
        assertEquals("RAM", stalled.headline()?.label)
    }

    @Test fun aCardWithoutSharesGivesItsFirstValueAndOneWithoutNumbersNothing() {
        assertEquals("3 up", card("a", Metric(label = "Players", value = "3 up")).headline()?.value)
        assertNull(card("b").headline())
        assertNull(card("c", Metric(label = "x", value = "—")).headline())
    }

    private fun page(vararg groups: Group) = Page(id = "general", groups = groups.toList())
    private fun byId(vararg c: Card) = c.associateBy { it.id }

    @Test fun groupsGetTheirTitlesAndTheCardsInTroubleComeFirstAfterTheBigOnes() {
        val host = card("host", pct("CPU", 5.0), type = "proxmox")
        val ok = card("ok", pct("CPU", 5.0)); val down = card("down", bad = true)
        val slots = buildSlots(page(Group("g", "Proxmox", listOf("ok", "down", "host"))), false, byId(host, ok, down), emptySet(), 3)
        assertEquals(listOf("t-g", "c-g-host", "c-g-down", "c-g-ok"), slots.map { it.key })
        assertEquals(true, slots[0].bad)
    }

    @Test fun needsAttentionIsOnlyOnTheFirstPageAndOnlyWhenSomethingIsRed() {
        val down = card("down", bad = true); val ok = card("ok", pct("CPU", 5.0))
        val p = page(Group("g", "Lab", listOf("ok", "down")))
        assertEquals(listOf("t-attention", "c-attn-down"), buildSlots(p, true, byId(ok, down), emptySet(), 3).map { it.key }.take(2))
        assertEquals(listOf("t-g", "c-g-down", "c-g-ok"), buildSlots(p, false, byId(ok, down), emptySet(), 3).map { it.key })
        assertEquals(listOf("t-g", "c-g-ok"), buildSlots(page(Group("g", "Lab", listOf("ok"))), true, byId(ok), emptySet(), 3).map { it.key })
    }

    @Test fun theAttentionRowIsOneLineWithAPlusN() {
        val cards = (1..5).map { card("d$it", bad = true) }
        val slots = buildSlots(page(Group("g", "", cards.map { it.id })), true, byId(*cards.toTypedArray()), emptySet(), 3)
        val row = slots.takeWhile { it.key.startsWith("c-attn") || it.key == "t-attention" || it.key == "attn-more" }
        assertEquals(listOf("t-attention", "c-attn-d1", "c-attn-d2", "attn-more"), row.map { it.key })
        assertEquals(3, row.last().more)
        assertEquals(1, row.last().span(3))
    }

    @Test fun aTitleIsAFullLineAndASmallTileOneCell() {
        val t = Slot("t", title = "Lab")
        assertEquals(4, t.span(4)); assertEquals(TITLE_H, t.height(112f), 0f)
        val big = Slot("c", card("h", pct("CPU", 1.0), type = "proxmox"), mini = true)
        assertEquals(1, big.span(3)); assertEquals(112f, big.height(112f), 0f)   // forced small
    }

    @Test fun aTitleNeverEndsAScreenWithItsCardsOnTheNextOne() {
        val items = listOf(Slot("t1", title = "A"), Slot("a", card("a")), Slot("t2", title = "B"), Slot("b", card("b")))
        val screens = keepTitlesWithCards(listOf(items.subList(0, 3), items.subList(3, 4)))
        assertEquals(listOf(listOf("t1", "a"), listOf("t2", "b")), screens.map { s -> s.map { it.key } })
        // packed by height: titles count in the budget
        val packed = packGrid(items, { it.span(3) }, { it.height(112f) }, 3, 12f, 26f + 12f + 112f)
        assertEquals(2, keepTitlesWithCards(packed).size)
    }
}
