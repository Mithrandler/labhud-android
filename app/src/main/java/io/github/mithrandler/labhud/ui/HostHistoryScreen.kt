package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.History
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Metric
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SPANS = listOf("6h" to 6 * 3600, "24h" to 86400, "7d" to 7 * 86400)
private const val POINTS = 96

/** One host's resources over time: a big chart per number, a finger on it reads the value and the hour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostHistoryScreen(repo: LabRepository, id: String, onBack: () -> Unit, onCard: (String) -> Unit) {
    val state by repo.state.collectAsState()
    val card = state?.byId?.get(id)
    var span by rememberSaveable { mutableStateOf(0) }
    var short by remember { mutableStateOf<History?>(null) }
    var long by remember { mutableStateOf<History?>(null) }

    LaunchedEffect(id) {
        while (true) {
            repo.api()?.let { api -> runCatching { api.history(false) }.onSuccess { short = it } }
            delay(60_000)
        }
    }
    LaunchedEffect(span) {
        if (span > 0) repo.api()?.let { api -> runCatching { api.history(true) }.onSuccess { long = it } }
    }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(LabIcons.back, "Back") } },
                title = { Text(card?.name ?: "", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = { onCard(id) }) { Text("Card") } },
            )
        },
    ) { pad ->
        val metrics = card?.resources().orEmpty()
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SPANS.forEachIndexed { i, (label, _) ->
                        SegmentedButton(selected = span == i, onClick = { span = i },
                            shape = SegmentedButtonDefaults.itemShape(i, SPANS.size)) { Text(label) }
                    }
                }
            }
            if (metrics.isEmpty()) item { Text("This card keeps no history.", color = Lab.muted) }
            metrics.forEach { m ->
                item(key = m.key) {
                    val h = if (span == 0) short else long
                    val series = h?.series?.get(m.key).orEmpty()
                    val keep = if (h == null || h.step == 0) series.size else (SPANS[span].second / h.step).coerceAtMost(series.size)
                    Panel("") {
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.short(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(m.value, color = sevColor(m.sev), fontWeight = FontWeight.SemiBold)
                        }
                        if (h == null) Text("Loading…", fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 8.dp))
                        else ScrubChart(series.takeLast(keep), h.step, m, Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * The line of one number with min / average / max under it. A finger on the chart draws a
 * vertical rule and shows the value and the time there; lifting it brings back the latest.
 */
@Composable
fun ScrubChart(raw: List<Double?>, step: Int, m: Metric, modifier: Modifier = Modifier) {
    val per = ((raw.size + POINTS - 1) / POINTS).coerceAtLeast(1)
    val points = buckets(raw, POINTS)
    val values = points.filterNotNull()
    var touch by remember { mutableStateOf<Float?>(null) }
    if (values.size < 2) {
        Text("No history yet", fontSize = 12.sp, color = Lab.muted, modifier = modifier)
        return
    }
    val lo = if (m.isShare()) 0.0 else values.min() - 2
    val hi = if (m.isShare()) maxOf(100.0, values.max()) else values.max() + 2
    val color = m.color()
    val now = System.currentTimeMillis()
    val pointMs = per * step * 1000L
    val time = if (raw.size * step > 86400) SimpleDateFormat("EEE HH:mm", Locale.ENGLISH) else DateFormat.getTimeInstance(DateFormat.SHORT)

    Column(modifier) {
        val at = touch?.let { x -> x.coerceIn(0f, 1f) }?.let { f -> ((points.size - 1) * f + 0.5f).toInt() }
        Text(
            if (at != null) "${m.show(points[at])} · ${time.format(Date(now - (points.size - 1 - at) * pointMs))}" else " ",
            fontSize = 12.sp, color = Lab.sub,
        )
        Box(Modifier.fillMaxWidth().height(140.dp).pointerInput(points.size) {
            awaitEachGesture {
                val down = awaitFirstDown()
                touch = down.position.x / size.width
                while (true) {
                    val e = awaitPointerEvent()
                    val p = e.changes.firstOrNull() ?: break
                    if (!p.pressed) break
                    touch = p.position.x / size.width
                    p.consume()
                }
                touch = null
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val stepX = size.width / (points.size - 1)
                fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
                for (i in 0..4) {
                    val gy = size.height * i / 4f
                    drawLine(Lab.line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                }
                // where it turns serious, when the metric is a share: 90% dashed
                if (m.isShare()) drawLine(Lab.red.copy(alpha = 0.5f), Offset(0f, y(90.0)), Offset(size.width, y(90.0)),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                var run = mutableListOf<Offset>()
                val runs = mutableListOf<List<Offset>>()
                points.forEachIndexed { i, v ->
                    if (v == null) { if (run.isNotEmpty()) runs.add(run); run = mutableListOf() } else run.add(Offset(i * stepX, y(v)))
                }
                if (run.isNotEmpty()) runs.add(run)
                for (r in runs) {
                    if (r.size < 2) continue
                    val line = Path().apply { moveTo(r[0].x, r[0].y); r.drop(1).forEach { lineTo(it.x, it.y) } }
                    val fill = Path().apply { addPath(line); lineTo(r.last().x, size.height); lineTo(r.first().x, size.height); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
                    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (at != null) {
                    val x = at * stepX
                    drawLine(Lab.sub, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    points[at]?.let { drawCircle(color, 4.dp.toPx(), Offset(x, y(it))) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("min" to values.min(), "avg" to values.average(), "max" to values.max()).forEach { (k, v) ->
                Text("$k ${m.show(v)}", fontSize = 12.sp, color = Lab.muted, modifier = Modifier.weight(1f))
            }
        }
    }
}
