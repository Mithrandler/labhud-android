package io.github.mithrandler.labhud.ui

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/* The small widgets a card can be (labhud 0.1.1, X): drawn here without any data from the server, so they cost no polling. */

/** The zone an IANA name stands for, the phone's own for "" or a name this phone does not know. */
fun zoneOf(name: String): ZoneId = runCatching { if (name.isEmpty()) ZoneId.systemDefault() else ZoneId.of(name) }.getOrDefault(ZoneId.systemDefault())

/** The time and the date in a zone. Ticks each second only while it is on screen. */
@Composable
fun ClockWidget(timezone: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    PollWhileStarted(intervalMs = 1000) { now = System.currentTimeMillis() }
    val locale = LocalConfiguration.current.locales[0]
    val zone = zoneOf(timezone)
    val at = Instant.ofEpochMilli(now).atZone(zone)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(at.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 34.sp, fontWeight = FontWeight.Light, color = Lab.text)
        Text(at.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)), fontSize = 12.sp, color = Lab.muted)
        if (timezone.isNotEmpty()) Text(zone.id, fontSize = 11.sp, color = Lab.muted)
    }
}

/** The weeks of a month, Monday first, each day as a number or null before the first and after the last. */
fun monthWeeks(first: LocalDate): List<List<Int?>> {
    val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val days = List(offset) { null } + (1..first.lengthOfMonth()).map { it as Int? }
    return days.chunked(7).map { it + List(7 - it.size) { null } }
}

/** This month, today marked. */
@Composable
fun CalendarWidget() {
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val weeks = remember(today) { monthWeeks(today.withDayOfMonth(1)) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(today.month.getDisplayName(TextStyle.FULL, locale) + " " + today.year, fontSize = 12.sp, color = Lab.accent, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { Text(it.getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f), fontSize = LabScale.textXs, color = Lab.muted, textAlign = TextAlign.Center) }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val isToday = d == today.dayOfMonth
                    Text(d?.toString() ?: "", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center,
                        color = if (isToday) Lab.accent else Lab.sub, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** True for an address the frame may show: http or https, with a host. */
fun frameAllowed(url: String): Boolean = runCatching {
    val u = java.net.URI(url)
    (u.scheme == "http" || u.scheme == "https") && !u.host.isNullOrEmpty()
}.getOrDefault(false)

/** A page inside the card. It stays on its own host (a link out is not followed), loads no file and takes no permission. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FrameWidget(url: String, height: Int) {
    if (!frameAllowed(url)) {
        Text("Not a web address.", fontSize = 12.sp, color = Lab.muted)
        return
    }
    val host = java.net.URI(url).host
    AndroidView(
        modifier = Modifier.fillMaxWidth().height((if (height in 120..800) height else 240).dp).padding(top = 6.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != host   // a link to another host is not followed
                    // the page's renderer died: drop this view rather than the whole app
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                        view.destroy()
                        return true
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
