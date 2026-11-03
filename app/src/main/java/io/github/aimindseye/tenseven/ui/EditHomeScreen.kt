package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.HomeApps
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.WidgetTarget
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy

internal enum class EditSection {
    Grid,
    QuickBar,
    Glance,
}

/** The item picked up with Enter, and the layout before the pick (Back restores it). */
internal data class EditMove(
    val section: EditSection,
    val key: String,
    val before: HomeEdit,
)

internal fun HomeEdit.list(section: EditSection): List<String> =
    when (section) {
        EditSection.Grid -> gridOrder
        EditSection.QuickBar -> quickBar
        EditSection.Glance -> glanceWidgets
    }

internal fun HomeEdit.with(
    section: EditSection,
    list: List<String>,
): HomeEdit =
    when (section) {
        EditSection.Grid -> copy(gridOrder = list)
        EditSection.QuickBar -> copy(quickBar = list)
        EditSection.Glance -> copy(glanceWidgets = list)
    }

/** Moves [key] by [delta] places in [section]: grid icons shift like a dragged icon, list items swap. */
internal fun HomeEdit.moved(
    section: EditSection,
    key: String,
    delta: Int,
): HomeEdit {
    val list = list(section)
    val index = list.indexOf(key)
    val next =
        when (section) {
            EditSection.Grid -> AppGrid.move(list, index, delta)
            EditSection.QuickBar -> QuickBar.move(list, index, delta)
            EditSection.Glance -> HomeApps.move(list, index, delta)
        }
    return with(section, next)
}

/**
 * Edit Home: one mode that orders the app grid, the bottom bar and the Glance
 * widgets shown as frames while no app runs, and shows or hides the Active
 * Frames header (status dials, weather and date, search row). Drag, or with
 * the keyboard: Enter picks an item up and drops it, arrows move it while
 * picked up (all four on the grid, Left/Right on the bar, Up/Down in the
 * list), Alt+arrows (Fn+arrows) move the focused item at once, Menu or Space
 * opens its options, Back cancels the move or leaves without saving, Done
 * saves.
 */
@Composable
internal fun EditHomeScreen(
    showFrameOptions: Boolean,
    apps: List<AppEntry>,
    initial: HomeEdit,
    defaults: HomeEdit,
    backGeneration: Int,
    onCancel: () -> Unit,
    onDone: (HomeEdit) -> Unit,
    onAppearance: () -> Unit,
) {
    val appByKey = apps.associateBy { it.stableKey() }
    var draft by remember { mutableStateOf(initial) }
    var moving by remember { mutableStateOf<EditMove?>(null) }
    // "<section>:<key>" whose options are open.
    var optionsFor by remember { mutableStateOf<String?>(null) }
    // null: editing; "bar": adding a slot; "bar:<index>": replacing that slot.
    var picking by remember { mutableStateOf<String?>(null) }
    val startBack = remember { backGeneration }
    val doneFocus = remember { FocusRequester() }
    val hosted = LocalHomeWidgets.current
    var pickingWidget by remember { mutableStateOf(false) }

    LaunchedEffect(backGeneration) {
        if (backGeneration > startBack) {
            val move = moving
            when {
                picking != null -> {
                    picking = null
                }

                move != null -> {
                    draft = move.before
                    moving = null
                }

                optionsFor != null -> {
                    optionsFor = null
                }

                else -> {
                    onCancel()
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { doneFocus.requestFocus() }
    }

    fun move(
        section: EditSection,
        key: String,
        delta: Int,
    ) {
        draft = draft.moved(section, key, delta)
    }

    fun itemKeys(
        section: EditSection,
        key: String,
        event: KeyEvent,
    ): Boolean {
        val native = event.nativeKeyEvent
        val down = event.type == KeyEventType.KeyDown
        val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
        val picked = moving?.let { it.section == section && it.key == key } == true
        val arrow = AppGrid.arrowDelta(native.keyCode)
        // Grid: all four arrows; bar: Left/Right; list: Up/Down (one place).
        val sectionDelta =
            when (section) {
                EditSection.Grid -> arrow
                EditSection.QuickBar -> arrow?.takeIf { it == -1 || it == 1 }
                EditSection.Glance -> arrow?.takeIf { it == -AppGrid.COLUMNS || it == AppGrid.COLUMNS }?.let { it / AppGrid.COLUMNS }
            }
        val delta = sectionDelta?.takeIf { alt || picked }
        val optionsKey = "${section.name}:$key"
        return when {
            delta != null -> {
                if (down) move(section, key, delta)
                true
            }

            native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && !alt -> {
                if (down && native.repeatCount == 0) {
                    moving = if (picked) null else EditMove(section, key, draft)
                }
                true
            }

            native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU ||
                native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE ||
                (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt) -> {
                if (down && native.repeatCount == 0) optionsFor = if (optionsFor == optionsKey) null else optionsKey
                true
            }

            else -> {
                false
            }
        }
    }

    val pickKind = picking
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            if (pickKind != null) {
                val choices = quickBarChoices(apps, appByKey, draft.quickBar)
                item {
                    MetroHeader(
                        title = if (pickKind == "bar") t("add_a_slot") else t("replace_slot"),
                        onBack = { picking = null },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (choices.isEmpty()) {
                    item { EmptyState(t("every_available_app_is_already")) }
                }
                items(choices, key = { it.token }) { choice ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .sableFocusRing()
                                .clickable {
                                    draft =
                                        if (pickKind == "bar") {
                                            draft.copy(quickBar = QuickBar.add(draft.quickBar, choice.token))
                                        } else {
                                            val index = pickKind.substringAfter(':').toIntOrNull() ?: -1
                                            draft.copy(quickBar = QuickBar.replace(draft.quickBar, index, choice.token))
                                        }
                                    picking = null
                                }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuickSlotMark(slot = choice, size = 32)
                        Spacer(Modifier.width(14.dp))
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                return@LazyColumn
            }

            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).width(48.dp)) {
                        Text(text = "‹", fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
                    Text(
                        text = t("edit_home"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                Text(
                    text = t("drag_or_use_keyboard_order"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = t("enter_picks_up_and_drops"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SectionLabel(t("app_grid"))
                Spacer(Modifier.height(4.dp))
            }
            items(draft.gridOrder.chunked(AppGrid.COLUMNS), key = { row -> "grid:" + row.first() }) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        val app = appByKey[key]
                        EditGridCell(
                            app = app,
                            picked = moving?.let { it.section == EditSection.Grid && it.key == key } == true,
                            onKey = { event -> itemKeys(EditSection.Grid, key, event) },
                            onClick = { optionsFor = if (optionsFor == "Grid:$key") null else "Grid:$key" },
                            onDragStep = { delta -> move(EditSection.Grid, key, delta) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(AppGrid.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
                val open = optionsFor?.takeIf { it.startsWith("Grid:") }?.substringAfter(':')
                if (open != null && open in row) {
                    val index = draft.gridOrder.indexOf(open)
                    EditOptions(
                        buildList {
                            if (index > 0) add(t("move_left") to { move(EditSection.Grid, open, -1) })
                            if (index < draft.gridOrder.lastIndex) add(t("move_right") to { move(EditSection.Grid, open, 1) })
                            if (index >= AppGrid.COLUMNS) add(t("move_up_a_row") to { move(EditSection.Grid, open, -AppGrid.COLUMNS) })
                            if (index + AppGrid.COLUMNS <= draft.gridOrder.lastIndex) {
                                add(t("move_down_a_row") to { move(EditSection.Grid, open, AppGrid.COLUMNS) })
                            }
                            add(t("move_to_the_start") to { move(EditSection.Grid, open, -index) })
                        },
                    )
                }
            }
            item(key = "bar") {
                Spacer(Modifier.height(10.dp))
                SectionLabel(t("bottom_bar"))
                Spacer(Modifier.height(6.dp))
                EditQuickBarRow(
                    tokens = draft.quickBar,
                    apps = apps,
                    appByKey = appByKey,
                    moving = moving?.takeIf { it.section == EditSection.QuickBar }?.key,
                    onKey = { token, event -> itemKeys(EditSection.QuickBar, token, event) },
                    onClick = { token ->
                        optionsFor = if (optionsFor == "QuickBar:$token") null else "QuickBar:$token"
                    },
                    onDragStep = { token, delta -> move(EditSection.QuickBar, token, delta) },
                )
                val barOptions = optionsFor?.takeIf { it.startsWith("QuickBar:") }?.substringAfter(':')
                if (barOptions != null && barOptions in draft.quickBar) {
                    val index = draft.quickBar.indexOf(barOptions)
                    val required = QuickBar.isRequired(barOptions)
                    EditOptions(
                        buildList {
                            if (index > 0) add(t("move_left") to { move(EditSection.QuickBar, barOptions, -1) })
                            if (index < draft.quickBar.lastIndex) add(t("move_right") to { move(EditSection.QuickBar, barOptions, 1) })
                            if (!required) {
                                add(
                                    "replace" to {
                                        picking = "bar:$index"
                                        optionsFor = null
                                    },
                                )
                                add(
                                    "remove" to {
                                        draft = draft.copy(quickBar = QuickBar.remove(draft.quickBar, index))
                                        optionsFor = null
                                    },
                                )
                            }
                        },
                    )
                    if (required) {
                        Text(
                            text = t("search_always_stays_on_the"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (draft.quickBar.size < QuickBar.MAX_SLOTS) {
                    MetroLink(text = t("add_a_slot_2"), onClick = { picking = "bar" })
                }
                MetroLink(
                    text = t("reset_bar_to_phone_search"),
                    onClick = { draft = draft.copy(quickBar = QuickBar.DEFAULT) },
                )
            }
            if (showFrameOptions) {
                item(key = "frames-options") {
                    Spacer(Modifier.height(10.dp))
                    SectionLabel(t("active_frames_header"))
                    Spacer(Modifier.height(4.dp))
                    val accent = MaterialTheme.colorScheme.primary
                    HomeLayout.SECTIONS.forEach { section ->
                        HomeToggleRow(
                            title = HomeLayout.label(section),
                            subtitle = HomeLayout.description(section),
                            selected = HomeLayout.isShown(draft.hiddenSections, section),
                            accent = accent,
                            onClick = { draft = draft.copy(hiddenSections = HomeLayout.toggle(draft.hiddenSections, section)) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MetroLink(text = "appearance", onClick = onAppearance)
                }
            }
        }
        if (pickKind == null) {
            EditBar(
                onReset = { draft = defaults },
                onCancel = onCancel,
                onDone = { onDone(draft) },
                doneFocus = doneFocus,
            )
        }
        if (pickingWidget && hosted.controller != null) {
            WidgetPicker(
                providers = hosted.controller.providers(),
                onPick = {
                    pickingWidget = false
                    hosted.controller.begin(it, WidgetTarget.HOME)
                },
                onDismiss = { pickingWidget = false },
            )
        }
    }
}

/**
 * The bar that stays at the bottom of Edit Home however far the list is scrolled: Reset puts the layout back to
 * the default, Cancel leaves without saving, Done saves. It is what tells you the screen is in edit mode.
 */
@Composable
internal fun EditBar(
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    doneFocus: FocusRequester,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onReset, modifier = Modifier.sableFocusRing()) { Text(stringResource(R.string.edit_reset)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel, modifier = Modifier.sableFocusRing()) { Text(stringResource(R.string.edit_cancel)) }
        TextButton(onClick = onDone, modifier = Modifier.focusRequester(doneFocus).sableFocusRing()) {
            Text(t("done"), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Widgets from other apps on the home screen: each with Up, Down and Remove, and Add. These apply at once, not on Done. */
@Composable
internal fun HostedWidgetsEditor(
    hosted: HomeWidgets,
    onAdd: () -> Unit,
) {
    val controller = hosted.controller ?: return
    val packageManager = LocalContext.current.packageManager
    Spacer(Modifier.height(10.dp))
    SectionLabel(stringResource(R.string.edit_widgets))
    Text(
        text = stringResource(R.string.edit_widgets_detail),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    hosted.ids.forEach { id ->
        val name = controller.info(id)?.let { runCatching { it.loadLabel(packageManager) }.getOrNull() } ?: "#$id"
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(name.toString(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            PageButton(stringResource(R.string.widgets_up)) { controller.move(id, -1) }
            PageButton(stringResource(R.string.widgets_down)) { controller.move(id, 1) }
            PageButton(stringResource(R.string.widgets_remove)) { controller.remove(id) }
        }
    }
    MetroLink(text = stringResource(R.string.widgets_add), onClick = onAdd)
}

/** One app in the Edit Home grid: Enter picks it up, arrows move it, or drag it in any direction. */
@Composable
internal fun EditGridCell(
    app: AppEntry?,
    picked: Boolean,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    onDragStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = with(LocalDensity.current) { EditRowStep.toPx() }
    val currentStep by rememberUpdatedState(onDragStep)
    val label = app?.label ?: t("app_not_installed")
    Column(
        modifier =
            modifier
                .padding(3.dp)
                .heightIn(min = 84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .sableFocusRing()
                .onPreviewKeyEvent(onKey)
                .clickable(onClick = onClick)
                .pointerInput(Unit) {
                    var travelX = 0f
                    var travelY = 0f
                    detectDragGestures(onDragStart = {
                        travelX = 0f
                        travelY = 0f
                    }) { change, amount ->
                        change.consume()
                        travelX += amount.x
                        travelY += amount.y
                        while (travelX > step) {
                            currentStep(1)
                            travelX -= step
                        }
                        while (travelX < -step) {
                            currentStep(-1)
                            travelX += step
                        }
                        while (travelY > step) {
                            currentStep(AppGrid.COLUMNS)
                            travelY -= step
                        }
                        while (travelY < -step) {
                            currentStep(-AppGrid.COLUMNS)
                            travelY += step
                        }
                    }
                }.semantics { contentDescription = t("enter_picks_up_arrows_move", label) }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (app != null) AppMark(app = app, size = 32)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A reorderable row: drag the handle, or the keys in [EditHomeScreen]. */
@Composable
internal fun EditListRow(
    label: String,
    leading: @Composable () -> Unit,
    picked: Boolean,
    onKey: (KeyEvent) -> Boolean,
    onClick: () -> Unit,
    onDragStep: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val step = with(LocalDensity.current) { EditRowStep.toPx() }
    val currentStep by rememberUpdatedState(onDragStep)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .sableFocusRing()
                .onPreviewKeyEvent(onKey)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "⠿",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .pointerInput(Unit) {
                        var travel = 0f
                        detectDragGestures(onDragStart = { travel = 0f }) { change, amount ->
                            change.consume()
                            travel += amount.y
                            while (travel > step) {
                                currentStep(1)
                                travel -= step
                            }
                            while (travel < -step) {
                                currentStep(-1)
                                travel += step
                            }
                        }
                    }.semantics { contentDescription = t("drag_to_reorder", label) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

/** The base quick bar in Edit Home: slots in a row, dragged sideways or moved with Left/Right. */
@Composable
internal fun EditQuickBarRow(
    tokens: List<String>,
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    moving: String?,
    onKey: (String, KeyEvent) -> Boolean,
    onClick: (String) -> Unit,
    onDragStep: (String, Int) -> Unit,
) {
    val step = with(LocalDensity.current) { EditRowStep.toPx() }
    val currentStep by rememberUpdatedState(onDragStep)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tokens.forEach { token ->
            key(token) {
                val slot = resolveQuickSlot(token, apps, appByKey)
                val picked = token == moving
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (picked) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .sableFocusRing()
                            .onPreviewKeyEvent { event -> onKey(token, event) }
                            .clickable { onClick(token) }
                            .pointerInput(token) {
                                var travel = 0f
                                detectDragGestures(onDragStart = { travel = 0f }) { change, amount ->
                                    change.consume()
                                    travel += amount.x
                                    while (travel > step) {
                                        currentStep(token, 1)
                                        travel -= step
                                    }
                                    while (travel < -step) {
                                        currentStep(token, -1)
                                        travel += step
                                    }
                                }
                            }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (slot != null) QuickSlotMark(slot = slot, size = 24)
                    Text(
                        text = slot?.label ?: quickSlotFallbackLabel(token),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Inline options under an Edit Home item. */
@Composable
internal fun EditOptions(options: List<Pair<String, () -> Unit>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 4.dp)) {
        options.forEach { (label, action) ->
            Text(
                text = label,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .sableFocusRing()
                        .clickable(onClick = action)
                        .padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun HomeToggleRow(
    title: String,
    subtitle: String?,
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
                .semantics {
                    contentDescription = "$title, " + if (selected) "on" else "off"
                }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (selected) "✓" else "",
            fontSize = 20.sp,
            color = accent,
            modifier = Modifier.width(28.dp),
        )
    }
}

/** Role slots first (Phone, Camera, Hub, All apps), then every other personal app A to Z. */
