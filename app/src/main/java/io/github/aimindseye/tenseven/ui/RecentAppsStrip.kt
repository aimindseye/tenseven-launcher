package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey

private const val RECENT_ICON_SIZE = 40
private val RECENT_FOCUS = Color(0x3D2D7FF9)

/**
 * A row of recently used apps, icons only, shown on Active Frames when Android does not give a home screen the
 * recent tasks (an ordinary install). Without usage access it offers to turn it on instead.
 */
@Composable
internal fun RecentAppsStrip(
    packages: List<String>,
    apps: List<AppEntry>,
    usageAccess: Boolean,
    onLaunchApp: (AppEntry) -> Unit,
    onAllowUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val byPackage = remember(apps) { apps.filter { it.inLauncherUser }.associateBy { it.component.packageName } }
    val shown = remember(packages, byPackage) { packages.mapNotNull(byPackage::get) }
    if (!usageAccess) {
        Text(
            text = stringResource(R.string.recents_allow),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .sableFocusRing()
                    .clickable(onClick = onAllowUsageAccess)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    if (shown.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.recents_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(shown, key = { it.stableKey() }) { app ->
                var focused by remember { mutableStateOf(false) }
                val label = app.label
                androidx.compose.foundation.layout.Box(
                    modifier =
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (focused) RECENT_FOCUS else Color.Transparent)
                            .sableFocusRing()
                            .clickable { onLaunchApp(app) }
                            .padding(6.dp)
                            .semantics { contentDescription = label },
                ) {
                    AppMark(app = app, size = RECENT_ICON_SIZE)
                }
            }
        }
    }
}
