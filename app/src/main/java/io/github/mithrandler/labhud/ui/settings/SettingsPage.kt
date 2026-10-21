package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.R
import io.github.mithrandler.labhud.ui.CardShape
import io.github.mithrandler.labhud.ui.Heading
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabIcons

/**
 * One settings section on its own screen: Back, the section's icon and title, then its content
 * in a column of panels, 16 dp apart from the edges, like the list it came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(section: Section, nav: SettingsNav, content: LazyListScope.() -> Unit) {
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = nav.back) { Icon(LabIcons.back, "Back") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(section.icon(), null, tint = Lab.title, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(section.title)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** A small heading inside a section, like the wall's card titles. */
fun LazyListScope.heading(text: String) = item { Text(text.uppercase(), style = Heading, modifier = Modifier.padding(top = 8.dp)) }

/**
 * What only the wall's own screen can do (the config editor, drag and drop, the network scan):
 * the phone says so and where, instead of a half-working copy.
 */
@Composable
fun WallOnly(where: String, what: List<String>) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LabIcons.wall, null, tint = Lab.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Done on the LabHUD screen", fontWeight = FontWeight.Medium)
            }
            Text("On the wall or in a browser: $where.", fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            what.forEach { Text("·  $it", fontSize = 13.sp, color = Lab.sub, modifier = Modifier.padding(vertical = 1.dp)) }
        }
    }
}

/** labhud's mark (static/brand/mark.svg): three cards with status dots. */
@Composable
fun BrandMark(height: Dp = 22.dp) {
    Image(painterResource(R.drawable.brand_mark), "LabHUD", Modifier.height(height).width(height * 304f / 256f))
}

/** The few icons only the settings need, drawn like LabIcons (thin lines, 24×24). */
object SettingsIcons {
    val container: ImageVector by lazy { icon("container", "M12 3l8 4.5v9L12 21l-8-4.5v-9z", "M4 7.5l8 4.5 8-4.5M12 12v9") }
    val phone: ImageVector by lazy { icon("phone", "M8.5 3H15.5A1.5 1.5 0 0 1 17 4.5V19.5A1.5 1.5 0 0 1 15.5 21H8.5A1.5 1.5 0 0 1 7 19.5V4.5A1.5 1.5 0 0 1 8.5 3Z", "M11 18h2") }
    val key: ImageVector by lazy { icon("key", "M5 15a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", "M12 12l8-8M17 7l2 2M15 9l2 2") }
    val server: ImageVector by lazy { icon("server", "M4.5 4H19.5A1.5 1.5 0 0 1 21 5.5V9.5A1.5 1.5 0 0 1 19.5 11H4.5A1.5 1.5 0 0 1 3 9.5V5.5A1.5 1.5 0 0 1 4.5 4Z", "M4.5 13H19.5A1.5 1.5 0 0 1 21 14.5V18.5A1.5 1.5 0 0 1 19.5 20H4.5A1.5 1.5 0 0 1 3 18.5V14.5A1.5 1.5 0 0 1 4.5 13Z", "M7 7.5h.01M7 16.5h.01") }

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) addPath(
                pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
