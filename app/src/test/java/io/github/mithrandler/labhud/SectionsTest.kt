package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.ui.settings.GROUPS
import io.github.mithrandler.labhud.ui.settings.SECTIONS
import io.github.mithrandler.labhud.ui.settings.section
import io.github.mithrandler.labhud.ui.settings.visibleSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {

    @Test
    fun `same groups and sections as the wall`() {
        assertEquals(listOf("account", "display", "infra", "monitor", "system", "phone"), GROUPS.map { it.first })
        assertEquals(
            mapOf(
                "account" to listOf("security"),
                "display" to listOf("look", "dashboard"),
                "infra" to listOf("hosts", "containers", "integrations"),
                "monitor" to listOf("alerts", "notifications"),
                "system" to listOf("api", "system"),
                "phone" to listOf("servers", "theme", "lock", "wall"),
            ),
            SECTIONS.groupBy({ it.group }, { it.id }),
        )
    }

    @Test
    fun `ids are unique`() = assertEquals(SECTIONS.size, SECTIONS.map { it.id }.toSet().size)

    @Test
    fun `not signed in shows only what is not the admin's`() =
        assertEquals(listOf("security", "notifications", "servers", "theme", "lock", "wall"), visibleSections(admin = false).map { it.id })

    @Test
    fun `the admin sees everything`() =
        assertEquals(SECTIONS.map { it.id }, visibleSections(admin = true).map { it.id })

    @Test
    fun `search finds by title and keyword, ignoring case`() {
        assertEquals(listOf("containers"), visibleSections(true, "DOCKER").map { it.id })
        assertEquals(listOf("security"), visibleSections(true, "phones").map { it.id })   // Phones is a panel of Account now
        assertEquals(listOf("security"), visibleSections(false, "sessions").map { it.id })
        assertTrue(visibleSections(false, "docker").isEmpty())
        assertEquals(SECTIONS.size, visibleSections(true, "  ").size)
    }

    @Test
    fun `API and audit is the admin's and found by audit`() {
        assertEquals(true, section("api")?.admin)
        assertEquals(listOf("api"), visibleSections(true, "audit").map { it.id })
        assertTrue(visibleSections(false, "audit").isEmpty())
    }

    @Test
    fun `Account is first, titled so, and visible without signing in`() {
        assertEquals("security", SECTIONS.first().id)
        assertEquals("Account", SECTIONS.first().title)
        assertEquals("security", visibleSections(admin = false).first().id)
        assertEquals(null, section("phones"))
    }

    @Test
    fun `section by id`() {
        assertEquals("Hosts and agents", section("hosts")?.title)
        assertEquals(null, section("nope"))
    }
}
