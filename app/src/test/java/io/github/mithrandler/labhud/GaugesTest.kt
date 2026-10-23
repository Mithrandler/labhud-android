package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Gpu
import io.github.mithrandler.labhud.data.Metric
import io.github.mithrandler.labhud.ui.gaugeCount
import io.github.mithrandler.labhud.ui.gaugeLayout
import io.github.mithrandler.labhud.ui.gaugesOf
import io.github.mithrandler.labhud.ui.tileHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rings of a machine: CPU and RAM in one ring, a GPU's load and video memory in one, and how they are laid out. */
class GaugesTest {
    private fun pct(key: String, label: String, v: Double, sev: String = "") = Metric(key = key, label = label, value = "${v.toInt()}%", pct = v, sev = sev)
    private fun temp(key: String, label: String, v: String, sev: String = "") = Metric(key = key, label = label, value = v, format = "celsius", sev = sev)
    private val cpu = pct("p.cpu", "CPU", 23.0)
    private val ram = pct("p.mem", "RAM", 60.0, "warn")
    private val disk = pct("p.disk", "Disk", 62.0)
    private fun host(vararg gpus: Gpu, metrics: List<Metric> = listOf(cpu, ram, disk, temp("p.cpu_temp", "CPU", "52°"))) =
        Card(id = "h", type = "proxmox", metrics = metrics, gpus = gpus.toList())

    @Test fun cpuAndRamShareARingAndTheTemperatureGoesWithTheCpu() {
        val g = gaugesOf(host(), host().metrics)
        assertEquals(listOf("CPU · RAM", "Disk"), g.map { it.label })
        assertEquals(23.0, g[0].a.pct, 0.0)
        assertEquals(60.0, g[0].b!!.pct, 0.0)
        assertEquals("warn", g[0].b!!.sev)
        assertEquals("52°", g[0].temp)
        assertNull(g[1].b)
    }

    @Test fun twoGpusAreTwoRingsOfLoadAndVideoMemory() {
        val c = host(Gpu(util = 45.0, mem = 30.0, temp = 51.0), Gpu(util = 90.0, mem = 70.0, temp = 68.0, utilSev = "warn", tempSev = "warn"))
        val g = gaugesOf(c, c.metrics)
        assertEquals(listOf("CPU · RAM", "Disk", "GPU1 · VRAM", "GPU2 · VRAM"), g.map { it.label })
        assertEquals("warn", g[3].a.sev)
        assertEquals("", g[2].a.sev)          // 45%: below the default marks
        assertEquals("68°", g[3].temp)
        assertEquals("warn", g[3].tempSev)
    }

    @Test fun aSingleGpuHasNoNumberAndOneWithoutVideoMemoryIsOneShare() {
        assertEquals("GPU · VRAM", gaugesOf(host(Gpu(util = 5.0, mem = 5.0)), host().metrics).last().label)
        val g = gaugesOf(host(Gpu(util = 5.0)), host().metrics).last()
        assertEquals("GPU", g.label)
        assertNull(g.b)
    }

    @Test fun withoutRamTheCpuStaysAlone() {
        val c = host(metrics = listOf(cpu, disk))
        assertEquals(listOf("CPU", "Disk"), gaugesOf(c, c.metrics).map { it.label })
    }

    @Test fun descriptionIsSpoken() {
        val g = gaugesOf(host(), host().metrics)[0]
        assertEquals("CPU · RAM: 23 percent, 60 percent, 52 degrees", g.description)
    }

    @Test fun fourGaugesAreTwoByTwoWhenThereIsHeightAndOneRowWhenThereIsNot() {
        assertEquals(2, gaugeLayout(4, 330f, 260f).cols)
        assertEquals(2, gaugeLayout(4, 330f, Float.MAX_VALUE).cols)
        val wide = gaugeLayout(4, 330f, 120f)
        assertEquals(4, wide.cols)
        assertTrue(wide.size in 60f..80f)
    }

    @Test fun theRingIsNeverBiggerThanTheTokenOrTheCell() {
        assertEquals(96f, gaugeLayout(2, 800f, 400f).size, 0f)
        assertEquals(60f, gaugeLayout(1, 60f, 400f).size, 0f)
        assertEquals(5, gaugeLayout(5, 800f, 120f).cols.coerceAtLeast(5))
    }

    @Test fun aMachineWithFourGaugesIsTallEnoughForThemTwoByTwoInPortraitAndOneRowWhenLow() {
        val c = host(Gpu(util = 1.0, mem = 1.0), Gpu(util = 1.0, mem = 1.0))
        assertEquals(4, c.gaugeCount())
        assertEquals(326f, c.tileHeight(112f), 0f)
        assertEquals(208f, c.tileHeight(84f), 0f)          // landscape: two rows are 180, the rings need 208
        val plain = Card(id = "n", type = "nas", metrics = listOf(disk))
        assertEquals(112f * 2 + 12f, plain.tileHeight(112f), 0f)
    }
}
