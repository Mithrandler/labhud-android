package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.watching
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.Utf8Stream
import io.github.mithrandler.labhud.data.ZoneAnswer
import io.github.mithrandler.labhud.data.ZoneContainer
import io.github.mithrandler.labhud.data.ZoneCount
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.bytesText
import io.github.mithrandler.labhud.data.childOf
import io.github.mithrandler.labhud.data.crumbs
import io.github.mithrandler.labhud.data.filteredImages
import io.github.mithrandler.labhud.data.filteredNetworks
import io.github.mithrandler.labhud.data.filteredVolumes
import io.github.mithrandler.labhud.data.parentOf
import io.github.mithrandler.labhud.data.title
import io.github.mithrandler.labhud.data.usedText
import io.github.mithrandler.labhud.data.volumeSize
import io.github.mithrandler.labhud.data.filtered
import io.github.mithrandler.labhud.data.line
import io.github.mithrandler.labhud.data.ordered
import io.github.mithrandler.labhud.data.plainTerminal
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.stackDirty
import io.github.mithrandler.labhud.data.stackNameOk
import io.github.mithrandler.labhud.data.status
import io.github.mithrandler.labhud.data.versionText
import io.github.mithrandler.labhud.data.tailLines
import io.github.mithrandler.labhud.data.tone
import io.github.mithrandler.labhud.data.zoneAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneTest {
    private fun c(name: String, state: String = "running", health: String = "", image: String = "img", updating: Boolean = false) =
        ZoneContainer(name = name, state = state, health = health, image = image, updating = updating)

    @Test fun hostsAndContainersAreReadFromTheServersAnswer() {
        val a = LabJson.decodeFromString<ZoneAnswer>(
            """{"ok":true,"hosts":[{"name":"big","online":true,"version":"0.1.1","containers":{"running":6,"total":7},"cpu":12.5,"mem":41,"future":1}]}"""
        )
        assertTrue(a.ok)
        assertEquals(ZoneHost(name = "big", online = true, version = "0.1.1", containers = ZoneCount(6, 7), cpu = 12.5, mem = 41.0), a.hosts[0])
        val b = LabJson.decodeFromString<ZoneAnswer>(
            """{"ok":true,"online":true,"containers":[{"name":"web","state":"running","health":"healthy","image":"nginx:1","mem_pct":10.6,"net_rx":2100,"can":["start","stop"],"shell":true,"hook":true}]}"""
        )
        val w = b.containers[0]
        assertEquals(10.6, w.memPct, 0.0)
        assertEquals(2100L, w.netRx)
        assertEquals(listOf("start", "stop"), w.can)
        assertTrue(w.shell && w.hook && !w.auto)
    }

    @Test fun anAnswerWithNothingInItIsEmptyNotBroken() {
        val a = LabJson.decodeFromString<ZoneAnswer>("""{"ok":false,"error":"forbidden"}""")
        assertFalse(a.ok)
        assertTrue(a.hosts.isEmpty() && a.containers.isEmpty() && a.inspect == null && a.stats == null)
        assertEquals("forbidden", a.refusal())
    }

    @Test fun aSessionThatEndedAndABlockAreToldApart() {
        val ended = LabJson.decodeFromString<ZoneAnswer>("""{"ok":false,"error":"the Servers session ended","resume":true}""")
        assertTrue(ended.resume)
        val blocked = LabJson.decodeFromString<ZoneAnswer>("""{"ok":false,"error":"too many refused calls: wait","retry_after":60}""")
        assertEquals("Too many refused calls: wait 60 s.", blocked.refusal())
        assertEquals("refused", ZoneAnswer().refusal())
    }

    @Test fun statusAndToneOfAContainer() {
        assertEquals("healthy", c("a", health = "healthy").status())
        assertEquals("exited", c("a", state = "exited", health = "healthy").status())
        assertEquals(0, c("a").tone())
        assertEquals(1, c("a", health = "unhealthy").tone())
        assertEquals(1, c("a", updating = true).tone())
        assertEquals(2, c("a", state = "exited").tone())
    }

    @Test fun filteringKeepsMatchesAndPutsTheOnesNeedingAttentionFirst() {
        val rows = listOf(c("zeta"), c("Alpha", image = "nginx:1"), c("down", state = "exited"), c("sick", health = "unhealthy"))
        assertEquals(listOf("down", "sick", "Alpha", "zeta"), rows.filtered("").map { it.name })
        assertEquals(listOf("Alpha"), rows.filtered(" NGINX ").map { it.name })
        assertEquals(listOf("zeta"), rows.filtered("ZET").map { it.name })
        assertTrue(rows.filtered("nothing like it").isEmpty())
    }

    @Test fun aHostLine() {
        assertEquals("6 / 7", ZoneHost(online = true, containers = ZoneCount(6, 7)).line())
        assertEquals("–", ZoneHost(online = false, containers = ZoneCount(6, 7)).line())
    }

    @Test fun bytesAreWrittenInBase1024() {
        assertEquals("0", bytesText(0.0))
        assertEquals("512 B", bytesText(512.0))
        assertEquals("1.5 KB", bytesText(1536.0))
        assertEquals("806 MB", bytesText(845_000_000.0))
        assertEquals("14.9 GB", bytesText(16_000_000_000.0))
        assertEquals("120 KB", bytesText(120.0 * 1024))
    }

    @Test fun aTerminalsTextLosesItsCodes() {
        assertEquals("ls\nfile.txt\n", plainTerminal("\u001B[0m\u001B[1;32mls\u001B[0m\r\nfile.txt\r\n"))
        assertEquals("title", plainTerminal("\u001B]0;window\u0007title"))
        assertEquals("a\tb", plainTerminal("a\tb\u0001\u007F"))
        assertEquals("no", plainTerminal("n\ro"))
    }

    @Test fun onlyTheLastLinesStay() {
        assertEquals("c\nd", tailLines("a\nb\nc\nd", 2))
        assertEquals("a", tailLines("a", 5))
    }

    @Test fun aCharacterCutBetweenTwoChunksIsNotLost() {
        val bytes = "ăbț→😀".toByteArray()
        for (cut in 1 until bytes.size) {
            val s = Utf8Stream()
            assertEquals("cut at $cut", "ăbț→😀", s.feed(bytes.copyOfRange(0, cut)) + s.feed(bytes.copyOfRange(cut, bytes.size)))
        }
        val three = Utf8Stream()
        val out = bytes.toList().chunked(1).joinToString("") { three.feed(it.toByteArray()) }
        assertEquals("ăbț→😀", out)
    }

    @Test fun serversNeedsHttpsExceptInADebugBuild() {
        assertTrue(zoneAllowed("https://lab.example", debug = false))
        assertTrue(zoneAllowed("HTTPS://lab.example", debug = false))
        assertFalse(zoneAllowed("http://192.0.2.1:8095", debug = false))
        assertTrue(zoneAllowed("http://10.0.2.2:8196", debug = true))
    }
}

class SwipeStepTest {
    @Test fun aShortDragIsNothingAndALongOneIsOnePlace() {
        assertEquals(0, io.github.mithrandler.labhud.ui.swipeStep(-100f, 1080f))
        assertEquals(0, io.github.mithrandler.labhud.ui.swipeStep(100f, 1080f))
        assertEquals(1, io.github.mithrandler.labhud.ui.swipeStep(-400f, 1080f))     // to the left: the next
        assertEquals(-1, io.github.mithrandler.labhud.ui.swipeStep(400f, 1080f))     // to the right: the previous
        assertEquals(1, io.github.mithrandler.labhud.ui.swipeStep(-163f, 1080f))
        assertEquals(0, io.github.mithrandler.labhud.ui.swipeStep(-162f, 1080f))
    }
}

class ZoneObjectsTest {
    private fun image(id: String, vararg tags: String, size: Long = 1) =
        io.github.mithrandler.labhud.data.ZoneImage(id = id, tags = tags.toList(), size = size)

    @Test fun anImageIsNamedByItsFirstTagElseItsId() {
        assertEquals("nginx:1", image("abc", "nginx:1", "nginx:latest").title())
        assertEquals("abc", image("abc").title())
    }

    @Test fun whatUsesItIsWritten() {
        assertEquals("unused", usedText(0, emptyList()))
        assertEquals("used by 2", usedText(2, emptyList()))
        assertEquals("used by web, db", usedText(2, listOf("web", "db")))
        assertEquals("used by a, b +3", usedText(5, listOf("a", "b")))
    }

    @Test fun imagesAreFilteredByTagOrIdAndTheBiggestComeFirst() {
        val rows = listOf(image("1111", "alpine:3", size = 7), image("2222", "nginx:1", "web:2", size = 142), image("3333", size = 88))
        assertEquals(listOf("2222", "3333", "1111"), rows.filteredImages("").map { it.id })
        assertEquals(listOf("2222"), rows.filteredImages(" WEB ").map { it.id })
        assertEquals(listOf("3333"), rows.filteredImages("333").map { it.id })
    }

    @Test fun volumesAndNetworksAreFiltered() {
        val vols = listOf(io.github.mithrandler.labhud.data.ZoneVolume(name = "pgdata", size = 9), io.github.mithrandler.labhud.data.ZoneVolume(name = "cache", size = 99))
        assertEquals(listOf("cache", "pgdata"), vols.filteredVolumes("").map { it.name })
        assertEquals(listOf("pgdata"), vols.filteredVolumes("PG").map { it.name })
        val nets = listOf(io.github.mithrandler.labhud.data.ZoneNetwork(name = "proxy"), io.github.mithrandler.labhud.data.ZoneNetwork(name = "media"))
        assertEquals(listOf("media", "proxy"), nets.filteredNetworks("").map { it.name })
    }

    @Test fun aVolumeSizeThatIsNotKnownIsADash() {
        assertEquals("–", volumeSize(-1))
        assertEquals("0", volumeSize(0))
        assertEquals("1.5 KB", volumeSize(1536))
    }

    @Test fun aPathIsBrokenIntoStepsAndWalksUpAndDown() {
        assertEquals(listOf("/" to "/"), crumbs("/"))
        assertEquals(listOf("/" to "/", "base" to "/base", "16384" to "/base/16384"), crumbs("/base/16384"))
        assertEquals("/base", parentOf("/base/16384"))
        assertEquals("/", parentOf("/base"))
        assertEquals("/", parentOf("/"))
        assertEquals("/a", childOf("/", "a"))
        assertEquals("/a/b", childOf("/a", "b"))
    }

    @Test fun theNewAnswersAreReadFromTheServer() {
        val a = LabJson.decodeFromString<ZoneAnswer>(
            """{"ok":true,"images":[{"id":"a1","tags":["x:1"],"size":5,"used":1,"used_by":["web"],"dangling":false}],"volumes":[{"name":"pg","size":-1,"browse":true}],
               "networks":[{"name":"proxy","builtin":false,"used_by":["a"]}],"entries":[{"name":"base","dir":true}],"path":"/","truncated":true,
               "file":{"path":"/f","size":3,"text":"hi","masked":false},"network":{"name":"proxy","containers":[{"name":"a","ipv4":"1.2.3.4"}]}}"""
        )
        assertEquals(listOf("web"), a.images[0].usedBy)
        assertTrue(a.volumes[0].browse)
        assertEquals(-1L, a.volumes[0].size)
        assertTrue(a.entries[0].dir && a.truncated)
        assertEquals("hi", a.file!!.text)
        assertEquals("1.2.3.4", a.network!!.containers[0].ipv4)
        val no = LabJson.decodeFromString<ZoneAnswer>("""{"ok":false,"error":"this volume is not opened for browsing","needs_access":true}""")
        assertTrue(no.needsAccess)
    }

    @Test fun stacksAreReadFromTheAnswerAndSayWhatTheyAre() {
        val a = LabJson.decodeFromString<ZoneAnswer>(
            """{"ok":true,"enabled":true,"stacks":[{"name":"site","updated":5,"env":true,"services":3,"running":2,"source":{"repo":"https://git.example/o/n","branch":"main","path":"c.yaml"},"hook":true},{"name":"old"}],
               "compose":"a: 1\n","env":"K=********\n","has_env":true,"versions":[1700000200,1700000100],"mask":"********","output":"x"}"""
        )
        assertTrue(a.enabled && a.hasEnv)
        assertEquals("2 / 3 running · git · webhook", a.stacks[0].line())
        assertEquals("stopped", a.stacks[1].line())
        assertEquals(listOf(1700000200L, 1700000100L), a.versions)
        assertEquals("********", a.mask)
    }

    @Test fun stackNamesAndUnsavedTextFollowTheHostsRules() {
        assertTrue(stackNameOk("site-2_x"))
        assertFalse(stackNameOk("Site"))
        assertFalse(stackNameOk("-a"))
        assertFalse(stackNameOk(""))
        assertFalse(stackNameOk("a".repeat(65)))
        assertFalse(stackDirty("a", "a", "b", "b"))
        assertTrue(stackDirty("a2", "a", "b", "b"))
        assertTrue(stackDirty("a", "a", "b2", "b"))
    }

    @Test fun aVersionIsShownInTheGivenZoneWithoutALocale() {
        assertEquals("2023-11-14 22:13", versionText(1700000000L, java.time.ZoneId.of("UTC")))
    }

    @Test fun guestHelpersAreAsTheHostTakesThem() {
        assertEquals(true, io.github.mithrandler.labhud.data.snapNameOk("before-upgrade"))
        assertEquals(true, io.github.mithrandler.labhud.data.snapNameOk("a"))
        for (bad in listOf("", "1abc", "-x", "a b", "a;b", "a".repeat(41))) assertEquals(bad, false, io.github.mithrandler.labhud.data.snapNameOk(bad))
        assertEquals("pbs", io.github.mithrandler.labhud.data.snapConfirmWord("rollback", "pbs", "s1"))
        assertEquals("s1", io.github.mithrandler.labhud.data.snapConfirmWord("delete", "pbs", "s1"))
        assertEquals("9 d", io.github.mithrandler.labhud.data.upText(9 * 86400L))
        assertEquals("5 h", io.github.mithrandler.labhud.data.upText(5 * 3600L))
        assertEquals("12 min", io.github.mithrandler.labhud.data.upText(12 * 60L))
    }

    @Test fun guestsAreOrderedAndReadFromTheAnswer() {
        val r = LabJson.decodeFromString<ZoneAnswer>(
            """{"ok":true,"enabled":true,"node":"pve2","guests":[
                {"id":"900","name":"tpl","type":"qemu","template":true,"ticked":true},
                {"id":"201","name":"rea","type":"qemu","running":false,"ticked":true},
                {"id":"113","name":"web","type":"lxc","running":true,"cpu":1.4,"mem":22.0,"uptime":400000,"ticked":true,"can":["start","shutdown","reboot"]},
                {"id":"101","name":"pbs","type":"qemu","running":true,"ticked":true}]}""")
        val ordered = r.guests.ordered()
        assertEquals(listOf("101", "113", "201", "900"), ordered.map { it.id })
        assertEquals("LXC · 1% · 22% · 4 d", ordered[1].line())
        assertEquals("VM · stopped", ordered[2].line())
        assertEquals("template · stopped", ordered[3].line())
        assertEquals("vm/101/x", io.github.mithrandler.labhud.data.ZoneBackup(volid = "pbs:backup/vm/101/x").title())
    }

    @Test fun theUpdateCheckSpeaksPlainly() {
        val about = { json: String -> LabJson.decodeFromString<io.github.mithrandler.labhud.data.About>(json) }
        assertEquals("Not checked yet.", io.github.mithrandler.labhud.ui.updateWords(null, false))
        assertEquals("Could not ask the server.", io.github.mithrandler.labhud.ui.updateWords(null, true))
        assertEquals(true, io.github.mithrandler.labhud.ui.updateWords(about("""{"enabled":false}"""), false).contains("switched off"))
        assertEquals("A newer version is out.", io.github.mithrandler.labhud.ui.updateWords(
            about("""{"enabled":true,"update":true,"latest":{"server":"v0.2.0"}}"""), false))
        assertEquals(true, io.github.mithrandler.labhud.ui.updateWords(
            about("""{"enabled":true,"latest":{"server":"v0.1.2","app":"v0.1.2"}}"""), false).startsWith("You are up to date."))
    }

    @Test fun an_images_cves_read_the_same_everywhere() {
        val bad = io.github.mithrandler.labhud.data.ZoneScan(2, 5, "trivy", 1700000000, listOf("web"))
        assertEquals("● 2 crit · 5 high", io.github.mithrandler.labhud.data.cveText(bad))
        assertEquals("● 3 high", io.github.mithrandler.labhud.data.cveText(bad.copy(critical = 0, high = 3)))
        assertEquals("● clean", io.github.mithrandler.labhud.data.cveText(bad.copy(critical = 0, high = 0)))
        assertEquals("○ not scanned", io.github.mithrandler.labhud.data.cveText(null))
        assertEquals(listOf(2, 1, 0, -1), listOf(bad, bad.copy(critical = 0), bad.copy(critical = 0, high = 0), null).map { io.github.mithrandler.labhud.data.cveLevel(it) })
        assertEquals("trivy · scanned 2023-11-14 22:13", io.github.mithrandler.labhud.data.scanWhen(bad, java.time.ZoneOffset.UTC))
        assertEquals("? · scanned ?", io.github.mithrandler.labhud.data.scanWhen(io.github.mithrandler.labhud.data.ZoneScan(), java.time.ZoneOffset.UTC))
    }

    @Test fun a_hosts_uptime_and_activity_read_short() {
        assertEquals(listOf("–", "7m", "3h 5m", "12d 0h"), listOf(0L, 420L, 11100L, 1036800L).map { io.github.mithrandler.labhud.data.uptimeText(it) })
        val e = io.github.mithrandler.labhud.data.ZoneActivity("t", "w", "servers/unit-restart", "h/u", true)
        assertEquals("unit-restart", io.github.mithrandler.labhud.data.activityText(e))
        assertEquals("unit-restart (refused)", io.github.mithrandler.labhud.data.activityText(e.copy(ok = false)))
    }

    @Test fun a_jobs_days_and_editor_values_read_and_check() {
        assertEquals("every day", io.github.mithrandler.labhud.data.daysText(listOf(0, 1, 2, 3, 4, 5, 6)))
        assertEquals("Mon Wed Sun", io.github.mithrandler.labhud.data.daysText(listOf(6, 0, 2)))
        assertEquals(true, io.github.mithrandler.labhud.data.jobOk("Prune", "03:30", setOf(6)))
        assertEquals(false, io.github.mithrandler.labhud.data.jobOk(" ", "03:30", setOf(6)))
        assertEquals(false, io.github.mithrandler.labhud.data.jobOk("Prune", "3:30", setOf(6)))
        assertEquals(false, io.github.mithrandler.labhud.data.jobOk("Prune", "24:00", setOf(6)))
        assertEquals(false, io.github.mithrandler.labhud.data.jobOk("Prune", "03:30", emptySet()))
    }

    @Test fun a_backup_job_needs_guests_and_a_storage_and_the_agents_history_reads_as_the_agents() {
        assertEquals(true, io.github.mithrandler.labhud.data.jobOk("B", "02:00", setOf(0), "backup", setOf("101"), "local"))
        assertEquals(false, io.github.mithrandler.labhud.data.jobOk("B", "02:00", setOf(0), "backup", emptySet(), "local"))
        assertEquals(false, io.github.mithrandler.labhud.data.jobOk("B", "02:00", setOf(0), "backup", setOf("101"), "a b"))
        assertEquals(true, io.github.mithrandler.labhud.data.jobOk("P", "02:00", setOf(0)))     // other kinds need none of it
        val a = io.github.mithrandler.labhud.data.ZoneActivity("t", "agent", "agent/crash", "h/web", false, "agent")
        assertEquals("agent: crash (failed)", io.github.mithrandler.labhud.data.activityText(a))
        val u = io.github.mithrandler.labhud.data.ZoneUpdates(12, 3, listOf("libssl3", "curl"))
        assertEquals("12 pending (3 security): libssl3, curl…", io.github.mithrandler.labhud.data.updatesText(u))
        assertEquals("up to date", io.github.mithrandler.labhud.data.updatesText(io.github.mithrandler.labhud.data.ZoneUpdates()))
    }

    @Test fun alert_rules_read_from_the_server_change_their_what_and_go_back_as_json() {
        val o = kotlinx.serialization.json.Json.parseToJsonElement("""{"on":"*","what":"cpu","above":90,"minutes":10,"x":1}""") as kotlinx.serialization.json.JsonObject
        val r = io.github.mithrandler.labhud.data.alertRule(o)!!
        assertEquals(io.github.mithrandler.labhud.data.AlertRule("*", "cpu", 90, 10), r)
        val off = r.watching("offline", listOf("offline", "failed"))
        assertEquals(null, off.above)
        assertEquals("""{"on":"*","what":"offline","minutes":10}""", off.json().toString())
        assertEquals(85, off.watching("temp", listOf("offline", "failed")).above)       // a mark again: the usual one
        assertEquals(150, io.github.mithrandler.labhud.data.alertMarkMax("temp")); assertEquals(100, io.github.mithrandler.labhud.data.alertMarkMax("disk"))
        assertEquals(null, io.github.mithrandler.labhud.data.alertRule(kotlinx.serialization.json.buildJsonObject { }))
        assertEquals("23:00 → 07:00", io.github.mithrandler.labhud.data.quietText(23, 7)); assertEquals("off", io.github.mithrandler.labhud.data.quietText(0, 0))
    }

    @Test fun the_text_size_is_kept_in_steps_and_the_scan_and_source_forms_read_short() {
        assertEquals(listOf(85, 85, 100, 100, 105, 130, 130), listOf(10, 86, 100, 102, 103, 129, 500).map { io.github.mithrandler.labhud.ui.textScaleOf(it) })
        val d = io.github.mithrandler.labhud.data.ScanStatus(running = true, done = 3, total = 254, found = listOf(io.github.mithrandler.labhud.data.ScanFound("pihole", "http://x:80")))
        assertEquals("3 of 254 · 1 found", io.github.mithrandler.labhud.data.scanText(d))
        assertEquals("10.0.0.0/24 · 1 found", io.github.mithrandler.labhud.data.scanText(d.copy(running = false, cidr = "10.0.0.0/24")))
        assertEquals("", io.github.mithrandler.labhud.data.scanText(io.github.mithrandler.labhud.data.ScanStatus()))
        val f = { n: String, secret: Boolean, opt: Boolean -> io.github.mithrandler.labhud.data.SourceField(n, n, secret, "", opt) }
        val src = io.github.mithrandler.labhud.data.SourceItem("x", "X", "", listOf(f("URL", false, false), f("TOKEN", true, false), f("EXTRA", false, true)))
        assertEquals(false, io.github.mithrandler.labhud.data.sourceFormOk(src, emptyMap()))
        assertEquals(true, io.github.mithrandler.labhud.data.sourceFormOk(src, mapOf("URL" to "http://a")))     // a secret may stay empty: the one set is kept
        assertEquals(false, io.github.mithrandler.labhud.data.sourceFormOk(src, mapOf("URL" to "  ")))
    }

    @Test fun a_new_pin_is_four_digits_typed_twice_and_the_config_check_reads_short() {
        val ok = { a: String, b: String -> io.github.mithrandler.labhud.data.pinOk(a, b) }
        assertEquals(true, ok("1234", "1234")); assertEquals(false, ok("1234", "1235")); assertEquals(false, ok("123", "123"))
        assertEquals(false, ok("12345", "12345")); assertEquals(false, ok("12a4", "12a4")); assertEquals(false, ok("", ""))
        val d = io.github.mithrandler.labhud.data.ConfigCheck(ok = true)
        assertEquals("✓ A good config.", io.github.mithrandler.labhud.data.configSaid(d, saved = false))
        assertEquals("✓ Saved: LabHUD reads it now.", io.github.mithrandler.labhud.data.configSaid(d, saved = true))
        assertEquals("✕ not valid TOML", io.github.mithrandler.labhud.data.configSaid(d.copy(ok = false, problems = listOf("not valid TOML")), saved = true))
        assertEquals("✕ refused", io.github.mithrandler.labhud.data.configSaid(d.copy(ok = false), saved = false))
        val many = d.copy(ok = false, problems = (1..10).map { "p$it" })
        assertEquals(true, io.github.mithrandler.labhud.data.configSaid(many, false).endsWith("… 2 more"))
    }
}
