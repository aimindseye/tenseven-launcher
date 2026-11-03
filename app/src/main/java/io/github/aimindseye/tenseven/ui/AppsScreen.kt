package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.design.SableAlphabetIndex
import io.github.aimindseye.tenseven.design.SableAlphabetRail
import io.github.aimindseye.tenseven.design.SableAlphabetRailWidth
import io.github.aimindseye.tenseven.design.SableFocusMemory
import io.github.aimindseye.tenseven.design.SableTypeToJump
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.privacy.AllAppsKeyAction
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy
import io.github.aimindseye.tenseven.privacy.PrivacyFilter
import kotlinx.coroutines.launch

@Composable
internal fun AppsScreen(
    apps: List<AppEntry>,
    focusMemory: SableFocusMemory,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLaunchSecond: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    privateSpaceLocked: Boolean?,
    onTogglePrivateSpace: () -> Unit,
) {
    // Private Space apps live in their own section at the end, never in the A-Z list.
    val (privateApps, listedApps) = remember(apps) { apps.partition { it.profileLabel == PROFILE_PRIVATE_LABEL } }
    var filter by remember { mutableStateOf(PrivacyFilter.All) }
    val rows = remember(listedApps, filter) { buildRailRows(listedApps.filter { filter.matches(it.privacy) }) }
    val keys = remember(rows) { rows.map { it.app.stableKey() } }
    val labels = remember(rows) { rows.map { it.app.label } }
    // One alphabet-index concept: section headers, the right-hand rail and
    // keyboard letters all come from this index (no second, hidden rail).
    val alphabet = remember(labels) { SableAlphabetIndex.build(labels) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val typeToJump = remember { SableTypeToJump() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedIndex by remember { mutableStateOf(-1) }
    var expandedKeys by remember { mutableStateOf(emptySet<String>()) }
    var restoreDone by remember { mutableStateOf(false) }

    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun focusIndex(index: Int) {
        val key = keys.getOrNull(index) ?: return
        scope.launch {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { requesterFor(key).requestFocus() }
        }
    }

    // FOCUS_RESTORATION: back from Peek / App info / Search lands on the same app
    // (or the app now at its old position if it was removed). Runs once per visit
    // so inventory refreshes never move focus under the user.
    LaunchedEffect(keys.isNotEmpty()) {
        if (!restoreDone && keys.isNotEmpty()) {
            restoreDone = true
            focusMemory.restore(FOCUS_SURFACE_ALL_APPS, keys)?.let(::focusIndex)
        }
    }

    fun openActions(index: Int) {
        val row = rows.getOrNull(index) ?: return
        focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
        onOpenPeek(row.app)
    }

    fun onRowKey(
        index: Int,
        event: KeyEvent,
    ): Boolean {
        val native = event.nativeKeyEvent
        val decision =
            AllAppsKeyPolicy.decide(
                keyCode = native.keyCode,
                unicode = native.unicodeChar,
                metaState = native.metaState,
                repeat = native.repeatCount > 0,
            )
        if (!decision.consumed) return false
        // Act on key down only; swallow the matching key up so clickable does not fire twice.
        if (event.type != KeyEventType.KeyDown) return true
        val row = rows.getOrNull(index) ?: return true
        when (decision.action) {
            AllAppsKeyAction.Open -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onLaunchApp(row.app)
            }
            AllAppsKeyAction.OpenSecond -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onLaunchSecond(row.app)
            }
            AllAppsKeyAction.ToggleDetail -> {
                val key = keys[index]
                expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
            }
            AllAppsKeyAction.Actions -> openActions(index)
            AllAppsKeyAction.AppInfo -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onOpenAppInfo(row.app)
            }
            AllAppsKeyAction.Search -> {
                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, keys[index], index)
                onSearch()
            }
            AllAppsKeyAction.TypeToJump -> {
                val target =
                    typeToJump.onChar(decision.char, SystemClock.uptimeMillis(), labels, index)
                        ?: alphabet.indexForChar(decision.char)
                if (target != null) focusIndex(target)
            }
            AllAppsKeyAction.IgnoreRepeat, AllAppsKeyAction.PassThrough -> Unit
        }
        return true
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 18.dp, bottom = 18.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = if (alphabet.isEmpty) 20.dp else SableAlphabetRailWidth + 4.dp),
        ) {
            MetroHeader(
                title = t("all_apps_2"),
                onBack = onBack,
            )
            Spacer(Modifier.height(8.dp))
            SearchLaunchButton(onClick = onSearch)
            Spacer(Modifier.height(6.dp))
            PrivacyFilterChips(selected = filter, onSelect = { filter = it })
            Spacer(Modifier.height(8.dp))

            if (rows.isEmpty() && filter != PrivacyFilter.All) {
                EmptyState(t("no_apps_match", filter.label))
            } else if (rows.isEmpty() && privateSpaceLocked == null) {
                EmptyState(t("no_launchable_apps_are_available"))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.app.stableKey() },
                    ) { index, row ->
                        if (row.startsSection) {
                            Text(
                                text = row.section,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .padding(top = 12.dp, bottom = 4.dp)
                                        .semantics { heading() },
                            )
                        }
                        val key = keys[index]
                        AppRow(
                            app = row.app,
                            expanded = key in expandedKeys,
                            onClick = {
                                focusMemory.remember(FOCUS_SURFACE_ALL_APPS, key, index)
                                onLaunchApp(row.app)
                            },
                            onContext = { openActions(index) },
                            modifier =
                                Modifier
                                    .focusRequester(requesterFor(key))
                                    .onFocusChanged { state ->
                                        if (state.hasFocus) {
                                            focusedIndex = index
                                            focusMemory.remember(FOCUS_SURFACE_ALL_APPS, key, index)
                                        }
                                    }.onPreviewKeyEvent { event -> onRowKey(index, event) },
                        )
                    }
                    if (privateSpaceLocked != null) {
                        item(key = "private-space") {
                            PrivateSpaceSection(
                                locked = privateSpaceLocked,
                                apps = privateApps,
                                onToggle = onTogglePrivateSpace,
                                onLaunchApp = onLaunchApp,
                                onOpenPeek = onOpenPeek,
                            )
                        }
                    }
                }
            }
        }

        if (!alphabet.isEmpty) {
            val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
            SableAlphabetRail(
                index = alphabet,
                activeKey = alphabet.sectionKeyAt(if (focusedIndex >= 0) focusedIndex else firstVisible),
                onJump = { target ->
                    scope.launch { listState.animateScrollToItem(target) }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * All Apps privacy filters (Sensitive · All · No sensitive): Left/Right move
 * between the chips, Enter applies; the applied chip is filled.
 */
@Composable
internal fun PrivacyFilterChips(
    selected: PrivacyFilter,
    onSelect: (PrivacyFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrivacyFilter.entries.forEach { chip ->
            val on = chip == selected
            Text(
                text = chip.label,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .sableFocusRing()
                        .clickable { onSelect(chip) }
                        .semantics { contentDescription = t("filter", chip.label) + if (on) t("applied") else "" }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Text(
        text = t("filters_enter_applies"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Private Space at the end of All Apps: one row that locks or unlocks it
 * (the system asks for its credential), and its apps only while unlocked.
 */
@Composable
internal fun PrivateSpaceSection(
    locked: Boolean,
    apps: List<AppEntry>,
    onToggle: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            text = t("private_space"),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .sableFocusRing()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (locked) "🔒" else "🔓",
                modifier = Modifier.width(38.dp),
                fontSize = 20.sp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (locked) t("unlock_private_space") else t("lock_private_space"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text =
                        if (locked) {
                            t("its_apps_stay_hidden_and")
                        } else {
                            t("apps_locking_stops_them_and", apps.size)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!locked) {
            apps.forEach { app ->
                AppRow(
                    app = app,
                    onClick = { onLaunchApp(app) },
                    onContext = { onOpenPeek(app) },
                )
            }
        }
    }
}

/**
 * Command / Search (sable-command-titan2). One field; a Best match row on top;
 * then grouped results (apps, permission matches, people, settings, Hub,
 * actions). Up/Down move through one result list, Left/Right on a result (or
 * at either end of the query) move between the group chips, Enter opens,
 * Shift+Enter opens an app's Dual copy, Menu or Alt+Enter shows the result's
 * actions, Back closes. `/private`, `/dual` and `/work` list one profile's
 * apps; a locked Private Space shows the locked state with Unlock.
 */
