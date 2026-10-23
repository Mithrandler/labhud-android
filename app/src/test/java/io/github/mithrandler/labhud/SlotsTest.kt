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

    private fun page(id: String, vararg groups: Group) = Page(id = id, groups = groups.toList())
    private fun byId(vararg c: Card) = c.associateBy { it.id }
    private fun host(id: String, node: String) = Card(id = id, name = id, type = "proxmox", node = node, metrics = listOf(pct("CPU", 5.0)))
    private fun guest(id: String, node: String, bad: Boolean? = null) = Card(id = id, name = id, kind = "guest", node = node, bad = bad)

    @Test fun onGeneralAHostIsWideAndItsGuestsAreInsideIt() {
        val h = host("pve", "pve"); val g1 = guest("vm1", "pve"); val other = card("nas", pct("CPU", 5.0))
        val slots = buildSlots(page("general", Group("a", "PVE", listOf("pve", "vm1")), Group("b", "Storage", listOf("nas"))), false, byId(h, g1, other), emptySet())
        assertEquals(listOf("c-a-pve", "c-b-nas"), slots.map { it.key })
        assertEquals(true, slots[0].wide); assertEquals(2, slots[0].span(4)); assertEquals(1, slots[1].span(4))
    }

    @Test fun aGuestInTroubleComesToTheFrontAndIsNotShownTwice() {
        val h = host("pve", "pve"); val g1 = guest("vm1", "pve", bad = true)
        val slots = buildSlots(page("general", Group("a", "PVE", listOf("pve", "vm1"))), false, byId(h, g1), emptySet())
        assertEquals(listOf("t-attention", "c-attn-vm1", "c-a-pve"), slots.map { it.key })
    }

    @Test fun aGuestWithoutItsHostInThePageStaysATile() {
        val g1 = guest("vm1", "pve")
        assertEquals(listOf("c-a-vm1"), buildSlots(page("general", Group("a", "VMs", listOf("vm1"))), false, byId(g1), emptySet()).map { it.key })
    }

    @Test fun otherPagesFoldAGroupOfSeveralCardsIntoOneTile() {
        val a = card("a", pct("CPU", 5.0)); val b = card("b", bad = true); val solo = card("solo", pct("CPU", 5.0))
        val slots = buildSlots(page("security", Group("g", "Threats", listOf("a", "b")), Group("s", "Solo", listOf("solo"))), false, byId(a, b, solo), emptySet())
        assertEquals(listOf("t-attention", "c-attn-b", "g-g", "c-s-solo"), slots.map { it.key })
        assertEquals(1, slots[2].group?.problems); assertEquals(listOf("a", "b"), slots[2].group?.cards?.map { it.id })
    }

    @Test fun aQuietPageHasNoAttentionRowAndFavouritesComeOnTheFirstPageOnly() {
        val ok = card("ok", pct("CPU", 5.0))
        val p = page("general", Group("g", "Lab", listOf("ok")))
        assertEquals(listOf("c-g-ok"), buildSlots(p, false, byId(ok), setOf("ok")).map { it.key })
        assertEquals(listOf("t-favorites", "c-fav-ok", "c-g-ok"), buildSlots(p, true, byId(ok), setOf("ok")).map { it.key })
    }

    @Test fun aTitleIsAFullLineAndEveryTileTheSameHeight() {
        val t = Slot("t", title = "Lab")
        assertEquals(4, t.span(4)); assertEquals(TITLE_H, t.height(112f), 0f)
        assertEquals(132f, Slot("c", card("h"), mini = true).height(132f), 0f)
        assertEquals(132f, Slot("h", host("h", "h"), wide = true).height(132f), 0f)
    }
}
