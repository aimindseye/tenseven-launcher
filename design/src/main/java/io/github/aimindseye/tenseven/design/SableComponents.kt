package io.github.aimindseye.tenseven.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen padding: the design's 24/20 dp, or 12/8 dp in a short window so content gets the room. */
@Composable
fun Modifier.sableScreenPadding(): Modifier =
    if (isSableShortWindow()) {
        padding(horizontal = SableSpacing.ScreenHorizontalShort, vertical = SableSpacing.ScreenVerticalShort)
    } else {
        padding(horizontal = SableSpacing.ScreenHorizontal, vertical = SableSpacing.ScreenVertical)
    }

/** Inner padding of panels and tiles: 16 dp, or 12 dp in a short window. */
@Composable
fun sablePanelPadding(): Dp = if (isSableShortWindow()) SableSpacing.Md else SableSpacing.Lg

/** Gap between a screen's blocks: 16 dp, or 8 dp in a short window. */
@Composable
fun sableScreenGap(): Dp = if (isSableShortWindow()) SableSpacing.Sm else SableSpacing.Lg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SableRefreshableSurface(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}
