package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.ui.normalMac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacTest {
    @Test fun typedMacs() {
        assertEquals("aa:bb:cc:dd:ee:ff", normalMac(" AA-BB-CC-DD-EE-FF "))
        assertEquals("7c:83:34:bb:36:47", normalMac("7c:83:34:bb:36:47"))
        assertNull(normalMac("7c:83:34:bb:36"))
        assertNull(normalMac("7c8334bb3647"))
        assertNull(normalMac("zz:83:34:bb:36:47"))
        assertNull(normalMac(""))
    }
}
