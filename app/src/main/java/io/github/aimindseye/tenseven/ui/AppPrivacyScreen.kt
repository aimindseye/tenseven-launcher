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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
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

@Composable
internal fun SablePeekScreen(
    app: AppEntry,
    inBaseBar: Boolean?,
    hasSecondInstance: Boolean,
    onToggleBaseBar: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenSecond: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onUninstall: () -> Unit,
    /** The letter held on Home to open this app, or null. */
    letterShortcut: Char?,
    shortcutsAvailable: Boolean,
    onSetLetterShortcut: (Char?) -> Unit,
) {
    // Waiting for the letter to hold: a letter assigns, Backspace clears, "cancel" or Back leaves it.
    var capturing by remember(app) { mutableStateOf(false) }
    val actions =
        remember(app, inBaseBar, hasSecondInstance) {
            AppActionPolicy.actions(
                AppActionContext(
                    pinnedToStart = false,
                    // null when the app is in another profile; a full bar opens the slot picker.
                    inBaseBar = inBaseBar,
                    launcherUser = app.inLauncherUser,
                    canUninstall = app.canUninstall && app.inLauncherUser,
                    // The BB10 app grid holds every app: nothing to pin.
                    pinnable = false,
                    hasSecondInstance = hasSecondInstance,
                ),
            )
        }
    // UNINSTALL_ONE_KEY=NO: a destructive action first arms, then needs an explicit confirm.
    var pendingConfirm by remember(app) { mutableStateOf<AppAction?>(null) }
    val openFocus = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }
    LaunchedEffect(app) {
        withFrameNanos { }
        runCatching { openFocus.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (!capturing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val letter = SpeedDialKeys.letterFor(native.keyCode)
                    val chord =
                        native.metaState and
                            (
                                android.view.KeyEvent.META_ALT_ON or
                                    android.view.KeyEvent.META_CTRL_ON or
                                    android.view.KeyEvent.META_META_ON
                            ) != 0
                    when {
                        native.keyCode == android.view.KeyEvent.KEYCODE_DEL -> {
                            onSetLetterShortcut(null)
                            capturing = false
                            true
                        }

                        letter != null && !chord -> {
                            onSetLetterShortcut(letter)
                            capturing = false
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        Text(
            text = "Close",
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .heightIn(min = 48.dp)
                    .onFocusChanged { closeFocused = it.hasFocus }
                    .background(
                        if (closeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.background,
                    )
                    .clickable(onClick = onClose)
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(54.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            Spacer(Modifier.height(8.dp))
            PrivacyDetail(app = app)

            Spacer(Modifier.height(18.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .focusRequester(openFocus)
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
                Text(
                    text = "→",
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(6.dp))

            actions.filterNot { it == AppAction.Open }.forEach { action ->
                val confirming = pendingConfirm == action
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
                    onClick = {
                        if (AppActionPolicy.requiresConfirmation(action) && !confirming) {
                            pendingConfirm = action
                        } else {
                            pendingConfirm = null
                            when (action) {
                                AppAction.Open -> onOpen()
                                AppAction.OpenSecond -> onOpenSecond()
                                AppAction.AppInfo -> onOpenAppInfo()
                                AppAction.NotificationSettings -> onOpenNotificationSettings()
                                AppAction.PinToStart, AppAction.RemoveFromStart -> Unit
                                AppAction.Uninstall -> onUninstall()
                                AppAction.AddToBaseBar, AppAction.RemoveFromBaseBar -> onToggleBaseBar()
                                AppAction.Disable -> Unit
                            }
                        }
                    },
                )
                if (confirming) {
                    ContextAction(
                        mark = "×",
                        label = "cancel",
                        onClick = { pendingConfirm = null },
                    )
                }
            }

            if (shortcutsAvailable) {
                ContextAction(
                    mark = "⌨",
                    label =
                        if (letterShortcut != null) {
                            t("letter_shortcut_current", letterShortcut.uppercaseChar().toString())
                        } else {
                            t("letter_shortcut")
                        },
                    onClick = { capturing = !capturing },
                )
                if (capturing) {
                    Text(
                        text = t("letter_shortcut_press"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    ContextAction(
                        mark = "×",
                        label = "cancel",
                        onClick = { capturing = false },
                    )
                }
            }

            Text(
                text = t("shortcuts_and_notification_content_stay"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
