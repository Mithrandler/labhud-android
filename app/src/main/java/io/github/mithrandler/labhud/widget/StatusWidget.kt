package io.github.mithrandler.labhud.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.mithrandler.labhud.LabHudApp
import io.github.mithrandler.labhud.MainActivity
import io.github.mithrandler.labhud.data.LabState
import io.github.mithrandler.labhud.ui.Hud
import java.text.DateFormat
import java.util.Date

/**
 * The home screen widget: a dot, "All good" or "N problems", and (when there is room) the first
 * problems by name. It reads the state the app kept last; RefreshWorker fetches a new one every
 * 15 minutes (Android's shortest) while a widget is placed.
 */
class StatusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 48.dp), DpSize(180.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as LabHudApp).repo
        val server = repo.store.currentNow()
        val state = server?.let { repo.cached(it.id) }
        provideContent { Content(server?.name ?: "LabHUD", state) }
    }

    @Composable
    private fun Content(name: String, st: LabState?) {
        val n = st?.problems?.size ?: -1
        val color = when { n < 0 -> Hud.border; n == 0 -> Hud.green; else -> Hud.red }
        val big = LocalSize.current.height >= 100.dp
        Column(
            GlanceModifier.fillMaxSize().background(Hud.card).cornerRadius(16.dp).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(10.dp).background(color).cornerRadius(5.dp)) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(name, style = TextStyle(color = ColorProvider(Hud.title), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                when { n < 0 -> "Open the app"; n == 0 -> "All good"; n == 1 -> "1 problem"; else -> "$n problems" },
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1,
            )
            if (big && st != null) {
                val byId = st.byId
                st.problems.take(3).mapNotNull { byId[it] }.forEach {
                    Text("· " + it.name, style = TextStyle(color = ColorProvider(Hud.red), fontSize = 13.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.defaultWeight())
                Text("as of " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(st.now * 1000)),
                    style = TextStyle(color = ColorProvider(Hud.muted), fontSize = 11.sp))
            }
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}
