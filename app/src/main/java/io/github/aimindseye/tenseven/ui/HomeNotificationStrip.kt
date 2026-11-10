package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.live.AppUnreadCount
import io.github.aimindseye.tenseven.model.HubBar

/**
 * Shared BB7/BB10 Home notification strip. The listener's permission-scoped,
 * explicitly opted-in app counts are the same source as Hub and the lock layer.
 * This is counts only: no message text appears on the Home surface.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeNotificationStrip(
    counts: List<AppUnreadCount>,
    enabledPackages: Set<String>,
    expanded: Boolean,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenApp: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val items =
        remember(counts, enabledPackages) {
            HubBar.items(counts.filter { it.packageName in enabledPackages }) { true }
        }
    if (items.isEmpty()) return
    val shown = if (expanded) items else items.take(4)
    val hidden = (items.size - shown.size).coerceAtLeast(0)
    val icons: @Composable () -> Unit = {
        shown.forEach { item ->
            val icon =
                remember(item.packageName) {
                    runCatching { pm.getApplicationIcon(item.packageName).toBitmap(72, 72).asImageBitmap() }.getOrNull()
                }
            val label =
                remember(item.packageName) {
                    runCatching {
                        pm
                            .getApplicationLabel(
                                pm.getApplicationInfo(item.packageName, 0),
                            ).toString()
                    }.getOrDefault(item.packageName)
                }
            Row(
                modifier =
                    Modifier
                        .testTag("home-notification-${item.packageName}")
                        .sableFocusRing()
                        .clickable {
                            if (onOpenApp != null) {
                                onOpenApp(item.packageName)
                            } else {
                                runCatching {
                                    pm.getLaunchIntentForPackage(item.packageName)?.let { context.startActivity(it) }
                                }
                            }
                        }.semantics { contentDescription = "Open $label: ${item.count} unread" }
                        .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (icon != null) {
                    Image(
                        icon,
                        contentDescription = item.packageName,
                        modifier = Modifier.size(23.dp).clip(RoundedCornerShape(5.dp)),
                    )
                } else {
                    Text("•", color = Color.White)
                }
                Text(
                    item.count.coerceAtMost(HubBar.MAX_COUNT).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
        if (hidden > 0) {
            Text(
                "+$hidden",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier =
                    Modifier
                        .sableFocusRing()
                        .clickable(onClick = onOpenHub)
                        .semantics { contentDescription = "$hidden more apps. Open Hub" }
                        .padding(vertical = 5.dp),
            )
        }
    }
    if (expanded) {
        FlowRow(
            modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            content = { icons() },
        )
    } else {
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            content = { icons() },
        )
    }
}
