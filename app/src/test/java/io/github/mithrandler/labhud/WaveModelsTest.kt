package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.DiscoverList
import io.github.mithrandler.labhud.data.HostInventory
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.StreamChunk
import io.github.mithrandler.labhud.data.splitShow
import io.github.mithrandler.labhud.ui.frameAllowed
import io.github.mithrandler.labhud.ui.monthWeeks
import io.github.mithrandler.labhud.ui.zoneOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The answers of the server's T, U, V and R routes, as agents.py, server.py and api/discover.py write them. */
class WaveModelsTest {

    @Test
    fun `an inventory as the server sends it`() {
        val inv = LabJson.decodeFromString<HostInventory>("""
            {"ok": true, "images": [{"id": "0123456789ab", "tags": ["nginx:1.27"], "size": 100, "created": 1700000000, "used": 0, "dangling": false}],
             "volumes": [{"name": "${"a".repeat(64)}", "driver": "local", "size": -1, "used": 0, "anonymous": true}],
             "networks": [{"name": "bridge", "driver": "bridge", "scope": "local", "used": 3, "builtin": true}],
             "more": {"images": 4}, "totals": {"images": 100, "unused_images": 100, "volumes": 0}, "at": 1700000000}
        """)
        assertEquals("nginx:1.27", inv.images.single().tags.single())
        assertTrue(inv.volumes.single().anonymous)
        assertEquals(-1L, inv.volumes.single().size)
        assertTrue(inv.networks.single().builtin)
        assertEquals(4, inv.more["images"])
        assertEquals(100L, inv.totals.unusedImages)
    }

    @Test
    fun `a stream chunk, open and at its end`() {
        val open = LabJson.decodeFromString<StreamChunk>("""{"ok": true, "data": "aGk=", "next": 2, "end": null}""")
        assertEquals(2, open.next)
        assertNull(open.end)
        assertEquals("exited", LabJson.decodeFromString<StreamChunk>("""{"ok": true, "data": "", "next": 2, "end": "exited"}""").end)
    }

    @Test
    fun `the discovered list`() {
        val l = LabJson.decodeFromString<DiscoverList>("""
            {"ok": true, "items": [{"key": "docker:n100/jellyfin", "kind": "docker", "name": "jellyfin", "detail": "n100 · jellyfin/jellyfin"}], "dismissed": 2}
        """)
        assertEquals("docker:n100/jellyfin", l.items.single().key)
        assertEquals(2, l.dismissed)
    }

    @Test
    fun `a stack's show splits into the file and the env names`() {
        assertEquals("services: {}\n" to listOf("A", "B"), splitShow("services: {}\n\n# .env: A, B"))
        assertEquals("services: {}" to emptyList<String>(), splitShow("services: {}"))
    }

    @Test
    fun `a month in weeks, Monday first`() {
        // 1 October 2026 is a Thursday: three blanks, and 31 days end on a Saturday
        val weeks = monthWeeks(LocalDate.of(2026, 10, 1))
        assertEquals(5, weeks.size)
        assertEquals(listOf(null, null, null, 1, 2, 3, 4), weeks.first())
        assertEquals(listOf(26, 27, 28, 29, 30, 31, null), weeks.last())
        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun `the frame takes only web addresses`() {
        assertTrue(frameAllowed("https://grafana.lan/d/x"))
        assertTrue(frameAllowed("http://192.168.1.5:3000"))
        assertFalse(frameAllowed("file:///sdcard/x.html"))
        assertFalse(frameAllowed("javascript:alert(1)"))
        assertFalse(frameAllowed("https://"))
        assertFalse(frameAllowed("not a url"))
    }

    @Test
    fun `an unknown zone is the phone's`() {
        assertEquals(ZoneId.of("Europe/Bucharest"), zoneOf("Europe/Bucharest"))
        assertEquals(ZoneId.systemDefault(), zoneOf(""))
        assertEquals(ZoneId.systemDefault(), zoneOf("Mars/Olympus"))
    }
}
