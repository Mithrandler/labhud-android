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
        assertEquals(listOf("display", "infra", "monitor", "system", "phone"), GROUPS.map { it.first })
        assertEquals(
            mapOf(
                "display" to listOf("look", "dashboard"),
                "infra" to listOf("hosts", "containers", "integrations"),
                "monitor" to listOf("alerts", "notifications"),
                "system" to listOf("security", "phones", "api", "system"),
                "phone" to listOf("servers", "theme", "lock", "wall"),
            ),
            SECTIONS.groupBy({ it.group }, { it.id }),
        )
    }

    @Test
    fun `ids are unique`() = assertEquals(SECTIONS.size, SECTIONS.map { it.id }.toSet().size)

    @Test
    fun `not signed in shows only what is not the admin's`() =
        assertEquals(listOf("notifications", "security", "servers", "theme", "lock", "wall"), visibleSections(admin = false).map { it.id })

    @Test
    fun `the admin sees everything`() =
        assertEquals(SECTIONS.map { it.id }, visibleSections(admin = true).map { it.id })

    @Test
    fun `search finds by title and keyword, ignoring case`() {
        assertEquals(listOf("containers"), visibleSections(true, "DOCKER").map { it.id })
        assertEquals(listOf("phones"), visibleSections(true, "phones").map { it.id })
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
    fun `section by id`() {
        assertEquals("Hosts and agents", section("hosts")?.title)
        assertEquals(null, section("nope"))
    }
}
