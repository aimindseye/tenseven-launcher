package io.github.aimindseye.tenseven

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.SableGlobalTheme
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.SettingsWidgets
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * Launcher Settings: one screen for the choices people reach for most, laid out like the settings of a
 * BlackBerry-style launcher: the home screen style as two cards (BlackBerry 7 or 10), then Home, Widgets,
 * Device and Privacy. Every change is saved at once; the home screens read it when they come forward.
 */
class LauncherSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        val state = StartStateRepository(this)
        setContent {
            SableGlobalTheme(window = window) {
                var style by remember { mutableStateOf(state.loadHomeStyle()) }
                var typeToSearch by remember { mutableStateOf(state.loadTypeToSearch()) }
                var iconSize by remember { mutableStateOf(state.loadBb7IconSize()) }
                var widgets by remember { mutableStateOf(GlanceWidgets.normalize(state.loadGlanceWidgets())) }
                LauncherSettings(
                    style = style,
                    typeToSearch = typeToSearch,
                    iconSize = iconSize,
                    widgets = widgets,
                    onStyle = {
                        style = it
                        state.saveHomeStyle(it)
                    },
                    onTypeToSearch = {
                        typeToSearch = it
                        state.saveTypeToSearch(it)
                    },
                    onIconSize = {
                        iconSize = it
                        state.saveBb7IconSize(it)
                    },
                    onWidget = { widget ->
                        widgets = GlanceWidgets.toggle(widgets, widget)
                        state.saveGlanceWidgets(widgets)
                    },
                    onLanguage = {
                        val intent =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(android.net.Uri.parse("package:$packageName"))
                            } else {
                                Intent(Settings.ACTION_LOCALE_SETTINGS)
                            }
                        runCatching { startActivity(intent) }
                    },
                    onBackup = { runCatching { startActivity(Intent(this, BackupActivity::class.java)) } },
                    onSetup = { runCatching { startActivity(Intent(this, OnboardingActivity::class.java)) } },
                    onAndroidSettings = { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } },
                )
            }
        }
    }
}

@Composable
private fun LauncherSettings(
    style: HomeStyle,
    typeToSearch: Boolean,
    iconSize: Bb7IconSize,
    widgets: List<String>,
    onStyle: (HomeStyle) -> Unit,
    onTypeToSearch: (Boolean) -> Unit,
    onIconSize: (Bb7IconSize) -> Unit,
    onWidget: (String) -> Unit,
    onLanguage: () -> Unit,
    onBackup: () -> Unit,
    onSetup: () -> Unit,
    onAndroidSettings: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.launcher_settings), style = MaterialTheme.typography.titleLarge)

            Section(stringResource(R.string.ls_section_style))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleCard(
                    title = stringResource(R.string.ls_bb7_title),
                    detail = stringResource(R.string.ls_bb7_detail),
                    active = style == HomeStyle.Bb7,
                    modifier = Modifier.weight(1f).focusRequester(first),
                ) { onStyle(HomeStyle.Bb7) }
                StyleCard(
                    title = stringResource(R.string.ls_bb10_title),
                    detail = stringResource(R.string.ls_bb10_detail),
                    active = style == HomeStyle.Bb10,
                    modifier = Modifier.weight(1f),
                ) { onStyle(HomeStyle.Bb10) }
            }

            Section(stringResource(R.string.ls_section_home))
            SwitchRow(
                stringResource(R.string.ls_type_to_search),
                stringResource(R.string.ls_type_to_search_detail),
                typeToSearch,
                onTypeToSearch,
            )
            Text(stringResource(R.string.ls_icon_size), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bb7IconSize.entries.forEach { size ->
                    Card(
                        title = size.title,
                        detail = "",
                        active = size == iconSize,
                        modifier = Modifier.weight(1f),
                    ) { onIconSize(size) }
                }
            }

            Section(stringResource(R.string.ls_section_widgets))
            SettingsWidgets.TOGGLEABLE.forEach { widget ->
                val (title, detail) =
                    when (widget) {
                        GlanceWidgets.CALENDAR -> R.string.ls_widget_calendar to R.string.ls_widget_calendar_detail
                        GlanceWidgets.ALARM -> R.string.ls_widget_alarm to R.string.ls_widget_alarm_detail
                        else -> R.string.ls_widget_media to R.string.ls_widget_media_detail
                    }
                SwitchRow(stringResource(title), stringResource(detail), widget in widgets) { onWidget(widget) }
            }

            Section(stringResource(R.string.ls_section_device))
            Card(stringResource(R.string.ls_language), stringResource(R.string.ls_language_detail), false, onClick = onLanguage)
            Card(stringResource(R.string.ls_backup), stringResource(R.string.ls_backup_detail), false, onClick = onBackup)
            Card(stringResource(R.string.ls_setup), stringResource(R.string.ls_setup_detail), false, onClick = onSetup)
            Card(
                stringResource(R.string.ls_android_settings),
                stringResource(R.string.ls_android_settings_detail),
                false,
                onClick = onAndroidSettings,
            )

            Section(stringResource(R.string.ls_section_privacy))
            Text(
                stringResource(R.string.ls_privacy_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StyleCard(
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = Card(title + if (active) " · " + stringResource(R.string.ls_current) else "", detail, active, modifier, onClick)

@Composable
private fun Card(
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border =
            BorderStroke(
                if (active) 2.dp else 1.dp,
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
