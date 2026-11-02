package io.github.aimindseye.tenseven.ui

import android.app.KeyguardManager
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.platform.HubCalendarEvent
import io.github.aimindseye.tenseven.platform.HubCalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opt-in, borderless calendar peek. Reads only the on-device Android CalendarProvider. */
@Composable
internal fun HubCalendarPeek(
    refreshGeneration: Int,
    onRequestCalendarPermission: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { HubCalendarRepository(context) }
    val permitted = repository.hasPermission()
    val deviceLocked = context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
    var expanded by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(emptyList<HubCalendarEvent>()) }

    LaunchedEffect(expanded, permitted, deviceLocked, refreshGeneration) {
        if (expanded && permitted && !deviceLocked) {
            events = withContext(Dispatchers.IO) { repository.upcoming() }
        } else {
            events = emptyList()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (expanded) "▴ Upcoming calendar" else "▾ Upcoming calendar",
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        if (expanded) {
            if (deviceLocked) {
                Text("Unlock to see upcoming calendar events",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            } else if (!permitted) {
                Text("Allow Calendar access to see events from calendars synced to Android",
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onRequestCalendarPermission).padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            } else if (events.isEmpty()) {
                Text("No upcoming events in the next seven days",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                events.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCalendar).padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(event.calendarName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            DateUtils.getRelativeTimeSpanString(event.beginsAt,
                                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
