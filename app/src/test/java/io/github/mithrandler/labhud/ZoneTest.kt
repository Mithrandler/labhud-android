package io.github.mithrandler.labhud

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
}
