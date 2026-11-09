package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.conversations.ConversationFilter
import io.github.aimindseye.tenseven.conversations.ConversationKind
import io.github.aimindseye.tenseven.model.HubFilter

/** Hub's own three-action navigation: never display the shared Home dock inside Hub. */
@Composable
internal fun HubCompactBar(
    onViews: () -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Bb10Background).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HubCompactControl("Views", listOf("M4 6h16", "M4 12h16", "M4 18h16"), Modifier.weight(1f), onViews)
        HubCompactControl(
            "Compose",
            listOf("M12 5h-7v14h14v-7", "M9 15l2 -5 8 -8 3 3 -8 8z"),
            Modifier.weight(1f),
            onCompose,
        )
        HubCompactControl("Search", LineIconPaths.SEARCH, Modifier.weight(1f), onSearch)
    }
}

@Composable
private fun HubCompactControl(
    label: String,
    paths: List<String>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.heightIn(min = 52.dp).clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LineIcon(paths, Modifier.size(22.dp), Bb10Text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Bb10Muted)
    }
}

/** A flat list of the extra Hub views; none is mistaken for a new notification source. */
@Composable
internal fun HubCompactViews(
    ui: HubConversationsUi,
    onTabChange: (HubFilter) -> Unit,
    onCalendar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = listOf("All", "Unread", "Messages", "Email", "Snoozed", "Remind", "Calls", "Contacts", "Calendar")
    Column(
        Modifier.fillMaxWidth().heightIn(max = 176.dp)
            .background(Bb10Background).verticalScroll(rememberScrollState()),
    ) {
        choices.chunked(3).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                group.forEach { choice ->
                    Text(
                        choice,
                        modifier = Modifier.weight(1f).clickable {
                            when (choice) {
                                "Calendar" -> onCalendar()
                                "Remind" -> {
                                    onTabChange(HubFilter.All)
                                    ui.onShowReminders()
                                }
                                else -> {
                                    val next = when (choice) {
                                        "Messages" -> ConversationFilter(kind = ConversationKind.Messages)
                                        "Email" -> ConversationFilter(kind = ConversationKind.Email)
                                        "Unread" -> ConversationFilter(unreadOnly = true)
                                        "Snoozed" -> ConversationFilter(snoozed = true)
                                        else -> ConversationFilter()
                                    }
                                    onTabChange(
                                        when (choice) {
                                            "Calls" -> HubFilter.Calls
                                            "Contacts" -> HubFilter.Contacts
                                            "Messages" -> HubFilter.Messages
                                            else -> HubFilter.All
                                        },
                                    )
                                    ui.onFilterChange(next)
                                    ui.onSourceChange(null)
                                }
                            }
                            onDismiss()
                        }.padding(horizontal = 8.dp, vertical = 10.dp),
                        color = Bb10Text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
