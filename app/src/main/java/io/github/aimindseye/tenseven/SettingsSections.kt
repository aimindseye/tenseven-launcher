package io.github.aimindseye.tenseven

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.Bb7Home
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.SettingsWidgets
import io.github.aimindseye.tenseven.model.ThemePresets
import io.github.aimindseye.tenseven.model.WallpaperSet
import io.github.aimindseye.tenseven.model.WallpaperSets
import io.github.aimindseye.tenseven.platform.LockLayerLauncher
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.platform.WallpaperArt
import io.github.aimindseye.tenseven.platform.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The setting groups shared by Launcher Settings and the setup screens. Each one reads and saves its own choices
 * through [StartStateRepository], so a setting added here shows up in both places and the first-run setup cannot
 * fall behind Launcher Settings. They draw no heading: the caller supplies one.
 */

private val LocalHomeStyleState = compositionLocalOf<MutableState<HomeStyle>?> { null }

/**
 * Gives every section on one screen the same current home style, so the BlackBerry 7 settings appear or go the moment
 * the style is picked. A screen that shows several sections wraps them in this; a section alone reads the saved style.
 */
@Composable
internal fun ProvideHomeStyle(
    state: StartStateRepository,
    content: @Composable () -> Unit,
) {
    val holder = remember { mutableStateOf(state.loadHomeStyle()) }
    CompositionLocalProvider(LocalHomeStyleState provides holder, content = content)
}

@Composable
private fun homeStyleState(state: StartStateRepository): MutableState<HomeStyle> =
    LocalHomeStyleState.current ?: remember { mutableStateOf(state.loadHomeStyle()) }

/** BlackBerry 7 or BlackBerry 10. */
@Composable
internal fun HomeStyleSection(
    state: StartStateRepository,
    firstFocus: FocusRequester? = null,
) {
    var style by homeStyleState(state)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleCard(
            title = stringResource(R.string.ls_bb7_title),
            detail = stringResource(R.string.ls_bb7_detail),
            active = style == HomeStyle.Bb7,
            modifier = Modifier.weight(1f).then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
        ) {
            style = HomeStyle.Bb7
            state.saveHomeStyle(HomeStyle.Bb7)
        }
        StyleCard(
            title = stringResource(R.string.ls_bb10_title),
            detail = stringResource(R.string.ls_bb10_detail),
            active = style == HomeStyle.Bb10,
            modifier = Modifier.weight(1f),
        ) {
            style = HomeStyle.Bb10
            state.saveHomeStyle(HomeStyle.Bb10)
        }
    }
}

/**
 * Columns, rows, icon size and app names, with a small live preview. Only the app names switch also applies to the
 * BlackBerry 10 home, so the rest shows only while BlackBerry 7 is the home screen style.
 */
@Composable
internal fun IconGridSection(state: StartStateRepository) {
    val bb7 = homeStyleState(state).value == HomeStyle.Bb7
    var iconSize by remember { mutableStateOf(state.loadBb7IconSize()) }
    var gridColumns by remember { mutableStateOf(state.loadGridColumns()) }
    var rows by remember { mutableStateOf(state.loadBb7HomeRows()) }
    var labels by remember { mutableStateOf(state.loadGridLabels()) }
    if (!bb7) {
        SwitchRow(stringResource(R.string.ls_labels), stringResource(R.string.ls_labels_detail), labels) {
            labels = it
            state.saveGridLabels(it)
        }
        Text(
            stringResource(R.string.ls_grid_bb7_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    IconGridPreview(columns = gridColumns, rows = rows, iconSize = iconSize, labels = labels)
    Text(stringResource(R.string.ls_icon_size), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Bb7IconSize.entries.forEach { size ->
            Card(title = size.title, detail = "", active = size == iconSize, modifier = Modifier.weight(1f)) {
                iconSize = size
                state.saveBb7IconSize(size)
            }
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
            ) {
                gridColumns = option
                state.saveGridColumns(option)
            }
        }
    }
    Text(stringResource(R.string.ls_rows), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (Bb7Home.MIN_HOME_ROWS..Bb7Home.MAX_HOME_ROWS).forEach { option ->
            Card(title = option.toString(), detail = "", active = option == rows, modifier = Modifier.weight(1f)) {
                rows = option
                state.saveBb7HomeRows(option)
            }
        }
    }
    SwitchRow(stringResource(R.string.ls_labels), stringResource(R.string.ls_labels_detail), labels) {
        labels = it
        state.saveGridLabels(it)
    }
}

/** The themes: BlackBerry (the default), Phone colours and six more. */
@Composable
internal fun ThemeSection(state: StartStateRepository) {
    var themeId by remember { mutableStateOf(ThemePresets.matching(state.loadAppearanceMode(), state.loadAppearanceAccent()).id) }
    ThemePresets.ALL.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { preset ->
                Card(
                    title = preset.title,
                    detail =
                        if (preset.id == ThemePresets.DEFAULT_ID) {
                            stringResource(R.string.ls_theme_default_detail)
                        } else if (preset.id == ThemePresets.PHONE_ID) {
                            stringResource(R.string.ls_theme_phone_detail)
                        } else {
                            preset.mode.name + " · " + preset.accent.displayName
                        },
                    active = preset.id == themeId,
                    modifier = Modifier.weight(1f),
                ) {
                    themeId = preset.id
                    if (preset.id == ThemePresets.DEFAULT_ID) {
                        state.saveAppearance(null, null)
                    } else {
                        state.saveAppearance(preset.mode.stableValue, preset.accent.stableValue)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Pick one of the eight wallpapers and set it for Home, Lock or both; plus the BlackBerry 10 see-through switch. */
@Composable
internal fun WallpaperSection(state: StartStateRepository) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var wallpaperId by remember {
        mutableStateOf(ThemePresets.matching(state.loadAppearanceMode(), state.loadAppearanceAccent()).wallpaperId)
    }
    var status by remember { mutableStateOf<Int?>(null) }
    var homeWallpaper by remember { mutableStateOf(state.loadHomeWallpaper()) }
    Text(
        stringResource(R.string.ls_wallpaper_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WallpaperSets.ALL.forEach { set -> WallpaperThumb(set, active = set.id == wallpaperId) { wallpaperId = set.id } }
    }
    // The BlackBerry 7 home always shows the wallpaper, so this switch is for the BlackBerry 10 home only.
    if (homeStyleState(state).value == HomeStyle.Bb10) {
        SwitchRow(stringResource(R.string.ls_home_wallpaper), stringResource(R.string.ls_home_wallpaper_detail), homeWallpaper) {
            homeWallpaper = it
            state.saveHomeWallpaper(it)
        }
    }
    fun apply(target: WallpaperTarget) {
        status = R.string.ls_wallpaper_working
        val set = WallpaperSets.byId(wallpaperId)
        scope.launch {
            val ok = withContext(Dispatchers.IO) { WallpaperArt.apply(context, set, target) }
            status = if (ok) R.string.ls_wallpaper_done else R.string.ls_wallpaper_failed
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(stringResource(R.string.ls_wallpaper_home), "", false, Modifier.weight(1f)) { apply(WallpaperTarget.HOME) }
        Card(stringResource(R.string.ls_wallpaper_lock), "", false, Modifier.weight(1f)) { apply(WallpaperTarget.LOCK) }
        Card(stringResource(R.string.ls_wallpaper_both), "", false, Modifier.weight(1f)) { apply(WallpaperTarget.BOTH) }
    }
    status?.let {
        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The Widgets page switch and the built-in widgets that show on it. */
@Composable
internal fun WidgetsSection(state: StartStateRepository) {
    var widgetsPage by remember { mutableStateOf(state.loadWidgetsPage()) }
    var widgets by remember { mutableStateOf(GlanceWidgets.normalize(state.loadGlanceWidgets())) }
    SwitchRow(stringResource(R.string.ls_widgets_page), stringResource(R.string.ls_widgets_page_detail), widgetsPage) {
        widgetsPage = it
        state.saveWidgetsPage(it)
    }
    SettingsWidgets.TOGGLEABLE.forEach { widget ->
        val (title, detail) =
            when (widget) {
                GlanceWidgets.CALENDAR -> R.string.ls_widget_calendar to R.string.ls_widget_calendar_detail
                GlanceWidgets.ALARM -> R.string.ls_widget_alarm to R.string.ls_widget_alarm_detail
                GlanceWidgets.BATTERY -> R.string.ls_widget_battery to R.string.ls_widget_battery_detail
                else -> R.string.ls_widget_media to R.string.ls_widget_media_detail
            }
        SwitchRow(stringResource(title), stringResource(detail), widget in widgets) {
            widgets = GlanceWidgets.toggle(widgets, widget)
            state.saveGlanceWidgets(widgets)
        }
    }
}

/** A way into the Hub settings screen. */
@Composable
internal fun HubSection() {
    val context = LocalContext.current
    Card(stringResource(R.string.ls_hub), stringResource(R.string.ls_hub_detail), false) {
        runCatching { context.startActivity(Intent(context, HubSettingsActivity::class.java)) }
    }
}

/** The lock layer switch, the two permissions it needs, and a preview. */
@Composable
internal fun LockLayerSection(state: StartStateRepository) {
    val context = LocalContext.current
    var lockLayer by remember { mutableStateOf(state.loadLockLayer()) }
    var tick by remember { mutableIntStateOf(0) }
    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val overlayAllowed = tick >= 0 && LockLayerLauncher.canDrawOverlays(context)
    val accessAllowed = tick >= 0 && ConversationAccess.isGranted(context)
    SwitchRow(stringResource(R.string.ls_lock_layer), stringResource(R.string.ls_lock_layer_detail), lockLayer) {
        lockLayer = it
        state.saveLockLayer(it)
    }
    if (lockLayer) {
        if (!overlayAllowed) {
            Card(stringResource(R.string.ls_lock_overlay), stringResource(R.string.ls_lock_overlay_detail), false) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")),
                    )
                }
            }
        }
        if (!accessAllowed) {
            Card(stringResource(R.string.ls_lock_access), stringResource(R.string.ls_lock_access_detail), false) {
                runCatching { context.startActivity(ConversationAccess.settingsIntent()) }
            }
        }
        Card(stringResource(R.string.ls_lock_preview), stringResource(R.string.ls_lock_preview_detail), false) {
            val returnTo = activity?.javaClass?.name
            runCatching {
                context.startActivity(
                    Intent(context, LockLayerActivity::class.java).apply { if (returnTo != null) putExtra(EXTRA_RETURN_TO, returnTo) },
                )
            }
        }
        if (overlayAllowed && accessAllowed) {
            Text(
                stringResource(R.string.ls_lock_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A small picture of the icon grid: the chosen number of columns and rows of icon squares, each with a name bar when
 * labels are on, at the chosen icon size. A stand-in for the BlackBerry 7 tray, so the choices show as they change.
 */
@Composable
internal fun IconGridPreview(
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
internal fun WallpaperThumb(
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
internal fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun StyleCard(
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = Card(title + if (active) " · " + stringResource(R.string.ls_current) else "", detail, active, modifier, onClick)

@Composable
internal fun Card(
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
internal fun SwitchRow(
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
