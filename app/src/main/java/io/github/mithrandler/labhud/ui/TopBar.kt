package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** What a screen with a [LabTopBar] scrolls: the large bar folds into the small one as the list goes up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLabBarScroll(): TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/** For the Scaffold of a screen with a [LabTopBar]. */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.labBarScroll(scroll: TopAppBarScrollBehavior): Modifier = nestedScroll(scroll.nestedScrollConnection)

/**
 * The top bar of a list screen: Material's large one in a Material theme, the HUD's small one on
 * the wall's black, as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabTopBar(
    title: @Composable () -> Unit,
    scroll: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (Lab.hud) TopAppBar(
        title = title, navigationIcon = navigationIcon, actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
    ) else LargeTopAppBar(
        title = title, navigationIcon = navigationIcon, actions = actions, scrollBehavior = scroll,
        colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Lab.bg, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}
