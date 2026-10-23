package io.github.mithrandler.labhud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
        // the whole cell takes the tap (a big target) but shows nothing: only the pill round the icon answers a touch
        val touch = remember { MutableInteractionSource() }
        val pressed by touch.collectIsPressedAsState()
        val fill by animateColorAsState(
            when {
                pressed -> Lab.accent.copy(alpha = if (on) 0.32f else 0.18f)
                on -> Lab.accent.copy(alpha = 0.22f)
                else -> Color.Transparent
            }, tween(90), label = "tab-fill",
        )
        Box(modifier.selectable(selected = on, role = Role.Tab, interactionSource = touch, indication = null, onClick = { onPick(route) }), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(fill)
                .size(width = LabScale.touch, height = LabScale.space8), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (on || pressed) Lab.title else Lab.muted, modifier = Modifier.size(LabScale.iconCard))
            }
        }
    }
    if (vertical) Column(
        Modifier.fillMaxHeight().background(Lab.card).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)).width(LabScale.navW),
        verticalArrangement = Arrangement.spacedBy(LabScale.space2, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) { tabs.forEach { (r, l, i) -> item(r, l, i, Modifier.size(width = LabScale.navW, height = LabScale.navH)) } }
    else Row(
        Modifier.fillMaxWidth().background(Lab.card).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).height(LabScale.navH),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) { tabs.forEach { (r, l, i) -> item(r, l, i, Modifier.weight(1f).height(LabScale.navH)) } }
}
