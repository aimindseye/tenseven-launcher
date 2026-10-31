package io.github.aimindseye.tenseven

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.Bb7Home
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.SettingsWidgets
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.model.ThemePreset
import io.github.aimindseye.tenseven.model.ThemePresets
import io.github.aimindseye.tenseven.model.WallpaperSet
import io.github.aimindseye.tenseven.model.WallpaperSets
import io.github.aimindseye.tenseven.platform.LockLayerLauncher
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.platform.WallpaperArt
import io.github.aimindseye.tenseven.platform.WallpaperTarget

/**
 * Launcher Settings: one screen for the choices people reach for most, laid out like the settings of a
 * BlackBerry-style launcher: the home screen style as two cards (BlackBerry 7 or 10), then Home, Widgets,
 * Device and Privacy. Every change is saved at once; the home screens read it when they come forward.
 */
class LauncherSettingsActivity : ComponentActivity() {
    /** Bumped on resume so the permission rows re-check after the user comes back from Android's settings. */
    private var resumeTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        val state = StartStateRepository(this)
        setContent {
            LauncherTheme(window = window) {
                var style by remember { mutableStateOf(state.loadHomeStyle()) }
                var typeToSearch by remember { mutableStateOf(state.loadTypeToSearch()) }
                var iconSize by remember { mutableStateOf(state.loadBb7IconSize()) }
                var gridColumns by remember { mutableStateOf(state.loadGridColumns()) }
                var rows by remember { mutableStateOf(state.loadBb7HomeRows()) }
                var labels by remember { mutableStateOf(state.loadGridLabels()) }
                var themeId by remember {
                    mutableStateOf(ThemePresets.matching(state.loadAppearanceMode(), state.loadAppearanceAccent()).id)
                }
                var wallpaperId by remember { mutableStateOf(ThemePresets.byId(themeId).wallpaperId) }
                var wallpaperStatus by remember { mutableStateOf<Int?>(null) }
                var lockLayer by remember { mutableStateOf(state.loadLockLayer()) }
                                var widgetsPage by remember { mutableStateOf(state.loadWidgetsPage()) }
                var widgets by remember { mutableStateOf(GlanceWidgets.normalize(state.loadGlanceWidgets())) }
                LauncherSettings(
                    style = style,
                    typeToSearch = typeToSearch,
                    iconSize = iconSize,
                    widgets = widgets,
                    widgetsPage = widgetsPage,
                    themeId = themeId,
                    wallpaperId = wallpaperId,
                    wallpaperStatus = wallpaperStatus,
                    onTheme = { preset ->
                        themeId = preset.id
                        wallpaperId = preset.wallpaperId
                        if (preset.id == ThemePresets.DEFAULT_ID) {
                            state.saveAppearance(null, null)
                        } else {
                            state.saveAppearance(preset.mode.stableValue, preset.accent.stableValue)
                        }
                    },
                    onWallpaper = { wallpaperId = it },
                    onApplyWallpaper = { target ->
                        wallpaperStatus = R.string.ls_wallpaper_working
                        val set = WallpaperSets.byId(wallpaperId)
                        Thread {
                            val ok = WallpaperArt.apply(applicationContext, set, target)
                            runOnUiThread {
                                wallpaperStatus = if (ok) R.string.ls_wallpaper_done else R.string.ls_wallpaper_failed
                            }
                        }.start()
                    },
                    lockLayer = lockLayer,
                    overlayAllowed = resumeTick >= 0 && LockLayerLauncher.canDrawOverlays(this),
                    accessAllowed = resumeTick >= 0 && ConversationAccess.isGranted(this),
                    onLockLayer = {
                        lockLayer = it
                        state.saveLockLayer(it)
                    },
                    onOverlay = {
                        runCatching {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")),
                            )
                        }
                    },
                    onAccess = { runCatching { startActivity(ConversationAccess.settingsIntent()) } },
                    onPreviewLock = { runCatching { startActivity(Intent(this, LockLayerActivity::class.java)) } },
                    onHub = { runCatching { startActivity(Intent(this, HubSettingsActivity::class.java)) } },
                    gridColumns = gridColumns,
                    rows = rows,
                    labels = labels,
                    onGridColumns = {
                        gridColumns = it
                        state.saveGridColumns(it)
                    },
                    onRows = {
                        rows = it
                        state.saveBb7HomeRows(it)
                    },
                    onLabels = {
                        labels = it
                        state.saveGridLabels(it)
                    },
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
                    onWidgetsPage = {
                        widgetsPage = it
                        state.saveWidgetsPage(it)
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
    widgetsPage: Boolean,
    themeId: String,
    wallpaperId: String,
    wallpaperStatus: Int?,
    onTheme: (ThemePreset) -> Unit,
    onWallpaper: (String) -> Unit,
    onApplyWallpaper: (WallpaperTarget) -> Unit,
    lockLayer: Boolean,
    overlayAllowed: Boolean,
    accessAllowed: Boolean,
    onLockLayer: (Boolean) -> Unit,
    onHub: () -> Unit,
    onPreviewLock: () -> Unit,
    onOverlay: () -> Unit,
    onAccess: () -> Unit,
    gridColumns: Int,
    rows: Int,
    labels: Boolean,
    onGridColumns: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onLabels: (Boolean) -> Unit,
    onStyle: (HomeStyle) -> Unit,
    onTypeToSearch: (Boolean) -> Unit,
    onIconSize: (Bb7IconSize) -> Unit,
    onWidgetsPage: (Boolean) -> Unit,
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

            Section(stringResource(R.string.ls_section_grid))
            IconGridPreview(columns = gridColumns, rows = rows, iconSize = iconSize, labels = labels)
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
            Text(stringResource(R.string.ls_columns), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GridColumns.OPTIONS.forEach { option ->
                    Card(
                        title = if (option == GridColumns.AUTO) stringResource(R.string.ls_auto) else option.toString(),
                        detail = "",
                        active = option == gridColumns,
                        modifier = Modifier.weight(1f),
                    ) { onGridColumns(option) }
                }
            }
            Text(stringResource(R.string.ls_rows), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (Bb7Home.MIN_HOME_ROWS..Bb7Home.MAX_HOME_ROWS).forEach { option ->
                    Card(
                        title = option.toString(),
                        detail = "",
                        active = option == rows,
                        modifier = Modifier.weight(1f),
                    ) { onRows(option) }
                }
            }
            SwitchRow(stringResource(R.string.ls_labels), stringResource(R.string.ls_labels_detail), labels, onLabels)

            Section(stringResource(R.string.ls_section_theme))
            ThemePresets.ALL.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { preset ->
                        Card(
                            title = preset.title,
                            detail = if (preset.id == ThemePresets.DEFAULT_ID) stringResource(R.string.ls_theme_default_detail) else preset.mode.name + " · " + preset.accent.displayName,
                            active = preset.id == themeId,
                            modifier = Modifier.weight(1f),
                        ) { onTheme(preset) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Section(stringResource(R.string.ls_section_wallpaper))
            Text(
                stringResource(R.string.ls_wallpaper_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperSets.ALL.forEach { set ->
                    WallpaperThumb(set, active = set.id == wallpaperId) { onWallpaper(set.id) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(stringResource(R.string.ls_wallpaper_home), "", false, Modifier.weight(1f)) { onApplyWallpaper(WallpaperTarget.HOME) }
                Card(stringResource(R.string.ls_wallpaper_lock), "", false, Modifier.weight(1f)) { onApplyWallpaper(WallpaperTarget.LOCK) }
                Card(stringResource(R.string.ls_wallpaper_both), "", false, Modifier.weight(1f)) { onApplyWallpaper(WallpaperTarget.BOTH) }
            }
            if (wallpaperStatus != null) {
                Text(
                    stringResource(wallpaperStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(stringResource(R.string.ls_section_widgets))
            SwitchRow(
                stringResource(R.string.ls_widgets_page),
                stringResource(R.string.ls_widgets_page_detail),
                widgetsPage,
                onWidgetsPage,
            )
            SettingsWidgets.TOGGLEABLE.forEach { widget ->
                val (title, detail) =
                    when (widget) {
                        GlanceWidgets.CALENDAR -> R.string.ls_widget_calendar to R.string.ls_widget_calendar_detail
                        GlanceWidgets.ALARM -> R.string.ls_widget_alarm to R.string.ls_widget_alarm_detail
                        else -> R.string.ls_widget_media to R.string.ls_widget_media_detail
                    }
                SwitchRow(stringResource(title), stringResource(detail), widget in widgets) { onWidget(widget) }
            }

            Section(stringResource(R.string.ls_section_hub))
            Card(stringResource(R.string.ls_hub), stringResource(R.string.ls_hub_detail), false, onClick = onHub)

            Section(stringResource(R.string.ls_section_lock))
            SwitchRow(stringResource(R.string.ls_lock_layer), stringResource(R.string.ls_lock_layer_detail), lockLayer, onLockLayer)
            if (lockLayer) {
                if (!overlayAllowed) {
                    Card(stringResource(R.string.ls_lock_overlay), stringResource(R.string.ls_lock_overlay_detail), false, onClick = onOverlay)
                }
                if (!accessAllowed) {
                    Card(stringResource(R.string.ls_lock_access), stringResource(R.string.ls_lock_access_detail), false, onClick = onAccess)
                }
                Card(stringResource(R.string.ls_lock_preview), stringResource(R.string.ls_lock_preview_detail), false, onClick = onPreviewLock)
                if (overlayAllowed && accessAllowed) {
                    Text(
                        stringResource(R.string.ls_lock_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

/**
 * A small picture of the icon grid: the chosen number of columns and rows of icon squares, each with a name bar when
 * labels are on, at the chosen icon size. A stand-in for the BlackBerry 7 tray, so the choices show as they change.
 */
@Composable
private fun IconGridPreview(
    columns: Int,
    rows: Int,
    iconSize: Bb7IconSize,
    labels: Boolean,
) {
    val shownColumns = if (columns == GridColumns.AUTO) 5 else columns
    val icon = (iconSize.iconDp * 0.6f).dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFF14181F),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(shownColumns) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(icon)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), MaterialTheme.shapes.small),
                            )
                            if (labels) {
                                Box(
                                    Modifier
                                        .padding(top = 3.dp)
                                        .size(width = icon, height = 4.dp)
                                        .background(Color.White.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One wallpaper set as a small picture with its name; the chosen one has a coloured border. */
@Composable
private fun WallpaperThumb(
    set: WallpaperSet,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bitmap = remember(set.id) { WallpaperArt.render(set, 108, 192).asImageBitmap() }
    Surface(
        onClick = onClick,
        modifier = Modifier.sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border =
            BorderStroke(
                if (active) 3.dp else 1.dp,
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bitmap, contentDescription = set.title, modifier = Modifier.size(width = 72.dp, height = 128.dp))
            Text(set.title, style = MaterialTheme.typography.labelMedium)
        }
    }
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
