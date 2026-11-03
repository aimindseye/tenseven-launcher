package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.model.HomeViewport
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.frames.*
import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import io.github.aimindseye.tenseven.model.*
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Frame presentation receives immutable state and callbacks; task queries belong to ActiveFramesHost. */
@Composable
internal fun ActiveFramesPage(
    state: FramesState,
    apps: List<AppEntry>,
    hiddenSections: Set<String>,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    snapshot: LiveSurfaceSnapshot,
    refreshGeneration: Int,
    frameSnapshot: (FrameItem, Long) -> Bitmap?,
    active: Boolean,
    onResume: (FrameItem) -> Unit,
    onClose: (FrameItem) -> Unit,
    onShowHub: () -> Unit,
    onAllowUsageAccess: () -> Unit,
    onOpenNotificationApp: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val display = remember(context) { StartStateRepository(context) }
    val dials = remember(refreshGeneration) { display.loadHomeDials(HomeStyle.Bb10) }
    val strip = remember(refreshGeneration) { display.loadHomeNotificationStrip(HomeStyle.Bb10) }
    val expanded = remember(refreshGeneration) { display.loadHomeNotificationExpanded(HomeStyle.Bb10) }
    val summaryActions = remember(context) { io.github.aimindseye.tenseven.platform.AppFrameSummaryRepository(context) }
    val enabledApps = remember(refreshGeneration) { display.loadHubEnabledApps() }
    fun shown(section: String) = HomeLayout.isShown(hiddenSections, section)
    val capture by io.github.aimindseye.tenseven.platform.ScreenCaptureState.state.collectAsState()
    val owned = emptyList<FrameTileView>()
    Column(Modifier.fillMaxSize()) {
        if (shown(HomeLayout.STATUS_RINGS) && dials.isNotEmpty()) StatusDialsRow(onOpenSettings, dials)
        if (shown(HomeLayout.WEATHER)) {
            DateHeader(refreshGeneration, snapshot.hubApps.filter { it.packageName in enabledApps }.sumOf { it.count },
                onShowHub, !strip, if (dials.isEmpty()) onOpenSettings else null)
            if (strip) HomeNotificationStrip(snapshot.hubApps, enabledApps, expanded, onShowHub, onOpenApp = onOpenNotificationApp)
        }
        if (shown(HomeLayout.SEARCH_ROW)) Box(Modifier.padding(horizontal = 6.dp)) { SearchLaunchButton(onClick = onSearch) }
        // Put the access offer outside the grid so it never displaces one of its two visible rows.
        if (!state.usageAllowed) UsageAccessHint(onAllowUsageAccess)
        if (capture.running) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Shared screen · separate capture preview", color = Bb10Text, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { context.stopService(android.content.Intent(context,
                    io.github.aimindseye.tenseven.platform.ScreenCaptureService::class.java)) }) { Text("Stop sharing") }
            }
            capture.bitmap?.let { picture -> Image(picture.asImageBitmap(), "Shared screen preview",
                Modifier.fillMaxWidth().height(96.dp), contentScale = ContentScale.Fit) }
        }
        ActiveFramesGrid(state, apps, snapshot, enabledApps, owned, frameSnapshot, active, onResume, onClose,
            Modifier.weight(1f).fillMaxWidth(), onOpenSummary = summaryActions::open)
    }
}

internal data class FrameTileView(val key: String, val title: String, val detail: String,
    val picture: Bitmap? = null, val onClose: (() -> Unit)? = null,
    val onOpen: (() -> Unit)? = null, val badge: String = "Live tile")

@Composable
internal fun ActiveFramesGrid(
    state: FramesState,
    apps: List<AppEntry>,
    snapshot: LiveSurfaceSnapshot,
    enabledApps: Set<String>,
    ownedTiles: List<FrameTileView>,
    frameSnapshot: (FrameItem, Long) -> Bitmap?,
    active: Boolean,
    onResume: (FrameItem) -> Unit,
    onClose: (FrameItem) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSummary: (String) -> Unit = {},
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var nextFocus by remember { mutableStateOf<String?>(null) }
    val personalUser = android.os.Process.myUserHandle().hashCode()
    val sortedItems = state.items.sortedBy { if (FrameLiveSummary.detail(state, it, snapshot, personalUser) != null) 0 else 1 }
    val liveCount = sortedItems.count { FrameLiveSummary.detail(state, it, snapshot, personalUser) != null }
    val captureCount = ownedTiles.count { it.key == "tile:capture" }
    val reserved = liveCount.coerceAtMost((state.settings.maximum - captureCount).coerceAtLeast(0))
    val tiles = ownedTiles.take((state.settings.maximum - reserved).coerceAtLeast(0))
    val visibleItems = sortedItems.take((state.settings.maximum - tiles.size).coerceAtLeast(0))
    val keys = tiles.map { it.key } + visibleItems.map { it.key }
    val first = remember { FocusRequester() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    LaunchedEffect(active, keys) {
        requesters.keys.retainAll(keys.toSet())
        if (active) {
            repeat(2) { withFrameNanos { } }
            val key = nextFocus?.takeIf { it in keys } ?: keys.firstOrNull()
            nextFocus = null
            runCatching { if (key == null) first.requestFocus() else requester(key).requestFocus() }
        }
    }
    BoxWithConstraints(modifier.testTag("active-frames-grid")) {
        val fontScale = HomeViewport.textScale(
            androidx.compose.ui.platform.LocalDensity.current.fontScale)
        val columns = HomeViewport.frameColumns(maxWidth.value.toInt(), fontScale)
        val height = ((maxHeight - 18.dp) / 2).coerceAtLeast(72.dp * fontScale)
        if (keys.isEmpty()) {
            Text(state.fallbackReason, color = Bb10Muted, modifier = Modifier.padding(12.dp)
                .focusRequester(first).focusable().testTag("frames-empty"))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(keys.chunked(columns), key = { it.first() }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        val item = state.items.firstOrNull { it.key == key }
                        val slot = Modifier.weight(1f).height(height).focusRequester(requester(key))
                        if (item == null) {
                            val tile = tiles.first { it.key == key }
                            FrameTile(tile.key, tile.title, if (tile.picture != null) "Shared preview" else tile.badge, tile.detail, tile.onClose != null, { tile.onOpen?.invoke() },
                                { tile.onClose?.invoke() }, slot, tile.picture, openEnabled = tile.onOpen != null && state.settings.openApps)
                        } else {
                            RecentFrameCard(state, item, apps, snapshot, enabledApps, frameSnapshot, active,
                                {
                                    val summary = FrameLiveSummary.summary(state, item, snapshot, personalUser)
                                    if (summary != null && summary.source == SummarySource.Local) onOpenSummary(summary.key) else onResume(item)
                                }, {
                                    val index = keys.indexOf(key)
                                    nextFocus = keys.filterNot { it == key }.getOrNull(index.coerceAtMost(keys.size - 2).coerceAtLeast(0))
                                    onClose(item)
                                }, slot)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RecentFrameCard(
    state: FramesState, item: FrameItem, apps: List<AppEntry>, snapshot: LiveSurfaceSnapshot,
    enabledApps: Set<String>, frameSnapshot: (FrameItem, Long) -> Bitmap?, active: Boolean,
    onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    val app = apps.firstOrNull { it.component.packageName == item.app.packageName && it.user.hashCode() == item.app.userId &&
        (item.app.activityClass == null || LocalFrameApps.canonical(it.component.className) == item.app.activityClass) }
    val picture by produceState<Bitmap?>(null, item.key, state.generation, active, state.snapshotsAllowed) {
        value = null
        if (active && state.snapshotsAllowed) value = withContext(Dispatchers.IO) { frameSnapshot(item, state.generation) }
    }
    val count = FrameMessages.count(state, item, snapshot.hubApps, enabledApps, android.os.Process.myUserHandle().hashCode())
    val summary = FrameLiveSummary.detail(state, item, snapshot, android.os.Process.myUserHandle().hashCode())
    val badge = FrameLiveSummary.summary(state, item, snapshot, android.os.Process.myUserHandle().hashCode())?.source?.label ?: "Live summary"
    val renderedBadge = when {
        summary != null -> badge
        picture != null -> "Preview"
        else -> "Recent app"
    }
    FrameTile(item.key, app?.label ?: LocalFrameApps.title(item.app.activityClass) ?: item.app.packageName, renderedBadge,
        listOfNotNull(summary, if (count > 0) "$count new" else null).joinToString(" · "), true, onOpen, onClose, modifier, picture, state.settings.openApps,
        icon = { if (app != null) AppMark(app, 28) })
}

/** Shared card used by the real grid and Compose keyboard/layout tests. */
@Composable
internal fun FrameTile(
    key: String, title: String, badge: String, detail: String,
    closable: Boolean, onOpen: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, picture: Bitmap? = null, openEnabled: Boolean = true,
    icon: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.testTag(key).onFocusChanged { focused = it.hasFocus }.clip(shape).background(Bb10Card)
        .border(if (focused) 2.dp else 1.dp, if (focused) Bb10Blue else Bb10Line, shape)
        .onPreviewKeyEvent { event ->
            val close = closable && ActiveFrames.isCloseKey(event.nativeKeyEvent.keyCode, event.nativeKeyEvent.metaState)
            val open = openEnabled && event.nativeKeyEvent.keyCode in listOf(23, 66) &&
                event.nativeKeyEvent.metaState and (0x2 or 0x8 or 0x1000 or 0x10000) == 0
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                if (close) onClose() else if (open) onOpen()
            }
            close || open
        }.then(
            if (closable || openEnabled) Modifier.focusProperties { canFocus = true }
                .combinedClickable(onClick = { if (openEnabled) onOpen() }, onLongClick = if (closable) onClose else null)
            else Modifier.focusable()
        ).semantics { contentDescription = "$title, $badge" }) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Bb10Background), contentAlignment = Alignment.Center) {
            FrameContent(badge, detail, picture, icon)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).background(if (focused) Bb10Blue else Bb10BarTop).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (focused) Bb10OnBlue else Bb10Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (closable) Box(Modifier.size(48.dp).testTag("close:$key").clickable(onClick = onClose)
                .semantics { contentDescription = "Close $title card" }, contentAlignment = Alignment.Center) {
                Text("×", color = if (focused) Bb10OnBlue else Bb10Text)
            }
        }
    }
}

@Composable
private fun BoxScope.FrameContent(badge: String, detail: String, picture: Bitmap?,
    icon: @Composable () -> Unit) {
            if (picture != null) {
                Image(picture.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Text("Preview", color = Bb10Text, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).background(Bb10Background).padding(4.dp))
            }
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                icon()
                Text(badge, color = Bb10Text, style = MaterialTheme.typography.labelSmall)
                if (detail.isNotBlank()) Text(detail, color = Bb10Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)

            }
}
