package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.About
import io.github.mithrandler.labhud.data.Container
import io.github.mithrandler.labhud.data.GameServer
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.ui.ServiceIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IconsAboutTest {
    private fun read(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun containersCarryImageAndIcon() {
        val cs = LabJson.decodeFromString<io.github.mithrandler.labhud.data.Card>(read("card-vm-pve-100.json")).containers
        val jelly = cs.first { it.name == "jellyfin" }
        assertEquals("jellyfin", jelly.icon)
        assertEquals("jellyfin/jellyfin:10.10.3", jelly.image)
        assertEquals("", Container().icon)
    }

    @Test fun gameCarriesIcon() {
        val g = LabJson.decodeFromString<GameServer>(read("game-a1f3.json"))
        assertEquals("minecraft", g.icon)
    }

    @Test fun lineIconsKnowEveryName() {
        for (n in listOf("home", "network", "media", "games", "discover", "container", "plug", "phone", "key", "settings")) assertNotNull(n, ServiceIcons.line(n))
        assertNull(ServiceIcons.line("jellyfin"))
    }

    @Test fun aboutWithUpdates() {
        val a = LabJson.decodeFromString<About>(read("about.json"))
        assertEquals("0.1.3", a.appUpdate())
        assertEquals("0.1.1", a.serverUpdate())
        assertEquals("https://github.com/Mithrandler/labhud-android/releases/latest", a.appDownload())
    }

    @Test fun aboutSwitchedOffOrCurrent() {
        assertNull(About().appUpdate())
        assertNull(About(enabled = false, update = true).serverUpdate())
        val a = LabJson.decodeFromString<About>(read("about.json")).copy(outdated = io.github.mithrandler.labhud.data.Outdated())
        assertNull(a.appUpdate())
    }
}
