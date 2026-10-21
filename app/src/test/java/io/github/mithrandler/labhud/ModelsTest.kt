package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Events
import io.github.mithrandler.labhud.data.GameServer
import io.github.mithrandler.labhud.data.Notes
import io.github.mithrandler.labhud.ui.duration
import io.github.mithrandler.labhud.data.History
import io.github.mithrandler.labhud.data.Info
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.data.Sparks
import io.github.mithrandler.labhud.data.Uptime
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

    /** labhud 0.3: what machine each card is, the games page's servers, the agent's charts. */
    @Test fun state03() {
        val st = LabJson.decodeFromString<LabState>(read("state.json"))
        assertEquals("proxmox", st.byId.getValue("host-pve").type)
        assertTrue(st.cards.any { it.type == "docker" })
        assertTrue(st.cards.any { it.type.isEmpty() })
        val games = st.pages.first { it.games }
        assertTrue(games.servers.isNotEmpty())
        assertTrue(st.byId.getValue("vm-pve-100").charts.isNotEmpty())
    }

    @Test fun containers() {
        val c = LabJson.decodeFromString<Card>(read("card-vm-pve-100.json"))
        assertEquals("docker", c.agent)
        val jf = c.containers.first { it.name == "jellyfin" }
        assertTrue(jf.update)
        assertTrue(jf.actions.any { it.name.endsWith("-update") })
        assertTrue(c.containers.first { it.name == "portainer" }.actions.isEmpty())
    }

    @Test fun game() {
        val g = LabJson.decodeFromString<GameServer>(read("game-a1f3.json"))
        assertEquals(listOf("alex", "sam", "kim"), g.who)
        assertEquals(20, g.max)
        assertEquals(null, g.map)
    }

    @Test fun rights() {
        val i = LabJson.decodeFromString<Info>("""{"api":1,"device_admin":true,"pin":true,"unlocked":false}""")
        assertTrue(i.deviceAdmin && i.pin)
        assertFalse(i.unlocked)
        val n = LabJson.decodeFromString<Notes>("""{"via":"app","quiet":[23,7],"notifications":[{"id":5,"time":9,"title":"t","text":"x","bad":true,"card":null}]}""")
        assertEquals("app", n.via)
        assertEquals(5L, n.notifications.single().id)
    }

    @Test fun durations() {
        assertEquals("1 h 2 min", duration(3725))
        assertEquals("2 d 7 h", duration(200000))
        assertEquals("0 min", duration(5))
    }

    /** /api/agents/list as ⚙ and the app's agent screen read it (labels may be null). */
    @Test fun agents() {
        val a = LabJson.decodeFromString<io.github.mithrandler.labhud.data.Agents>(read("agents.json"))
        val n = a.agents.single()
        assertEquals("192.0.2.5", n.address)
        assertEquals(listOf("100", "101"), n.targets["pve"])
        assertEquals("windows", n.labels["pve"]?.get("102"))
        assertEquals(null, n.labels["pve"]?.get("101"))
        assertTrue("pve" in a.verbs && "sensors" !in a.verbs)
    }

    /** A card's icon and size, a percentage's bar and its marks. */
    @Test fun cardLook() {
        val st = LabJson.decodeFromString<LabState>(read("state.json"))
        val pve = st.byId.getValue("host-pve")
        assertEquals("proxmox", pve.icon)
        assertEquals("l", pve.size)
        val cpu = pve.metrics.first { it.label == "CPU" }
        assertTrue(cpu.pct != null && cpu.pct!! in 0.0..100.0)
        assertEquals(80.0, cpu.warn)
        assertEquals(92.0, cpu.crit)
        // a number that is not a percentage has no bar
        assertEquals(null, pve.metrics.first { it.key == "proxmox.pve.vms" }.pct)
        // a card with no icon or size from the server: empty strings
        assertEquals("", st.byId.getValue("events").icon)
        assertEquals("", st.byId.getValue("vm-pve-101").size)
        // an older server: no such fields at all
        val old = LabJson.decodeFromString<Card>("""{"id":"x","metrics":[{"key":"k","value":"5%"}]}""")
        assertEquals("", old.icon)
        assertEquals(null, old.metrics.single().pct)
    }

    /** GET /api/v1/sparks: 30 points a series, every key one with a trend. */
    @Test fun sparks() {
        val s = LabJson.decodeFromString<Sparks>(read("sparks.json"))
        assertEquals(60, s.step)
        assertTrue(s.series.isNotEmpty())
        assertTrue(s.series.values.all { it.size == 30 })
        assertTrue("proxmox.pve.cpu" in s.series)
    }

    /** GET /api/v1/uptime with coverage, and without it from an older server. */
    @Test fun uptimeCoverage() {
        val u = LabJson.decodeFromString<Uptime>(read("uptime.json"))
        assertEquals(900, u.step)
        assertEquals(u.percent.keys, u.coverage.keys)
        assertTrue(u.cards.getValue("host-pve").size == 96)
        val gap = LabJson.decodeFromString<Uptime>("""{"step":900,"cards":{"a":[1.0,null,0.5,-1]},"percent":{"a":75.0},"coverage":{"a":87.5}}""")
        assertEquals(87.5, gap.coverage.getValue("a"), 0.0)
        assertEquals(null, gap.cards.getValue("a")[1])
        val old = LabJson.decodeFromString<Uptime>("""{"step":900,"cards":{},"percent":{}}""")
        assertTrue(old.coverage.isEmpty())
    }

    /** The derived lookups are worked out once per decoded document, and equal documents are equal (Compose skips on that). */
    @Test fun derivedFieldsAreCached() {
        val json = """{"cards":[{"id":"a","rows":[["n","v",true]]},{"id":"b"}]}"""
        val st = LabJson.decodeFromString<LabState>(json)
        assertTrue(st.byId === st.byId)
        assertTrue(st.byId.getValue("a").rows === st.byId.getValue("a").rows)
        assertEquals(true, st.byId.getValue("a").rows.single().bad)
        assertEquals(st, LabJson.decodeFromString<LabState>(json))
    }

    /** One client per pinned certificate: its connection pool is shared by every call to that server. */
    @Test fun oneClientPerFingerprint() {
        assertTrue(LabApi.clientFor("") === LabApi.clientFor(""))
        val fp = "AB:" + "cd".repeat(31)
        assertTrue(LabApi.clientFor(fp) === LabApi.clientFor(fp.lowercase().replace(":", "")))
        assertTrue(LabApi.clientFor(fp) !== LabApi.clientFor(""))
    }
}
