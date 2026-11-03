package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.design.SableAlphabetIndex
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.CoreAppIdentity
import java.util.Locale

internal fun findLiveApp(
    apps: List<AppEntry>,
    packageName: String,
): AppEntry? =
    apps.firstOrNull { CoreAppIdentity.canonicalPackage(it.component.packageName) == packageName }
        ?: when (packageName) {
            MAIL_PACKAGE ->
                apps.firstOrNull { it.label.equals(MAIL_LABEL, ignoreCase = true) }
            else -> null
        }

@Composable
internal fun MetroHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .width(48.dp),
        ) {
            Text(
                text = "‹",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun MetroLink(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
internal fun ContextAction(
    mark: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mark, fontSize = 22.sp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun EmptyState(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 18.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun buildRailRows(apps: List<AppEntry>): List<AppRailRow> {
    val locale = Locale.getDefault()
    val sorted =
        apps.sortedWith(
            compareBy<AppEntry> { it.label.lowercase(locale) }
                .thenBy { it.component.packageName }
                .thenBy { it.component.className },
        )

    return sorted.mapIndexed { index, app ->
        val section = SableAlphabetIndex.keyFor(app.label, locale)
        AppRailRow(
            app = app,
            section = section,
            startsSection =
                index == 0 ||
                    SableAlphabetIndex.keyFor(sorted[index - 1].label, locale) != section,
        )
    }
}

/**
 * Home apps before the user orders them: the screen design's list (Messages,
 * Mail, Calendar, Contacts, Phone, Camera), each the first installed app of
 * its group in the launcher's own profile.
 */
