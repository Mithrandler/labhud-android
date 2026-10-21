package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.Backups
import io.github.mithrandler.labhud.data.Channel
import io.github.mithrandler.labhud.data.Channels
import io.github.mithrandler.labhud.data.Container
import io.github.mithrandler.labhud.data.FleetUpdate
import io.github.mithrandler.labhud.data.Scan
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.Registries
import io.github.mithrandler.labhud.data.Registry
import io.github.mithrandler.labhud.data.ScanRow
import io.github.mithrandler.labhud.data.channelBody
import io.github.mithrandler.labhud.ui.settings.backupSize
import io.github.mithrandler.labhud.ui.settings.channelLine
import io.github.mithrandler.labhud.ui.settings.channelValues
import io.github.mithrandler.labhud.ui.settings.cveCount
import io.github.mithrandler.labhud.ui.settings.registryLine
import io.github.mithrandler.labhud.ui.settings.scannersLine
import io.github.mithrandler.labhud.ui.settings.secretSaved
import io.github.mithrandler.labhud.ui.settings.toggled
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupApiTest {
    private fun read(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun registries() {
        val r = LabJson.decodeFromString<Registries>(read("registries.json"))
        assertTrue(r.ok)
        assertFalse(r.managed)
        assertFalse(r.holdCritical)
        assertEquals(listOf("ghcr.io", "registry.example.org:5000"), r.registries.map { it.host })
        assertEquals("mithrandler", r.registries[0].user)
        assertEquals(1, r.registries[0].containers)
        assertFalse(r.registries[0].token)
        assertEquals(3, r.scans.size)
        assertEquals(listOf("jellyfin", "sonarr", "radarr"), r.scans.map { it.name })
        assertEquals(2, r.scans[0].critical)
        assertEquals(7, r.scans[0].high)
        assertEquals("trivy", r.scans[0].tool)
    }

    @Test fun registryLine() {
        assertEquals("user mithrandler · no token · 1 container", registryLine(Registry(host = "ghcr.io", user = "mithrandler", containers = 1)))
        assertEquals("user ci · token saved · not used yet", registryLine(Registry(user = "ci", token = true)))
        assertEquals("user u · no token · 3 containers", registryLine(Registry(user = "u", containers = 3)))
    }

    @Test fun cveCount() {
        assertEquals("2 critical · 7 high", cveCount(ScanRow(critical = 2, high = 7)))
        assertEquals("3 high", cveCount(ScanRow(high = 3)))
        assertEquals("clean", cveCount(ScanRow()))
        assertEquals("1 critical", cveCount(ScanRow(critical = 1)))
    }

    @Test fun scanners() {
        assertTrue(scannersLine(Registries()).startsWith("No agent can scan"))
        assertEquals("Can scan: bigdocker, nas (not ticked).", scannersLine(LabJson.decodeFromString<Registries>("""{"ok":true,"scanners":[{"agent":"bigdocker","on":true,"online":true},{"agent":"nas","on":false,"online":true}]}""")))
    }

    @Test fun channels() {
        val c = LabJson.decodeFromString<Channels>(read("notify-channels.json"))
        assertTrue(c.ok)
        assertEquals(listOf("app", "ntfy", "email", "webhook", "discord", "gotify"), c.order)
        assertEquals(8, c.events.size)
        assertEquals(listOf("host_down", "A host down"), c.events[1])
        assertFalse(c.explicit)
        assertEquals(listOf("admin_login", "update_available", "update_done"), c.always)
        assertEquals(listOf("app", "ntfy", "webhook", "discord"), c.matrix["host_down"])
        assertEquals(emptyList<String>(), c.matrix["admin_login"])
        val d = c.channels["discord"]!!
        assertTrue(d.set)
        assertEquals("discord.com", d.host)
        assertEquals(4, d.sent)
        assertEquals("HTTP 429", d.lastError)
        assertTrue(c.channels["webhook"]!!.auth)
        assertFalse(c.channels["email"]!!.set)
        assertNull(c.channels["email"]!!.port)
    }

    @Test fun channelLine() {
        assertEquals("set · discord.com · 4 sent · last error: HTTP 429", channelLine("discord", Channel(set = true, host = "discord.com", sent = 4, lastError = "HTTP 429")))
        assertEquals("not set", channelLine("email", Channel()))
        assertEquals("no from address", channelLine("email", Channel(problem = "no from address")))
        assertEquals("set · https://gotify.example.org · 0 sent", channelLine("gotify", Channel(set = true, url = "https://gotify.example.org", host = "ignored", sent = 0)))
    }

    @Test fun toggled() {
        val m = mapOf("alert" to listOf("app", "ntfy"), "host_down" to listOf("app"))
        assertEquals(listOf("app"), toggled(m, "alert", "ntfy")["alert"])
        assertEquals(listOf("app", "email"), toggled(m, "host_down", "email")["host_down"])
        assertEquals(listOf("discord"), toggled(m, "update_done", "discord")["update_done"])
        assertEquals(listOf("app", "ntfy"), toggled(m, "host_down", "email")["alert"])
    }

    @Test fun channelValues() {
        val v = channelValues("email", Channel(host = "smtp.example.org", port = 587, security = "starttls", user = "me", from = "lab@example.org", to = listOf("a@example.org", "b@example.org")))
        assertEquals("smtp.example.org", v["host"])
        assertEquals("587", v["port"])
        assertEquals("a@example.org, b@example.org", v["to"])
        assertEquals("starttls", channelValues("email", Channel())["security"])
        assertEquals("", channelValues("email", Channel())["port"])
        assertEquals(mapOf("url" to ""), channelValues("gotify", Channel()))
        assertTrue(channelValues("discord", Channel()).isEmpty())
    }

    @Test fun secretSaved() {
        assertTrue(secretSaved("email", "password", Channel(password = true)))
        assertFalse(secretSaved("email", "password", Channel()))
        assertTrue(secretSaved("discord", "url", Channel(set = true)))
        assertTrue(secretSaved("webhook", "auth", Channel(auth = true)))
        assertTrue(secretSaved("gotify", "token", Channel(token = true)))
        assertFalse(secretSaved("gotify", "url", Channel(set = true)))
    }

    @Test fun channelBody() {
        val o = Json.parseToJsonElement(channelBody("email", mapOf("host" to "smtp.example.org", "port" to "465", "to" to "a@example.org"), mapOf("password" to "pw"))).jsonObject
        assertEquals("email", o["channel"]!!.jsonPrimitive.content)
        val cfg = o["config"]!!.jsonObject
        assertEquals(465, cfg["port"]!!.jsonPrimitive.int)
        assertTrue(cfg["port"]!!.jsonPrimitive.isString.not())
        assertEquals("smtp.example.org", cfg["host"]!!.jsonPrimitive.content)
        assertEquals("pw", o["secrets"]!!.jsonObject["password"]!!.jsonPrimitive.content)
        val p = Json.parseToJsonElement(channelBody("email", mapOf("port" to ""), emptyMap())).jsonObject
        assertNull(p["config"]!!.jsonObject["port"])
        assertNull(p["secrets"])
        val q = Json.parseToJsonElement(channelBody("discord", emptyMap(), mapOf("url" to "https://discord.com/api/webhooks/1/x"))).jsonObject
        assertNull(q["config"])
    }

    @Test fun backups() {
        val b = LabJson.decodeFromString<Backups>(read("backups.json"))
        assertTrue(b.ok)
        assertNull(b.problem)
        assertFalse(b.encryption)
        assertFalse(b.passwordSet)
        assertTrue(b.settings.daily)
        assertEquals(7, b.settings.keep)
        assertEquals(3, b.settings.at)
        assertEquals(1, b.backups.size)
        assertEquals("manual", b.backups[0].kind)
        assertFalse(b.backups[0].encrypted)
        assertTrue(b.backups[0].name.endsWith(".tar.gz"))
        assertTrue(b.last!!.ok)
        assertEquals("manual", b.last!!.kind)
    }

    @Test fun backupSize() {
        assertEquals("425 B", backupSize(425L))
        assertEquals(String.format("%.1f KiB", 1.5), backupSize(1536L))
        assertEquals(String.format("%.1f MiB", 3.0), backupSize(3L * 1024 * 1024))
    }

    @Test fun defaults() {
        val r = LabJson.decodeFromString<Registries>("{}")
        assertFalse(r.ok)
        assertTrue(r.registries.isEmpty())
        val b = LabJson.decodeFromString<Backups>("""{"ok":false,"error":"forbidden"}""")
        assertEquals("forbidden", b.error)
        val c = LabJson.decodeFromString<Channels>("{}")
        assertTrue(c.channels.isEmpty())
    }

    @Test fun scanOnRows() {
        val c = LabJson.decodeFromString<Container>("""{"name":"jellyfin","up":true,"scan":{"critical":2,"high":7,"t":1792580445}}""")
        assertEquals(Scan(2, 7, 1792580445), c.scan)
        assertNull(LabJson.decodeFromString<Container>("""{"name":"x","scan":null}""").scan)
        assertEquals(3, LabJson.decodeFromString<FleetUpdate>("""{"name":"sonarr","host":"docker","scan":{"critical":0,"high":3,"t":0}}""").scan?.high)
        assertEquals("CVEs: 2 critical · 7 high", Scan(2, 7).text())
        assertEquals("CVEs: 3 high", Scan(high = 3).text())
        assertEquals("no critical or high CVEs", Scan().text())
    }
}
