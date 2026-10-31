package io.github.aimindseye.tenseven

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.DesignText
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
            LauncherTheme(window = window) {
                LauncherSettings(
                    state = state,
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
    state: StartStateRepository,
    onLanguage: () -> Unit,
    onBackup: () -> Unit,
    onSetup: () -> Unit,
    onAndroidSettings: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var typeToSearch by remember { mutableStateOf(state.loadTypeToSearch()) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.launcher_settings), style = MaterialTheme.typography.titleLarge)

            ProvideHomeStyle(state) {
            Section(stringResource(R.string.ls_section_style))
            HomeStyleSection(state, first)

            Section(stringResource(R.string.ls_section_home))
            SwitchRow(stringResource(R.string.ls_type_to_search), stringResource(R.string.ls_type_to_search_detail), typeToSearch) {
                typeToSearch = it
                state.saveTypeToSearch(it)
            }

            Section(stringResource(R.string.ls_section_grid))
            IconGridSection(state)

            Section(stringResource(R.string.ls_section_theme))
            ThemeSection(state)

            Section(stringResource(R.string.ls_section_wallpaper))
            WallpaperSection(state)

            Section(stringResource(R.string.ls_section_widgets))
            WidgetsSection(state)

            Section(stringResource(R.string.ls_section_hub))
            HubSection()

            Section(stringResource(R.string.ls_section_lock))
            LockLayerSection(state)

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
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
