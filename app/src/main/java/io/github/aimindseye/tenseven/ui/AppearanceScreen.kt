package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.AccentPreset
import io.github.aimindseye.tenseven.design.AppearanceMode
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.iconpacks.IconPackLoader
import io.github.aimindseye.tenseven.iconpacks.IconPacks
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.platform.Capabilities
import io.github.aimindseye.tenseven.platform.LockScreenAccessibilityService
import io.github.aimindseye.tenseven.platform.StartStateRepository
import java.util.Locale

@Composable
internal fun AppearanceScreen(
    appearance: SableAppearance,
    onBack: () -> Unit,
    onAppearanceChanged: (SableAppearance) -> Unit,
    onReset: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MetroHeader(
                title = "appearance",
                onBack = onBack,
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("theme")
            Spacer(Modifier.height(4.dp))
        }

        items(AppearanceMode.entries) { mode ->
            val selected = appearance.mode == mode
            AppearanceChoiceRow(
                title =
                    when (mode) {
                        AppearanceMode.FollowSystem -> t("follow_system")
                        AppearanceMode.Light -> "light"
                        AppearanceMode.Dark -> "dark"
                    },
                selected = selected,
                accent = appearance.accent.color,
                onClick = {
                    onAppearanceChanged(
                        appearance.copy(mode = mode),
                    )
                },
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SectionLabel("accent")
            Spacer(Modifier.height(4.dp))
        }

        items(AccentPreset.entries) { accent ->
            val selected = appearance.accent == accent
            AccentChoiceRow(
                accent = accent,
                selected = selected,
                onClick = {
                    onAppearanceChanged(
                        appearance.copy(accent = accent),
                    )
                },
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SectionLabel(stringResource(R.string.icon_pack_label))
            Spacer(Modifier.height(4.dp))
        }

        item {
            val context = LocalContext.current
            val packs = remember { IconPackLoader.installed(context) }
            IconPacks.init(context)
            AppearanceChoiceRow(
                title = stringResource(R.string.icon_pack_default),
                selected = IconPacks.selected == null,
                accent = appearance.accent.color,
                onClick = { IconPacks.select(context, null) },
            )
            packs.forEach { pack ->
                AppearanceChoiceRow(
                    title = pack.label,
                    selected = IconPacks.selected == pack.packageName,
                    accent = appearance.accent.color,
                    onClick = { IconPacks.select(context, pack.packageName) },
                )
            }
            if (packs.isEmpty()) {
                Text(
                    text = stringResource(R.string.icon_pack_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            val context = LocalContext.current
            // The lite build has no accessibility service, so the double-tap lock switch is not offered.
            if (!Capabilities.hasLockService(context)) return@item
            Spacer(Modifier.height(14.dp))
            SectionLabel(stringResource(R.string.lock_label))
            Spacer(Modifier.height(4.dp))
            var enabled by remember { mutableStateOf(StartStateRepository(context).loadDoubleTapLock()) }
            AppearanceChoiceRow(
                title = stringResource(if (enabled) R.string.lock_on else R.string.lock_off),
                selected = enabled,
                accent = appearance.accent.color,
                onClick = {
                    enabled = !enabled
                    StartStateRepository(context).saveDoubleTapLock(enabled)
                    if (enabled && !LockScreenAccessibilityService.isEnabled(context)) {
                        // The service is off until the user turns it on in Accessibility settings.
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
            )
            Text(
                text = stringResource(R.string.lock_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            Text(
                text = t("reset_follow_system_sable_blue"),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .sableFocusRing()
                        .clickable(onClick = onReset)
                        .padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun AppearanceChoiceRow(
    title: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(
                        if (selected) {
                            accent
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight =
                if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
        )
        if (selected) {
            Text(
                text = "✓",
                fontSize = 20.sp,
                color = accent,
            )
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
internal fun AccentChoiceRow(
    accent: AccentPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .background(accent.color),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = accent.displayName.lowercase(Locale.getDefault()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight =
                if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
        )
        if (selected) {
            Text(
                text = "✓",
                fontSize = 20.sp,
                color = accent.color,
            )
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
