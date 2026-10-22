package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Metric
import io.github.mithrandler.labhud.ui.ringsAndChips
import io.github.mithrandler.labhud.ui.worseSev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A temperature goes into the ring of its own part (CPU, GPU); everything else stays a chip. */
class RingsTest {
    private fun pct(key: String, label: String) = Metric(key = key, label = label, value = "10%", pct = 10.0)
    private fun temp(key: String, label: String) = Metric(key = key, label = label, value = "53°", format = "celsius")

    @Test fun tempsJoinTheirRing() {
        val cpu = pct("p.cpu", "CPU"); val ram = pct("p.mem", "RAM"); val gpu = pct("p.gpu", "GPU")
        val tc = temp("p.cpu_temp", "CPU"); val tg = temp("p.gpu_temp", "GPU"); val vm = Metric(key = "p.vms", label = "VM", value = "4")
        val (rings, chips) = ringsAndChips(listOf(cpu, ram, gpu, tc, tg, vm))
        assertEquals(listOf(cpu to tc, ram to null, gpu to tg), rings)
        assertEquals(listOf(vm), chips)
    }

    @Test fun lonelyTempStaysAChip() {
        val t = temp("p.cpu_temp", "CPU")
        val (rings, chips) = ringsAndChips(listOf(pct("p.mem", "RAM"), t))
        assertNull(rings.single().second)
        assertEquals(listOf(t), chips)
    }

    @Test fun worse() {
        assertEquals("crit", worseSev("warn", "crit"))
        assertEquals("warn", worseSev("", "warn"))
        assertEquals("", worseSev("", ""))
    }
}
