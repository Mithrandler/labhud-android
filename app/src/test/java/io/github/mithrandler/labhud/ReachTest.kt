package io.github.mithrandler.labhud

import io.github.mithrandler.labhud.data.LabRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException

/** What the offline banner says, from the exception the connection ended with. */
class ReachTest {
    private val url = "http://192.0.2.10:8095"
    private fun says(e: Throwable) = LabRepository.reach(url, e)

    @Test fun timeout() = assertTrue(says(SocketTimeoutException("connect timed out")).startsWith("No answer from $url"))
    @Test fun refused() = assertTrue("refused the connection" in says(ConnectException("Failed to connect: Connection refused")))
    @Test fun unknownName() = assertTrue("not known here" in says(UnknownHostException("wall.lan")))
    @Test fun certificate() = assertTrue("different certificate" in says(SSLPeerUnverifiedException("not the certificate LabHUD was paired with")))
    @Test fun wrapped() = assertTrue(says(IOException("canceled", SocketTimeoutException("timeout"))).startsWith("No answer"))
    @Test fun other() = assertTrue("home network or VPN" in says(IOException("something else")))
}
