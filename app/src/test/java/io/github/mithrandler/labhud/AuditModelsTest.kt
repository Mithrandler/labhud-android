package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.ApiTokens
import io.github.mithrandler.labhud.data.AuditPage
import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.NewToken
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.settings.auditTime
import io.github.mithrandler.labhud.ui.settings.scopeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditModelsTest {
    private fun read(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun tokens() {
        val t = LabJson.decodeFromString<ApiTokens>(read("tokens.json"))
        assertTrue(t.ok)
        assertTrue(t.enabled)
        assertEquals(3, t.tokens.size)
        assertEquals(listOf("home-assistant", "backup-script", "ci-deploy"), t.tokens.map { it.name })
        assertEquals(listOf("read", "act", "admin"), t.tokens.map { it.scope })
        assertEquals(0L, t.tokens[0].expires)
        assertTrue(t.tokens[0].last > 0)
        assertEquals(0L, t.tokens[2].last)
        assertTrue(t.tokens.all { !it.expired })
    }

    @Test fun audit() {
        val p = LabJson.decodeFromString<AuditPage>(read("audit.json"))
        assertTrue(p.ok)
        assertTrue(p.enabled)
        assertEquals(90, p.days)
        assertEquals(4, p.rows.size)
        assertTrue(p.rows.all { it.ts.endsWith("Z") })
        assertEquals("admin", p.rows[0].who)
        assertEquals("sign-in", p.rows[0].action)
        assertTrue(p.rows[0].ok)
    }

    @Test fun newToken() {
        val n = LabJson.decodeFromString<NewToken>("""{"ok":true,"token":"lh_abc","id":"0011aabb","name":"x","scope":"act","made":1,"expires":0,"last":0,"ip":"","expired":false}""")
        assertEquals("lh_abc", n.token)
        assertEquals("act", n.scope)
        assertEquals("0011aabb", n.id)
    }

    @Test fun defaults() {
        val p = LabJson.decodeFromString<AuditPage>("{}")
        assertFalse(p.ok)
        assertTrue(p.rows.isEmpty())
        assertFalse(p.more)
        assertFalse(p.enabled)
        val t = LabJson.decodeFromString<ApiTokens>("""{"ok":false,"error":"forbidden"}""")
        assertEquals("forbidden", t.error)
        assertTrue(t.tokens.isEmpty())
    }

    @Test fun scopes() {
        assertEquals(Lab.red, scopeColor("admin"))
        assertEquals(Lab.warn, scopeColor("act"))
        assertEquals(Lab.sub, scopeColor("read"))
        assertEquals(Lab.sub, scopeColor("bogus"))
    }

    @Test fun auditTimeFallsBack() {
        assertEquals("not a time", auditTime("not a time"))
        val formatted = auditTime("2026-10-18T13:01:36Z")
        assertTrue(formatted.isNotEmpty())
        assertTrue(formatted != "2026-10-18T13:01:36Z")
    }
}
