package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.SpeedDialKeys
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.privacy.AppAction
import io.github.aimindseye.tenseven.privacy.AppActionContext
import io.github.aimindseye.tenseven.privacy.AppActionPolicy

private fun peekActionsFor(
    app: AppEntry,
    inBaseBar: Boolean?,
    hasSecondInstance: Boolean,
): List<AppAction> =
    AppActionPolicy.actions(
        AppActionContext(
            pinnedToStart = false,
            inBaseBar = inBaseBar,
            launcherUser = app.inLauncherUser,
            canUninstall = app.canUninstall && app.inLauncherUser,
            pinnable = false,
            hasSecondInstance = hasSecondInstance,
        ),
    )

private class PeekActionCallbacks(
    val onToggleBaseBar: () -> Unit,
    val onOpen: () -> Unit,
    val onOpenSecond: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onUninstall: () -> Unit,
)

private class PeekSheetModel(
    val app: AppEntry,
    val actions: List<AppAction>,
    val confirming: AppAction?,
    val letterShortcut: Char?,
    val shortcutsAvailable: Boolean,
    val capturing: Boolean,
)

private class PeekSheetEvents(
    val openFocus: FocusRequester,
    val onOpen: () -> Unit,
    val onAction: (AppAction) -> Unit,
    val onCancelConfirm: () -> Unit,
    val onToggleCapture: () -> Unit,
    val onCancelCapture: () -> Unit,
)

internal data class PeekAppState(
    val app: AppEntry,
    val inBaseBar: Boolean?,
    val hasSecondInstance: Boolean,
    val letterShortcut: Char?,
    val shortcutsAvailable: Boolean,
)

internal data class PeekNavigation(
    val onClose: () -> Unit,
    val onOpen: () -> Unit,
    val onOpenSecond: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
)

internal data class PeekEdits(
    val onToggleBaseBar: () -> Unit,
    val onUninstall: () -> Unit,
    val onSetLetterShortcut: (Char?) -> Unit,
)

@Suppress("FunctionNaming")
@Composable
internal fun SablePeekScreen(
    model: PeekAppState,
    navigation: PeekNavigation,
    edits: PeekEdits,
) {
    var capturing by remember(model.app) { mutableStateOf(false) }
    val actions =
        remember(model.app, model.inBaseBar, model.hasSecondInstance) {
            peekActionsFor(model.app, model.inBaseBar, model.hasSecondInstance)
        }
    var pendingConfirm by remember(model.app) { mutableStateOf<AppAction?>(null) }
    val openFocus = remember { FocusRequester() }
    LaunchedEffect(model.app) {
        withFrameNanos { }
        runCatching { openFocus.requestFocus() }
    }
    val callbacks =
        PeekActionCallbacks(
            edits.onToggleBaseBar,
            navigation.onOpen,
            navigation.onOpenSecond,
            navigation.onOpenAppInfo,
            navigation.onOpenNotificationSettings,
            edits.onUninstall,
        )
    val events =
        PeekSheetEvents(
            openFocus = openFocus,
            onOpen = navigation.onOpen,
            onAction = { action ->
                if (AppActionPolicy.requiresConfirmation(action) && pendingConfirm != action) {
                    pendingConfirm = action
                } else {
                    pendingConfirm = null
                    dispatchPeekAction(action, callbacks)
                }
            },
            onCancelConfirm = { pendingConfirm = null },
            onToggleCapture = { capturing = !capturing },
            onCancelCapture = { capturing = false },
        )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                peekCaptureKey(event, capturing, edits.onSetLetterShortcut) { capturing = false }
            },
    ) {
        PeekCloseButton(navigation.onClose)
        PeekSheet(
            PeekSheetModel(
                model.app,
                actions,
                pendingConfirm,
                model.letterShortcut,
                model.shortcutsAvailable,
                capturing,
            ),
            events,
        )
    }
}

private fun peekCaptureKey(
    event: KeyEvent,
    capturing: Boolean,
    onSetLetterShortcut: (Char?) -> Unit,
    onStopCapture: () -> Unit,
): Boolean {
    val native = event.nativeKeyEvent
    if (!capturing || event.type != KeyEventType.KeyDown) return false
    val letter = SpeedDialKeys.letterFor(native.keyCode)
    val chord =
        native.metaState and (
            android.view.KeyEvent.META_ALT_ON or android.view.KeyEvent.META_CTRL_ON or
                android.view.KeyEvent.META_META_ON
        ) != 0
    return when {
        native.keyCode == android.view.KeyEvent.KEYCODE_DEL -> {
            onSetLetterShortcut(null)
            onStopCapture()
            true
        }

        letter != null && !chord -> {
            onSetLetterShortcut(letter)
            onStopCapture()
            true
        }

        else -> {
            false
        }
    }
}

private fun dispatchPeekAction(
    action: AppAction,
    callbacks: PeekActionCallbacks,
) {
    when (action) {
        AppAction.Open -> callbacks.onOpen()
        AppAction.OpenSecond -> callbacks.onOpenSecond()
        AppAction.AppInfo -> callbacks.onOpenAppInfo()
        AppAction.NotificationSettings -> callbacks.onOpenNotificationSettings()
        AppAction.PinToStart, AppAction.RemoveFromStart -> Unit
        AppAction.Uninstall -> callbacks.onUninstall()
        AppAction.AddToBaseBar, AppAction.RemoveFromBaseBar -> callbacks.onToggleBaseBar()
        AppAction.Disable -> Unit
    }
}

@Suppress("FunctionNaming")
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PeekCloseButton(onClose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = "Close",
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .heightIn(min = 48.dp)
                .onFocusChanged { focused = it.hasFocus }
                .background(
                    if (focused) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                ).clickable(onClick = onClose)
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PeekSheet(
    model: PeekSheetModel,
    events: PeekSheetEvents,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Box(Modifier.width(54.dp).height(4.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(18.dp))
        PeekAppHeader(model.app)
        Spacer(Modifier.height(8.dp))
        PrivacyDetail(app = model.app)
        Spacer(Modifier.height(18.dp))
        PeekOpenAction(events.openFocus, events.onOpen)
        Spacer(Modifier.height(6.dp))
        PeekContextActions(model, events.onAction, events.onCancelConfirm)
        PeekShortcutActions(model, events.onToggleCapture, events.onCancelCapture)
        Text(
            text = t("shortcuts_and_notification_content_stay"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PeekAppHeader(app: AppEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(app = app, size = 58)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t("privacy_security"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                app.profileLabel?.let { profile ->
                    Spacer(Modifier.width(6.dp))
                    ProfileBadge(profile)
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PeekOpenAction(
    focus: FocusRequester,
    onOpen: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .focusRequester(focus)
                .sableFocusRing()
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "open",
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(text = "→", fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PeekContextActions(
    model: PeekSheetModel,
    onAction: (AppAction) -> Unit,
    onCancelConfirm: () -> Unit,
) {
    model.actions.filterNot { it == AppAction.Open }.forEach { action ->
        val confirming = model.confirming == action
        ContextAction(
            mark =
                when (action) {
                    AppAction.PinToStart, AppAction.AddToBaseBar -> "+"
                    AppAction.RemoveFromStart, AppAction.RemoveFromBaseBar -> "−"
                    AppAction.AppInfo -> "ⓘ"
                    AppAction.NotificationSettings -> "◔"
                    AppAction.Uninstall, AppAction.Disable -> "⌫"
                    AppAction.Open, AppAction.OpenSecond -> "→"
                },
            label = if (confirming) AppActionPolicy.confirmationLabel(action) else action.label,
            onClick = { onAction(action) },
        )
        if (confirming) ContextAction(mark = "×", label = "cancel", onClick = onCancelConfirm)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PeekShortcutActions(
    model: PeekSheetModel,
    onToggleCapture: () -> Unit,
    onCancelCapture: () -> Unit,
) {
    if (model.shortcutsAvailable) {
        ContextAction(
            mark = "⌨",
            label =
                if (model.letterShortcut != null) {
                    t("letter_shortcut_current", model.letterShortcut.uppercaseChar().toString())
                } else {
                    t("letter_shortcut")
                },
            onClick = onToggleCapture,
        )
        if (model.capturing) {
            Text(
                text = t("letter_shortcut_press"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            ContextAction(mark = "×", label = "cancel", onClick = onCancelCapture)
        }
    }
}
