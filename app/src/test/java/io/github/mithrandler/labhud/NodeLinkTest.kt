package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Guest
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.ui.dayShares
import io.github.mithrandler.labhud.ui.packGrid
import io.github.mithrandler.labhud.ui.paginate
import io.github.mithrandler.labhud.ui.pageCount
import io.github.mithrandler.labhud.ui.hostsWithGuests
import io.github.mithrandler.labhud.ui.meters
import org.junit.Assert.assertEquals
import org.junit.Test

/** A VM sits under the host it runs on; the guest's bars and the node the server sends. */
class NodeLinkTest {
    private fun host(id: String, node: String) = Card(id = id, name = id, type = "proxmox", node = node)
    private fun vm(id: String, node: String, bad: Boolean? = null) = Card(id = id, name = id, type = "vm", kind = "guest", node = node, bad = bad)

    @Test fun guestsFollowTheirHost() {
        val cards = listOf(vm("b", "n2"), host("H2", "n2"), vm("a", "n1"), host("H1", "n1"), vm("c", "n1", bad = true), vm("lost", "gone"))
        assertEquals(listOf("H1", "c", "a", "H2", "b", "lost"), hostsWithGuests(cards).map { it.id })
    }

    @Test fun aHostWithoutNodeTakesNoGuests() {
        assertEquals(listOf("H", "g"), hostsWithGuests(listOf(vm("g", ""), host("H", ""))).map { it.id })
    }

    @Test fun decodesNodeAndCounts() {
        val c = LabJson.decodeFromString<Card>("""{"id":"h","node":"pve","guest_counts":{"vm":[3,4],"lxc":[3,3]}}""")
        assertEquals("pve", c.node)
        assertEquals(listOf(3, 4), c.guestCounts["vm"])
    }

    @Test fun guestBars() {
        val m = Guest(cpu = 12.4, mem = 85.0, disk = null).meters()
        assertEquals(listOf("CPU", "RAM"), m.map { it.label })
        assertEquals(listOf("12%", "85%"), m.map { it.value })
        assertEquals("warn", m[1].sev)
    }
}

/** The GPUs and a mini-card's number of containers as the server sends them. */
class GpuStateTest {
    @Test fun gpusWithAndWithoutVideoMemory() {
        val c = LabJson.decodeFromString<Card>("""{"id":"h","gpus":[{"util":20.5,"mem":30,"temp":46},{"util":1}]}""")
        assertEquals(2, c.gpus.size)
        assertEquals(30.0, c.gpus[0].mem!!, 0.0)
        assertEquals(null, c.gpus[1].mem)
        assertEquals(null, c.gpus[1].temp)
    }

    @Test fun guestUptimeAndIds() {
        val g = LabJson.decodeFromString<Guest>("""{"cpu":1.5,"mem":40,"uptime":90061,"node":"pve","vmid":"100"}""")
        assertEquals("pve", g.node)
        assertEquals("100", g.vmid)
        assertEquals(90061L, g.uptime)
        assertEquals(listOf("CPU", "RAM"), g.meters().map { it.label })
    }
}

/** The week of uptime as a share a day. */
class DaySharesTest {
    @Test fun meanOfADayAndTheUnknownOnes() {
        // 14 slices = 2 a day: day 1 all up, day 2 half down, day 3 off, day 4 unknown, then three up days
        val v = listOf(1.0, 1.0, 1.0, 0.0, -1.0, -1.0, null, null, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        val d = dayShares(v)
        assertEquals(7, d.size)
        assertEquals(1.0, d[0]!!, 0.0)
        assertEquals(0.5, d[1]!!, 0.0)
        assertEquals(-1.0, d[2]!!, 0.0)
        assertEquals(null, d[3])
        assertEquals(1.0, d[6]!!, 0.0)
    }

    @Test fun aYoungHistoryLeavesTheOldDaysUnknown() {
        assertEquals(List(7) { null }, dayShares(emptyList()))
    }
}

/** Pages of a grid: by count, and by the height the lines really take. */
class PagingTest {
    @Test fun pagesByCount() {
        assertEquals(1, pageCount(0, 4))
        assertEquals(2, pageCount(5, 4))
        assertEquals(listOf(listOf(1, 2), listOf(3)), paginate(listOf(1, 2, 3), 2))
        assertEquals(listOf(emptyList<Int>()), paginate(emptyList<Int>(), 2))
    }

    @Test fun aHalfEmptyLineStillCostsAWholeLine() {
        // 6 cells a line: a big card (6) of 240 dp, then 4 small ones (2 cells, 160 dp): a line of 3 and a line of 1
        val items = listOf(6 to 240f, 2 to 160f, 2 to 160f, 2 to 160f, 2 to 160f)
        val screens = packGrid(items, { it.first }, { it.second }, 6, 10f, 560f)
        // 240 + 10 + 160 = 410 fits; the 4th small one opens a line at 410 + 10 + 160 = 580 > 560: a new screen
        assertEquals(listOf(4, 1), screens.map { it.size })
    }

    @Test fun anItemTallerThanTheBudgetHasAScreenToItself() {
        val screens = packGrid(listOf(6 to 900f, 6 to 100f), { it.first }, { it.second }, 6, 10f, 500f)
        assertEquals(listOf(1, 1), screens.map { it.size })
    }

    @Test fun nothingIsOneEmptyScreen() {
        assertEquals(1, packGrid(emptyList<Pair<Int, Float>>(), { it.first }, { it.second }, 6, 10f, 500f).size)
    }
}
