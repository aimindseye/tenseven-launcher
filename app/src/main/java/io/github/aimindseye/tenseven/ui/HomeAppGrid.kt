package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.privacy.AllAppsKeyAction
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy

@Composable
internal fun AppGridPage(
    apps: List<AppEntry>,
    rows: Int,
    columns: Int,
    iconScale: Float,
    /** Every grid app's name, so a shortened name never repeats another ([AppGrid.gridLabel]). */
    gridLabels: Set<String>,
    active: Boolean,
    onLaunchApp: (AppEntry) -> Unit,
    onLaunchSecond: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onEditHome: () -> Unit,
    showLabels: Boolean = true,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(active, apps.firstOrNull()?.stableKey()) {
        if (active && apps.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onEditHome() }) }
                .padding(horizontal = GRID_PADDING, vertical = GRID_PADDING),
    ) {
        val iconDp =
            AppGrid.iconSizeFor(
                cellWidthDp = (maxWidth / columns).value.toInt(),
                cellHeightDp = (maxHeight / rows).value.toInt(),
                labelDp =
                    if (showLabels) {
                        (GRID_LABEL_LINE_DP * LocalDensity.current.fontScale).toInt() + GRID_CELL_CHROME_DP
                    } else {
                        GRID_CELL_CHROME_DP
                    },
                scale = iconScale,
            )
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (column in 0 until columns) {
                        val app = apps.getOrNull(row * columns + column)
                        if (app == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            GridCell(
                                app = app,
                                label = AppGrid.gridLabel(app.label, gridLabels),
                                iconDp = iconDp,
                                showLabel = showLabels,
                                onOpen = { onLaunchApp(app) },
                                onOpenSecond = { onLaunchSecond(app) },
                                onActions = { onOpenPeek(app) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(if (row == 0 && column == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A grid icon: the app's glyph (no tile) with its short name on one line
 * under it ([AppGrid.gridLabel]); focus fills the cell BB10 blue.
 */
@Composable
internal fun GridCell(
    app: AppEntry,
    label: String,
    iconDp: Int,
    onOpen: () -> Unit,
    onOpenSecond: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier =
            modifier
                .padding(GRID_CELL_GAP)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Bb10Blue.copy(alpha = 0.32f) else Color.Transparent)
                .border(2.dp, if (focused) Bb10Blue else Color.Transparent, shape)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val decision =
                        AllAppsKeyPolicy.decide(
                            keyCode = native.keyCode,
                            unicode = 0,
                            metaState = native.metaState,
                            repeat = native.repeatCount > 0,
                        )
                    val action: (() -> Unit)? =
                        when (decision.action) {
                            AllAppsKeyAction.OpenSecond -> onOpenSecond
                            AllAppsKeyAction.Actions, AllAppsKeyAction.ToggleDetail, AllAppsKeyAction.AppInfo -> onActions
                            else -> null
                        }
                    if (action != null && event.type == KeyEventType.KeyDown) action()
                    action != null
                }.combinedClickable(onClick = onOpen, onLongClick = onActions)
                .semantics {
                    contentDescription = app.label + (app.profileLabel?.let { ", ${AppProfiles.badge(it)}" } ?: "")
                }.padding(vertical = GRID_CELL_INNER_PADDING, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppMark(app = app, size = iconDp)
            val badge = app.profileLabel
            if (badge != null) {
                Text(
                    text = AppProfiles.badge(badge),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .border(1.dp, Bb10Muted, RoundedCornerShape(6.dp))
                            .padding(horizontal = 3.dp),
                    fontSize = 8.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        if (showLabel) Spacer(Modifier.height(GRID_ICON_LABEL_GAP))
        if (showLabel) {
            Text(
                text = label,
                // No tracking: "Minesweeper" fits a fifth of 411dp on one line.
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                color = Bb10Text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// A live datum's line, or how to reach its app when there is nothing live to show.
