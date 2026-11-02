package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
@Composable
internal fun HomeNotificationStrip(
    counts: List<AppUnreadCount>,
    enabledPackages: Set<String>,
    expanded: Boolean,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val items = remember(counts, enabledPackages) {
        HubBar.items(counts.filter { it.packageName in enabledPackages }) { true }
    }
    if (items.isEmpty()) return
    val shown = if (expanded) items else items.take(4)
    val hidden = (items.size - shown.size).coerceAtLeast(0)
    Row(
        modifier = modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .sableFocusRing()
            .clickable(onClick = onOpenHub)
            .semantics { contentDescription = "Notifications: " + items.sumOf { it.count } + " unread. Open Hub" }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        shown.forEach { item ->
            val icon = remember(item.packageName) {
                runCatching { pm.getApplicationIcon(item.packageName).toBitmap(72, 72).asImageBitmap() }.getOrNull()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (icon != null) {
                    Image(icon, contentDescription = item.packageName,
                        modifier = Modifier.size(23.dp).clip(RoundedCornerShape(5.dp)))
                } else {
                    Text("•", color = Color.White)
                }
                Text(item.count.coerceAtMost(HubBar.MAX_COUNT).toString(),
                    style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
        if (hidden > 0) Text("+$hidden", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
