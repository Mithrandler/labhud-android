package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.Pairing
import io.github.mithrandler.labhud.data.PinnedTrustManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.CertificateException

class PairingTest {
    private val code = "abcdefghijklmnopqrstuvwx"

    @Test fun parsesWhatLabhudPrints() {
        // the exact shape of devices.pair_link in labhud
        val link = Pairing.parse("labhud://pair?u=http%3A%2F%2F192.168.1.210%3A8095&c=$code")!!
        assertEquals("http://192.168.1.210:8095", link.url)
        assertEquals(code, link.code)
        assertEquals("", link.fingerprint)
        val fp = "a".repeat(64)
        assertEquals(fp, Pairing.parse("  labhud://pair?u=https%3A%2F%2Fwall.lan%2F&c=$code&fp=${fp.uppercase()}\n")!!.fingerprint)
    }

    @Test fun refusesOthers() {
        listOf(
            "https://example.com/?u=http://x&c=$code",
            "labhud://card/x",
            "labhud://pair?u=ftp%3A%2F%2Fx&c=$code",
            "labhud://pair?u=http%3A%2F%2Fx&c=short",
            "labhud://pair?c=$code",
            "labhud://pair?u=http%3A%2F%2Fx&c=$code&fp=nothex",
            "not a link at all",
        ).forEach { assertNull(it, Pairing.parse(it)) }
    }

    @Test fun pinnedTrustManager() {
        val der = byteArrayOf(1, 2, 3)
        assertEquals(64, LabApi.sha256(der).length)
        val tm = PinnedTrustManager(LabApi.sha256(der).uppercase())
        try {
            tm.checkServerTrusted(null, "RSA")
            fail("an empty chain must be refused")
        } catch (_: CertificateException) {
        }
    }
}
