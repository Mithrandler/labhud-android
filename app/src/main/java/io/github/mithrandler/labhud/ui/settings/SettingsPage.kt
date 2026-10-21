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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.R
import io.github.mithrandler.labhud.ui.LabTopBar
import io.github.mithrandler.labhud.ui.labBarScroll
import io.github.mithrandler.labhud.ui.rememberLabBarScroll
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
    val barScroll = rememberLabBarScroll()
    Scaffold(
        modifier = Modifier.labBarScroll(barScroll),
        containerColor = Lab.bg,
        topBar = {
            LabTopBar(
                scroll = barScroll,
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
