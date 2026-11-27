package io.github.aimindseye.tenseven.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.model.Bb10Gestures
import io.github.aimindseye.tenseven.model.HomeHeader
import io.github.aimindseye.tenseven.model.HomeStatusMode
import io.github.aimindseye.tenseven.model.LauncherKeys
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.SpeedDialKeys
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.StartTypeToSearch
import io.github.aimindseye.tenseven.platform.DeviceCompatibility
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.Canvas as ComposeCanvas

@Composable
internal fun startGridWidthDp(): Int {
    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width
    val insets = WindowInsets.safeDrawing
    val available =
        with(density) {
            (
                width - insets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr) -
                    insets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
            ).toDp().value.toInt()
        }
    return (available - (GRID_PADDING.value * 2).toInt()).coerceAtLeast(1)
}

/**
 * The app grid's height in dp: the window (LocalWindowInfo, so the Q25's
 * square and any other size alike) less the system bars, the page dots, the
 * bar and the grid's padding. Before the window is measured, the
 * configuration's screen height stands in.
 */
@Composable
internal fun startGridHeightDp(): Int {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val insets = WindowInsets.safeDrawing
    val contentDp =
        if (windowHeightPx > 0) {
            with(density) { (windowHeightPx - insets.getTop(density) - insets.getBottom(density)).toDp().value.toInt() }
        } else {
            LocalConfiguration.current.screenHeightDp - STATUS_BAR_FALLBACK_DP
        }
    return contentDp - START_CHROME_DP
}

/**
 * The horizontal strip (BB10 layout): pages, the BB10 page indicator and the
 * Phone · Search · Camera bar.
 *
 * Keys anywhere on the strip: Alt+Left/Right (Fn+Left/Right) move one page,
 * Alt+1..Alt+3 open the bar slots, Alt+Space opens Command, digits typed on the
 * focused Phone slot dial, Menu that nothing took opens Edit Home, and any
 * other printable key opens Command/Search seeded with it.
 */
@Composable
internal fun StartStrip(
    state: PagerState,
    pageCount: Int,
    pageLead: Int,
    quickSlots: List<QuickSlot>,
    onMovePage: (Int) -> Unit,
    onShowPage: (Int) -> Unit,
    onQuickSlot: (QuickSlot) -> Unit,
    onHubPeek: () -> Unit,
    onTypeToSearch: (String) -> Unit,
    speedDialLetters: Set<Char>,
    onSpeedDial: (Char) -> Unit,
    onCommand: () -> Unit,
    onEditHome: () -> Unit,
    page: @Composable (Int) -> Unit,
) {
    val speedDialKeys = remember { SpeedDialKeys() }
    val homeFocus = remember { FocusRequester() }
    val swipePx = with(LocalDensity.current) { HOME_SWIPE_DP.dp.toPx() }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Bb10Background.copy(alpha = LocalHomeBackdrop.current))
                .startStripPreviewEvents(
                    StripInputState(quickSlots, speedDialLetters, speedDialKeys),
                    StripNavigationActions(onMovePage, onQuickSlot, onCommand),
                ).startStripSearchEvents(
                    StripInputState(quickSlots, speedDialLetters, speedDialKeys),
                    StripSearchActions(onEditHome, onTypeToSearch, onSpeedDial),
                )
                // After the key handlers, so they sit above this focus target: with nothing else focused (a
                // touch launch) Home still receives type-to-search, Alt+Space and Menu.
                .focusRequester(homeFocus)
                .focusable()
                // A swipe down on Home that no page used (they scroll vertically themselves) opens Command.
                .pointerInput(onCommand, swipePx) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = { if (Bb10Gestures.vertical(dragged, swipePx) != null) onCommand() },
                    ) { _, amount -> dragged += amount }
                },
    ) {
        LaunchedEffect(Unit) { runCatching { homeFocus.requestFocus() } }
        HorizontalPager(
            state = state,
            beyondViewportPageCount = 0,
            // Hub handles right swipe -> its Settings panel; left swipe -> Home.
            userScrollEnabled = state.currentPage - pageLead != StartPages.HUB,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index -> page(index) }
        if (state.currentPage != StartPages.HUB + pageLead) {
            HomeHintFooter(HomeHints.BB10, Bb10Text.copy(alpha = 0.6f))
            ReferenceHomeBar(
                slots = quickSlots,
                pages = HomeBarPages(state.currentPage, pageCount, pageLead),
                actions =
                    HomeBarActions(
                        onShowPage,
                        onQuickSlot,
                        peek = onHubPeek,
                    ),
            )
        }
    }
}

private data class StripInputState(
    val quickSlots: List<QuickSlot>,
    val speedDialLetters: Set<Char>,
    val speedDialKeys: SpeedDialKeys,
)

private data class StripNavigationActions(
    val onMovePage: (Int) -> Unit,
    val onQuickSlot: (QuickSlot) -> Unit,
    val onCommand: () -> Unit,
)

private data class StripSearchActions(
    val onEditHome: () -> Unit,
    val onTypeToSearch: (String) -> Unit,
    val onSpeedDial: (Char) -> Unit,
)

private fun Modifier.startStripPreviewEvents(
    state: StripInputState,
    actions: StripNavigationActions,
): Modifier {
    val quickSlots = state.quickSlots
    val onMovePage = actions.onMovePage
    val onQuickSlot = actions.onQuickSlot
    val onCommand = actions.onCommand
    return this
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val delta = StartPages.keyDelta(native.keyCode, native.metaState)
            val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
            val slot =
                QuickBar.shortcutSlot(
                    keyCode = native.keyCode,
                    metaState = native.metaState,
                    slotCount = quickSlots.size,
                    unicode = native.unicodeChar,
                )
            val action: (() -> Unit)? =
                when {
                    delta != null -> {
                        { onMovePage(delta) }
                    }

                    alt && native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE -> {
                        onCommand
                    }

                    slot != null -> {
                        { onQuickSlot(quickSlots[slot]) }
                    }

                    else -> {
                        null
                    }
                }
            if (action != null && event.type == KeyEventType.KeyDown && native.repeatCount == 0) action()
            action != null
        }
}

private fun Modifier.startStripSearchEvents(
    state: StripInputState,
    actions: StripSearchActions,
): Modifier {
    val speedDialKeys = state.speedDialKeys
    val speedDialLetters = state.speedDialLetters
    val onEditHome = actions.onEditHome
    val onTypeToSearch = actions.onTypeToSearch
    val onSpeedDial = actions.onSpeedDial
    return this
        .onKeyEvent { event ->
            // What no frame, icon or slot took: Menu edits Home, printable keys search.
            val native = event.nativeKeyEvent
            if (DeviceCompatibility.keyAction(native) == LauncherKeys.Action.Menu) {
                if (event.type == KeyEventType.KeyDown) onEditHome()
                return@onKeyEvent true
            }
            val down = event.type == KeyEventType.KeyDown
            val seed =
                if (down) {
                    StartTypeToSearch.seed(
                        unicode = native.unicodeChar,
                        metaState = native.metaState,
                        repeat = native.repeatCount > 0,
                    )
                } else {
                    null
                }
            // Holding a speed dial letter calls it; a short press of it still searches.
            val step =
                speedDialKeys.onKey(
                    native.keyCode,
                    down,
                    native.repeatCount,
                    native.metaState,
                    seed,
                    speedDialLetters,
                )
            when {
                step is SpeedDialKeys.Step.Call -> onSpeedDial(step.letter)
                step is SpeedDialKeys.Step.Search -> onTypeToSearch(step.seed)
                step == SpeedDialKeys.Step.Consume -> Unit
                seed != null -> onTypeToSearch(seed.toString())
                else -> return@onKeyEvent false
            }
            true
        }
}

// The three BB10 system-page marks share the same geometry and colors.
@Suppress("FunctionNaming")
@Composable
private fun Bb10IndicatorMark(
    page: Int,
    color: Color,
) {
    ComposeCanvas(Modifier.size(if (page < StartPages.FIRST_GRID) 10.dp else 6.dp)) {
        val unit = size.minDimension
        when (page) {
            StartPages.WIDGETS -> {
                // Widgets: one wide outlined panel.
                drawRoundRect(
                    color = color,
                    topLeft = Offset(unit * 0.05f, unit * 0.2f),
                    size = Size(unit * 0.9f, unit * 0.6f),
                    cornerRadius = CornerRadius(unit * 0.12f),
                    style = Stroke(width = unit * 0.14f),
                )
            }

            StartPages.HUB -> {
                // Hub: three short lines.
                for (line in 0..2) {
                    val y = unit * (0.2f + line * 0.3f)
                    drawLine(color, Offset(0f, y), Offset(unit, y), strokeWidth = unit * 0.14f, cap = StrokeCap.Round)
                }
            }

            StartPages.HOME -> {
                // Active Frames: four small squares.
                val cell = unit * 0.42f
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRect(
                            color = color,
                            topLeft = Offset(col * (unit - cell), row * (unit - cell)),
                            size = Size(cell, cell),
                        )
                    }
                }
            }

            else -> {
                drawCircle(color = color, radius = unit / 2f)
            }
        }
    }
}

internal fun pageDescription(
    page: Int,
    widgetsLabel: String,
): String =
    when (page) {
        StartPages.WIDGETS -> widgetsLabel
        StartPages.HUB -> t("hub_page")
        StartPages.HOME -> t("active_frames")
        else -> t("app_grid_page", page - StartPages.FIRST_GRID + 1)
    }

/** The status dials (Wi-Fi, Mobile, CPU, RAM, battery time left, Cell) (the Settings gear is gone; Settings opens from the long-press menu). */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun StatusDialsRow(
    onOpenSettings: () -> Unit,
    visible: Set<String>,
) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var mode by remember(context) {
        mutableStateOf(StartStateRepository(context).loadHomeStatusMode())
    }
    var selectedMetrics by remember(context, visible) {
        mutableStateOf(StartStateRepository(context).loadHomeDials())
    }
    DisposableEffect(lifecycle, context) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    val saved = StartStateRepository(context)
                    mode = saved.loadHomeStatusMode()
                    selectedMetrics = saved.loadHomeDials()
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = FRAME_PADDING, end = 2.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == HomeStatusMode.Hidden) {
            androidx.compose.foundation.layout
                .Spacer(Modifier.weight(1f))
        } else {
            BbHomeStatus(mode = mode, visible = selectedMetrics, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Today's date under the status dials. The date follows the
 * locale and turns over at midnight; there is no clock here.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun DateHeader(
    refreshGeneration: Int,
    unread: Int,
    onShowHub: () -> Unit,
    showLegacyBadge: Boolean = true,
    onOpenSettings: (() -> Unit)? = null,
) {
    val locale = Locale.getDefault()
    val today by
        produceState(initialValue = LocalDate.now(), refreshGeneration) {
            while (true) {
                value = LocalDate.now()
                val millisOfDay = LocalTime.now().toNanoOfDay() / NANOS_PER_MILLI
                delay(HomeHeader.millisToMidnight(millisOfDay))
            }
        }
    val dateText =
        remember(today, locale) {
            val pattern =
                android.text.format.DateFormat
                    .getBestDateTimePattern(locale, HomeHeader.DATE_SKELETON)
            today.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = FRAME_PADDING).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val header = rememberHeaderOptions()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateText,
                fontSize = (23.sp * header.size.multiplier),
                color = Bb10Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = dateText },
            )
            HomeWeatherLine(header, modifier = Modifier.fillMaxWidth())
        }
        SoundProfileButton()
    }
}

/** [Bb10Theme] with the Hub peek layered over the Start strip. */
@Composable
internal fun Bb10ThemeWithPeek(
    appearance: SableAppearance,
    peekOpen: Boolean,
    edgeEnabled: Boolean,
    onPeekChange: (Boolean) -> Unit,
    onOpenHub: () -> Unit,
    content: @Composable () -> Unit,
) {
    Bb10Theme(appearance) {
        HubPeekHost(peekOpen, edgeEnabled, onPeekChange, onOpenHub, content)
    }
}

/*
 * The built-in widgets Launcher Settings turned on (Calendar, Alarm, Battery, Now playing) as cards at the top of the
 * Widgets page, in the user's order. No ambient polling: values refresh with Start's live snapshot.
 */
