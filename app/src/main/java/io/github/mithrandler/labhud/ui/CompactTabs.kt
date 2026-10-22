package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp

/**
 * The places of the app as icons only (no labels), thin: 44 dp high along the bottom, or 52 dp wide in a
 * column at the side when the screen is wide, so the height stays the cards'. The one you are on has a pill.
 */
@Composable
fun CompactTabs(tabs: List<Triple<String, String, ImageVector>>, current: String?, vertical: Boolean, onPick: (String) -> Unit) {
    @Composable fun item(route: String, label: String, icon: ImageVector, modifier: Modifier) {
        val on = route == current
        Box(modifier.selectable(selected = on, role = Role.Tab, onClick = { onPick(route) }), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) Lab.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                .size(width = 48.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (on) Lab.title else Lab.muted, modifier = Modifier.size(22.dp))
            }
        }
    }
    if (vertical) Column(
        Modifier.fillMaxHeight().background(Lab.card).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)).width(56.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) { tabs.forEach { (r, l, i) -> item(r, l, i, Modifier.size(width = 56.dp, height = 44.dp)) } }
    else Row(
        Modifier.fillMaxWidth().background(Lab.card).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).height(44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) { tabs.forEach { (r, l, i) -> item(r, l, i, Modifier.weight(1f).height(44.dp)) } }
}
