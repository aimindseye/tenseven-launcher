package io.github.aimindseye.tenseven.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * The page and hero header in a short window: one line, eyebrow then title, and the subtitle (when
 * there is one) as one muted line, so the screen's content starts right under it.
 */
@Composable
private fun SableShortHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
