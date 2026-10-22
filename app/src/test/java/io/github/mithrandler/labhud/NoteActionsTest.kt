package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.LabJson
import io.github.mithrandler.labhud.data.Notes
import io.github.mithrandler.labhud.data.allowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteActionsTest {
    private val json = """{"via":"app","notifications":[
        {"id":7,"time":9,"title":"t","text":"2 new","bad":false,"actions":[
          {"id":"add","label":"Add","path":"/api/discover/approve","body":{"keys":["vm:101","ct:7"]}},
          {"id":"ignore","label":"Ignore","path":"/api/discover/dismiss","body":{"keys":["vm:101"]}}]},
        {"id":8,"time":10,"title":"t","text":"plain"}]}"""

    @Test fun parsesActions() {
        val n = LabJson.decodeFromString<Notes>(json).notifications
        assertEquals(listOf("Add", "Ignore"), n[0].actions.map { it.label })
        assertEquals("""{"keys":["vm:101","ct:7"]}""", n[0].actions[0].body.toString())
        assertTrue(n[0].actions.all { it.allowed() })
        assertTrue(n[1].actions.isEmpty())
    }

    @Test fun refusesOtherPaths() {
        val a = LabJson.decodeFromString<Notes>(json).notifications[0].actions[0]
        assertFalse(a.copy(path = "/api/admin/reset").allowed())
        assertFalse(a.copy(path = "/api/discover/../admin").allowed())
        assertFalse(a.copy(label = "").allowed())
    }
}
