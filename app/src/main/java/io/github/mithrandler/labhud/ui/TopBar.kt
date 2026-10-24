package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * What a screen with a [LabTopBar] scrolls: nothing. The bar is the small one at a fixed height; a pinned behavior never
 * takes a scroll from the list (an exit-until-collapsed one, with no bar to measure, swallowed every swipe up).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLabBarScroll(): TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

/** Kept for the Scaffold of each screen: it adds no nested scroll, so the list under the bar gets every gesture. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
fun Modifier.labBarScroll(scroll: TopAppBarScrollBehavior): Modifier = this

/**
 * The top bar of every screen: the small one, the same height everywhere (the large Material one that
 * folded on scroll took a third of the screen before the first row). [scroll] stays for the callers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabTopBar(
    title: @Composable () -> Unit,
    scroll: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title, navigationIcon = navigationIcon, actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
    )
}
