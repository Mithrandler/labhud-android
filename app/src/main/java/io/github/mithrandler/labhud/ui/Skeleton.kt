package io.github.mithrandler.labhud.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A grey block that stands where something is still loading: it breathes, or just sits there when the system has animations off. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = CardShape) {
    val alpha = if (rememberAnimationsOn()) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha").value
    } else 0.5f
    Box(modifier.clip(shape).background(Lab.block.copy(alpha = alpha)))
}

/** A card's screen while it loads: a header block and a bigger one. */
@Composable
fun SkeletonPanel(modifier: Modifier = Modifier, height: Dp = 96.dp) {
    SkeletonBlock(modifier.fillMaxWidth().height(height))
}

/** Status while it has nothing to show yet: two rows of tiles, as many to a row as the page will have. */
@Composable
fun SkeletonTiles(cols: Int = 3, rows: Int = 2, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(cols) { SkeletonBlock(Modifier.weight(1f).height(LocalTileH.current)) }
            }
        }
    }
}
