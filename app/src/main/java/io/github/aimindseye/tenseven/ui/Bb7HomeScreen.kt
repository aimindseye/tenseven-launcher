package io.github.aimindseye.tenseven.ui

import android.content.Intent
import android.provider.AlarmClock
import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableTheme
import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveDatum
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.Bb7Home
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.Bb7Panel
import io.github.aimindseye.tenseven.model.Bb7Panels
import io.github.aimindseye.tenseven.model.Bb7Scrim
import io.github.aimindseye.tenseven.model.Bb7Swipe
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeApps
import io.github.aimindseye.tenseven.model.SettingsWidgets
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.AppUsageRepository
import io.github.aimindseye.tenseven.platform.DefaultApps
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.NextAlarm
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Sable Start in the BlackBerry 7 style ([io.github.aimindseye.tenseven.model.HomeStyle.Bb7]): the banner
 * (status dials, date, weather and unread count; the time is the status bar's, so it is not shown
 * twice) on the wallpaper, and a tray along the bottom with one row of Favorites that opens into
 * the whole panel. Keys as on BlackBerry 7: arrows move, Enter opens, Up opens the tray and
 * Escape closes it, Space switches panels (Favorites, All, Downloads), typing searches apps, Menu
 * opens the home options. As on the BlackBerry 10 home, Hub is to the left and the apps to the
 * right: a swipe to the right or Alt+Left opens Hub, a swipe to the left or Alt+Right opens All;
 * with the tray open, Alt+Left/Right switch panels. The home screen shows [homeRows] rows of the
 * panel's icons; dragging the tray up or down shows more or fewer, as on BlackBerry 7. Menu on an
 * icon puts it on the home screen (Add to Favorites), takes it off, or moves it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bb7HomeScreen(
    apps: List<AppEntry>,
    favoriteKeys: List<String>?,
    homeRows: Int,
    liveSurfaceRepository: LiveSurfaceRepository,
    appearance: SableAppearance,
    refreshGeneration: Int,
    homeGeneration: Int,
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenHub: () -> Unit,
    onFavoritesChanged: (List<String>) -> Unit,
    onHomeRowsChanged: (Int) -> Unit,
    onOpenMenu: (List<Pair<String, () -> Unit>>) -> Unit,
    /** Alt+Space (as on the BlackBerry 10 home): Command. */
    onOpenCommand: () -> Unit = {},
    onDoubleTapEmpty: (() -> Unit)? = null,
    /** The panels the tray cycles through, and how dark the banner and tray are; both are set from the Menu key. */
    panels: List<Bb7Panel> = Bb7Panels.DEFAULT,
    scrimLevel: Int = Bb7Scrim.DEFAULT,
    onPanelsChanged: (List<Bb7Panel>) -> Unit = {},
    onScrimChanged: (Int) -> Unit = {},
    onOpenWallpaper: () -> Unit = {},
    /** Icon size and the widgets shown under the banner (Launcher Settings). */
    iconSize: Bb7IconSize = Bb7IconSize.DEFAULT,
    widgets: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val usage = remember(apps, refreshGeneration, homeGeneration) { AppUsageRepository(context).load() }
    var panel by remember { mutableStateOf(Bb7Panel.Favorites) }
    // A panel the user hid can no longer be the one shown.
    LaunchedEffect(panels) { if (panel !in panels) panel = panels.first() }
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf(false) }

    // Set from the laid-out width below, read by the key handler.
    var keyColumns by remember { mutableIntStateOf(Bb7Home.MIN_COLUMNS) }
    val focus = remember { FocusRequester() }

    // Home closes the tray and any search, as the BlackBerry 7 home key did.
    LaunchedEffect(homeGeneration) {
        open = false
        query = ""
        selected = 0
        moving = false
    }
    val live by
        produceState(liveSurfaceRepository.initialSnapshot(), refreshGeneration) {
            value = liveSurfaceRepository.snapshot()
        }
    val today by
        produceState(System.currentTimeMillis(), refreshGeneration) {
            while (true) {
                value = System.currentTimeMillis()
                delay(DATE_TICK_MS - value % DATE_TICK_MS)
            }
        }
    val openApps = {
        query = ""
        panel = if (Bb7Panel.All in panels) Bb7Panel.All else panels.first()
        open = true
        selected = 0
    }

    val defaultPackages = remember(context, apps.size) { DefaultApps.homePackages(context) }
    val searching = query.isNotEmpty()
    val shown =
        if (searching) Bb7Home.search(apps, query) else Bb7Home.panelApps(panel, apps, favoriteKeys, usage, defaultPackages)
    val favorites = Bb7Home.panelApps(Bb7Panel.Favorites, apps, favoriteKeys, defaultPackages = defaultPackages).map { it.stableKey() }
    val trayTitle =
        when {
            moving -> t("move") + shown.getOrNull(selected)?.label.orEmpty() + t("enter_to_drop")
            searching -> t("search", query)
            else -> panel.title
        }
    // Moves the favourite being moved one place ([delta] -1/+1) or a row ([delta] ±columns).
    val moveBy = { delta: Int ->
        val next = HomeApps.move(favorites, selected, delta)
        if (next != favorites) {
            onFavoritesChanged(next)
            selected += delta
        }
    }
    // The Menu key: the highlighted icon's items first, then Start's own (added by the activity).
    val menuFor = { app: AppEntry? ->
        buildList<Pair<String, () -> Unit>> {
            if (app != null) {
                val key = app.stableKey()
                add(t("open") to { onLaunchApp(app) })
                if (key in favorites) {
                    if (panel == Bb7Panel.Favorites && !searching) add(t("move_2") to { moving = true })
                    add(context.getString(R.string.bb7_remove_home) to { onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key)) })
                } else {
                    add(context.getString(R.string.bb7_add_home) to { onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key)) })
                }
                add(t("app_info") to { onOpenAppInfo(app) })
            }
            if (!open && !searching && homeRows < Bb7Home.MAX_HOME_ROWS) {
                add(t("show_more_rows") to { onHomeRowsChanged(homeRows + 1) })
            }
            if (!open && !searching && homeRows > Bb7Home.MIN_HOME_ROWS) {
                add(t("show_fewer_rows") to { onHomeRowsChanged(homeRows - 1) })
            }
            if (!searching && panel != Bb7Panel.All) {
                // Add icons to the home screen: pick them in All (Menu or long-press an icon, Add to Favorites).
                add(context.getString(R.string.bb7_add_apps) to openApps)
            }
            if (!searching) {
                // Panels: show or hide each one (the last one stays); the banner and tray can be more see-through.
                Bb7Panel.entries.forEach { p ->
                    if (p in panels && panels.size == 1) return@forEach
                    val text = context.getString(if (p in panels) R.string.bb7_panel_hide else R.string.bb7_panel_show, p.title)
                    add(text to { onPanelsChanged(Bb7Panels.toggle(panels, p)) })
                }
                if (scrimLevel > Bb7Scrim.MIN) add(context.getString(R.string.bb7_clearer) to { onScrimChanged(Bb7Scrim.step(scrimLevel, -1)) })
                if (scrimLevel < Bb7Scrim.MAX) add(context.getString(R.string.bb7_darker) to { onScrimChanged(Bb7Scrim.step(scrimLevel, 1)) })
                add(context.getString(R.string.bb7_wallpaper) to onOpenWallpaper)
            }
        }
    }

    SableTheme(appearance = appearance) {
        Bb10Theme(appearance) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val native = event.nativeKeyEvent
                            val columns = keyColumns
                            val visible = if (open || searching) shown.size else minOf(shown.size, columns * homeRows)
                            if (moving) {
                                when (native.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> moveBy(-1)
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> moveBy(1)
                                    KeyEvent.KEYCODE_DPAD_UP -> moveBy(-columns)
                                    KeyEvent.KEYCODE_DPAD_DOWN -> moveBy(columns)
                                    else -> moving = false
                                }
                                return@onPreviewKeyEvent true
                            }
                            when {
                                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && native.isAltPressed ->
                                    if (open || searching) {
                                        panel = Bb7Home.step(panel, -1, panels)
                                        selected = 0
                                    } else {
                                        onOpenHub()
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && native.isAltPressed ->
                                    if (open || searching) {
                                        panel = Bb7Home.step(panel, 1, panels)
                                        selected = 0
                                    } else {
                                        openApps()
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ->
                                    selected = Bb7Home.move(selected, visible, columns, -1, 0)
                                native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ->
                                    selected = Bb7Home.move(selected, visible, columns, 1, 0)
                                native.keyCode == KeyEvent.KEYCODE_DPAD_UP ->
                                    if (open || searching || selected >= columns) {
                                        selected = Bb7Home.move(selected, visible, columns, 0, -1)
                                    } else {
                                        open = true
                                    }
                                native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ->
                                    if (open && selected + columns >= visible) {
                                        open = false
                                        selected = selected.coerceAtMost(columns - 1)
                                    } else {
                                        selected = Bb7Home.move(selected, visible, columns, 0, 1)
                                    }
                                native.keyCode == KeyEvent.KEYCODE_ENTER && native.isAltPressed ->
                                    onOpenMenu(menuFor(shown.getOrNull(selected)))
                                native.keyCode == KeyEvent.KEYCODE_ENTER ||
                                    native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ->
                                    shown.getOrNull(selected)?.let { onLaunchApp(it) }
                                native.keyCode == KeyEvent.KEYCODE_SPACE && native.isAltPressed -> onOpenCommand()
                                native.keyCode == KeyEvent.KEYCODE_SPACE && !searching -> {
                                    panel = Bb7Home.step(panel, 1, panels)
                                    selected = 0
                                }
                                native.keyCode == KeyEvent.KEYCODE_DEL && searching -> {
                                    query = query.dropLast(1)
                                    selected = 0
                                }
                                native.keyCode == KeyEvent.KEYCODE_ESCAPE || native.keyCode == KeyEvent.KEYCODE_BACK ->
                                    if (searching || open) {
                                        query = ""
                                        open = false
                                        selected = 0
                                    } else {
                                        return@onPreviewKeyEvent false
                                    }
                                native.keyCode == KeyEvent.KEYCODE_MENU -> onOpenMenu(menuFor(shown.getOrNull(selected)))
                                else -> {
                                    val ch = native.unicodeChar.takeIf { it > 0 }?.toChar()
                                    if (ch == null || !(ch.isLetterOrDigit() || (ch == ' ' && searching))) {
                                        return@onPreviewKeyEvent false
                                    }
                                    query += ch
                                    selected = 0
                                }
                            }
                            true
                        },
            ) {
                val columns = Bb7Home.columns(maxWidth.value.toInt(), iconSize.cellDp)
                SideEffect { keyColumns = columns }
                val swipe = with(LocalDensity.current) { SWIPE_DP.dp.toPx() }
                val rowPx = with(LocalDensity.current) { ROW_DP.dp.toPx() }
                Column(
                    modifier =
                        Modifier.fillMaxSize().pointerInput(swipe) {
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = {
                                    when (Bb7Home.swipe(dragged, swipe)) {
                                        Bb7Swipe.Hub -> onOpenHub()
                                        Bb7Swipe.Apps -> openApps()
                                        null -> Unit
                                    }
                                },
                            ) { _, amount -> dragged += amount }
                        }.pointerInput(onDoubleTapEmpty, open) {
                            // Only empty space: taps on icons and rows are consumed by them first.
                            // Long-press empty space: open All to pick icons for the home screen.
                            if (!open) {
                                detectTapGestures(
                                    onDoubleTap = { onDoubleTapEmpty?.invoke() },
                                    onLongPress = { openApps() },
                                )
                            }
                        },
                ) {
                    Bb7Banner(
                        dateText = DateFormat.getDateInstance(DateFormat.FULL).format(Date(today)),
                        unread = live.hubApps.sumOf { it.count },
                        compact = open || searching,
                        scrim = Color.Black.copy(alpha = Bb7Scrim.bannerAlpha(scrimLevel)),
                        onOpenHub = onOpenHub,
                    )
                    if (!open && !searching) {
                        Bb7WidgetStrip(
                            widgets = SettingsWidgets.strip(widgets),
                            calendar = live.calendar,
                            media = live.music,
                            refreshGeneration = refreshGeneration,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    Bb7Tray(
                        title = trayTitle,
                        apps = if (open || searching) shown else shown.take(columns * homeRows),
                        columns = columns,
                        open = open || searching,
                        iconDp = iconSize.iconDp,
                        moving = moving,
                        scrim = Color.Black.copy(alpha = Bb7Scrim.trayAlpha(scrimLevel)),
                        onResize = { dragged ->
                            val rows = Bb7Home.resizeRows(homeRows, dragged, rowPx)
                            if (rows > Bb7Home.MAX_HOME_ROWS) {
                                open = true
                            } else if (rows != homeRows) {
                                onHomeRowsChanged(rows)
                                selected = selected.coerceAtMost(columns * rows - 1)
                            }
                        },
                        selected = selected,
                        modifier = if (open || searching) Modifier.weight(1f) else Modifier,
                        onToggle = {
                            open = !open
                            query = ""
                        },
                        onStep = { step ->
                            query = ""
                            panel = Bb7Home.step(panel, step, panels)
                            selected = 0
                        },
                        onLaunch = { onLaunchApp(it) },
                        onMenu = {
                            // The ⋮ button: the menu for the highlighted icon.
                            onOpenMenu(menuFor(shown.getOrNull(selected)))
                        },
                        onLongPress = { app ->
                            // Long-press an icon: its menu (Add to Favorites, Move, App info...).
                            selected = shown.indexOf(app).coerceAtLeast(0)
                            onOpenMenu(menuFor(app))
                        },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** One widget of the strip: its name small, its line below, a tap opens the phone's app for it. */
private data class Bb7Widget(
    val title: String,
    val line: String,
    val open: () -> Unit,
)

/** The widgets Launcher Settings turned on (Calendar, Alarm, Now Playing), side by side under the banner. */
@Composable
private fun Bb7WidgetStrip(
    widgets: List<String>,
    calendar: LiveDatum,
    media: LiveDatum,
    refreshGeneration: Int,
) {
    if (widgets.isEmpty()) return
    val context = LocalContext.current
    val alarm = remember(refreshGeneration) { NextAlarm.text(context) }
    val cards =
        widgets.map { widget ->
            when (widget) {
                GlanceWidgets.CALENDAR ->
                    Bb7Widget(
                        "Calendar",
                        calendar.detail.takeIf { calendar.availability == LiveAvailability.LIVE }.orEmpty().ifBlank { "No events" },
                    ) { DefaultApps.launch(context, DefaultApps.CALENDAR) }
                GlanceWidgets.ALARM ->
                    Bb7Widget("Alarm", alarm ?: "No alarm") {
                        runCatching {
                            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                else ->
                    Bb7Widget(
                        "Now Playing",
                        media.detail.takeIf { media.availability == LiveAvailability.LIVE }.orEmpty().ifBlank { "Nothing playing" },
                    ) { DefaultApps.launch(context, DefaultApps.MUSIC) }
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cards.forEach { card ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(onClick = card.open)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(card.title, color = BANNER_MUTED, fontSize = 10.sp, maxLines = 1)
                Text(card.line, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The BlackBerry 7 banner: the status dials (as on the BlackBerry 10 home), the date, weather and
 * the Hub unread count, on a scrim so they read over any wallpaper. The status bar shows the time.
 */
@Composable
private fun Bb7Banner(
    dateText: String,
    unread: Int,
    compact: Boolean,
    scrim: Color,
    onOpenHub: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(scrim)
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        if (!compact) SableStatusRings(modifier = Modifier.fillMaxWidth(), ringSize = RING_DP.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(top = if (compact) 0.dp else 6.dp)) {
                Text(
                    text = dateText,
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            SoundProfileButton()
            Spacer(Modifier.width(6.dp))
            UnreadBadge(unread, onOpenHub)
        }
    }
}

/** The Hub unread count; tapping it opens Hub. Nothing when there is nothing unread. */
@Composable
private fun UnreadBadge(
    unread: Int,
    onOpenHub: () -> Unit,
) {
    if (unread <= 0) return
    Text(
        text = "✉ $unread",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                .clickable(onClick = onOpenHub)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * The tray: its panel name between arrows, then the home screen's rows of apps (closed) or the
 * whole panel (open). Tapping the name opens or closes it; the arrows switch panels; dragging the
 * closed tray up or down ([onResize], px) shows more or fewer rows. The icon being moved is outlined.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bb7Tray(
    title: String,
    apps: List<AppEntry>,
    columns: Int,
    open: Boolean,
    iconDp: Int,
    moving: Boolean,
    selected: Int,
    scrim: Color,
    modifier: Modifier,
    onResize: (Float) -> Unit,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    onStep: (Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
) {
    val grid = rememberLazyGridState()
    LaunchedEffect(selected, open) {
        if (open && selected in apps.indices) grid.animateScrollToItem(selected)
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(scrim)
                .then(
                    if (open) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            var dragged = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = { onResize(dragged) },
                            ) { _, amount -> dragged += amount }
                        }
                    },
                ).padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrayArrow("‹") { onStep(-1) }
            Text(
                text = title + if (open) "  ▾" else "  ▴",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onToggle),
            )
            TrayArrow("›") { onStep(1) }
            // Android's Titan 2 has no Menu key: this button (or Alt+Enter) opens the same menu.
            TrayArrow("⋮") { onMenu() }
        }
        if (apps.isEmpty()) {
            Text(
                text = t("no_apps_here"),
                color = BANNER_MUTED,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                textAlign = TextAlign.Center,
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            userScrollEnabled = open,
            modifier = if (open) Modifier.weight(1f) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.component.flattenToString() + app.user.hashCode() }) { index, app ->
                val isSelected = index == selected
                val outline = if (moving && isSelected) Color.White else MaterialTheme.colorScheme.primary
                Column(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) outline else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
                            .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppMark(app = app, size = iconDp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrayArrow(
    mark: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.width(44.dp).height(32.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = mark, color = Color.White, fontSize = 22.sp)
    }
}

private const val DATE_TICK_MS = 60_000L
private const val RING_DP = 34
private const val SWIPE_DP = 72

/** A tray row's height, the drag that shows one more or one fewer row. */
private const val ROW_DP = 64
private val BANNER_MUTED = Color(0xFFC9D1DA)
