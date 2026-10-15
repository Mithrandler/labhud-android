package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Events
import io.github.mithrandler.labhud.data.History
import io.github.mithrandler.labhud.data.Info
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LabState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The answers of a real labhud in demo mode (saved in src/test/resources) decode as expected. */
class ModelsTest {
    private fun read(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun state() {
        val st = LabJson.decodeFromString<LabState>(read("state.json"))
        assertTrue(st.pages.isNotEmpty())
        assertTrue(st.cards.size > 10)
        assertEquals(st.problems, st.cards.filter { it.bad == true }.map { it.id })
        val pve = st.byId.getValue("host-pve")
        assertEquals("up", pve.status)
        assertTrue(pve.metrics.any { it.trend })
        val events = st.byId.getValue("events")
        assertEquals("list", events.kind)
        assertEquals(null, events.status)
        assertTrue(events.rows.any { it.bad })
        assertTrue(st.cards.any { it.kind == "guest" })
        // every id a group names is a card
        st.pages.flatMap { p -> p.groups.flatMap { it.cards } }.forEach { assertTrue(it, it in st.byId) }
    }

    @Test fun card() {
        val c = LabJson.decodeFromString<Card>(read("card-host-pve.json"))
        assertEquals("host", c.sections.first().title)
        assertTrue(c.sections.all { s -> s.rows.all { it.name.isNotEmpty() } })
    }

    @Test fun others() {
        val info = LabJson.decodeFromString<Info>(read("info.json"))
        assertEquals(1, info.api)
        assertTrue(info.maintenanceButtons)
        assertTrue(LabJson.decodeFromString<Events>(read("events.json")).events.isNotEmpty())
        val h = LabJson.decodeFromString<History>(read("history.json"))
        assertTrue(h.step > 0 && h.series.isNotEmpty())
    }

    @Test fun unknownFieldsAndNulls() {
        val st = LabJson.decodeFromString<LabState>("""{"cards":[{"id":"x","status":null,"bad":null,"new":1,"rows":[["a",null],["b","1",true,true]]}],"later":{}}""")
        val c = st.cards.single()
        assertEquals(null, c.bad)
        assertEquals("", c.rows[0].value)
        assertTrue(c.rows[1].bad && c.rows[1].quiet)
        assertFalse(c.rows[0].bad)
    }
}
