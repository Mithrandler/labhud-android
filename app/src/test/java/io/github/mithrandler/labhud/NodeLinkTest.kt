package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Guest
import io.github.mithrandler.labhud.data.LabJson
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
