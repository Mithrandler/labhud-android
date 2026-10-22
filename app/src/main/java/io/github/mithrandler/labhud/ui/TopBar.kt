package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
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
