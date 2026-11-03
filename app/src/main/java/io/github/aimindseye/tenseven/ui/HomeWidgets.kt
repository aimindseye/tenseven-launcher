package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.platform.CapabilityStatus
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveDatum
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import io.github.aimindseye.tenseven.model.ActiveFrames
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.Capabilities
import io.github.aimindseye.tenseven.platform.DefaultApps
import io.github.aimindseye.tenseven.platform.NextAlarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BuiltInWidgets(
    widgets: List<String>,
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    refreshGeneration: Int,
    onLaunchApp: (AppEntry) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onRequestPermissions: () -> Unit,
    onShowHub: () -> Unit,
) {
    val context = LocalContext.current
    val battery by
        produceState<Pair<Int?, Boolean>>(initialValue = null to false, refreshGeneration) {
            value = withContext(Dispatchers.IO) { readBattery(context) }
        }

    fun open(packageName: String) = findLiveApp(apps, packageName)?.let(onLaunchApp)

    fun openDefault(category: String) {
        if (!DefaultApps.launch(context, category)) {
            Toast.makeText(context, context.getString(R.string.default_app_missing), Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(FRAME_GAP)) {
        widgets.chunked(ActiveFrames.COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FRAME_GAP)) {
                row.forEach { widget ->
                    val modifier =
                        Modifier
                            .weight(1f)
                            .height(BUILT_IN_WIDGET_HEIGHT)
                    when (widget) {
                        GlanceWidgets.ALARM -> {
                            val alarm = remember { NextAlarm.text(context) }
                            GlanceFrameCard(
                                title = GlanceWidgets.label(GlanceWidgets.ALARM),
                                headline = alarm ?: "No alarm",
                                detail = "",
                                accent = SableSun,
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                },
                                modifier = modifier,
                            )
                        }

                        GlanceWidgets.BATTERY -> {
                            BatteryGlanceFrame(battery, onStartIntent, modifier)
                        }

                        GlanceWidgets.PHOTOS -> {
                            val datum = snapshot.photos
                            val needsPermission = datum.availability == LiveAvailability.PERMISSION_REQUIRED
                            GlanceFrameCard(
                                title = t("photos"),
                                headline = if (needsPermission) t("photos") else datum.detail,
                                detail = if (needsPermission) t("enter_to_allow_local_photo") else "",
                                accent = SablePurple,
                                onClick = { if (needsPermission) onRequestPermissions() else openDefault(DefaultApps.GALLERY) },
                                modifier = modifier,
                            )
                        }

                        else -> {
                            LiveGlanceFrame(
                                widget = widget,
                                snapshot = snapshot,
                                openDefault = ::openDefault,
                                onStartIntent = onStartIntent,
                                onShowHub = onShowHub,
                                modifier = modifier,
                            )
                        }
                    }
                }
                repeat(ActiveFrames.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The Battery Glance frame: level and charging state; Enter opens battery usage. */
@Composable
internal fun BatteryGlanceFrame(
    battery: Pair<Int?, Boolean>,
    onStartIntent: (Intent) -> Unit,
    modifier: Modifier,
) {
    val (level, charging) = battery
    GlanceFrameCard(
        title = t("battery"),
        headline = level?.let { "$it%" } ?: "--",
        detail =
            if (level == null) {
                "unavailable"
            } else if (charging) {
                "charging"
            } else {
                ""
            },
        accent = SableGreen,
        onClick = { onStartIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
        modifier = modifier,
    )
}

/**
 * Calendar, Media, Hub and Mail Glance frames. Calendar shows the next event from the phone's calendar and Media
 * what is playing; each opens the phone's default app for it. Where a line needs a permission the frame asks for it.
 */
@Composable
internal fun LiveGlanceFrame(
    widget: String,
    snapshot: LiveSurfaceSnapshot,
    openDefault: (String) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onShowHub: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val (datum, accent) =
        when (widget) {
            GlanceWidgets.CALENDAR -> snapshot.calendar to SableBlue
            GlanceWidgets.MEDIA -> snapshot.music to SablePurple
            GlanceWidgets.HUB -> snapshot.messages to SableGreen
            else -> snapshot.mail to SableOrange
        }
    // Calendar asks for a runtime permission; Now playing and the Hub need the notification listener, which the lite build lacks.
    val listenerMissing =
        widget == GlanceWidgets.MEDIA &&
            rememberPlatformCapabilities().hubNotifications == CapabilityStatus.Unavailable
    val needsAccess = datum.availability == LiveAvailability.PERMISSION_REQUIRED && !listenerMissing
    val headline =
        when {
            widget == GlanceWidgets.CALENDAR && needsAccess -> context.getString(R.string.calendar_allow)
            widget == GlanceWidgets.MEDIA && needsAccess -> context.getString(R.string.media_allow)
            datum.availability == LiveAvailability.LIVE || datum.availability == LiveAvailability.EMPTY -> datum.detail
            widget == GlanceWidgets.HUB -> context.getString(R.string.hub_open)
            else -> context.getString(R.string.default_app_open)
        }
    GlanceFrameCard(
        title = GlanceWidgets.label(widget),
        headline = headline,
        detail = "",
        accent = accent,
        onClick = {
            when {
                widget == GlanceWidgets.HUB -> {
                    onShowHub()
                }

                widget == GlanceWidgets.CALENDAR && needsAccess -> {
                    context.findActivity()?.requestPermissions(
                        arrayOf(Manifest.permission.READ_CALENDAR),
                        SableStartActivity.LIVE_PERMISSION_REQUEST_CODE,
                    )
                }

                widget == GlanceWidgets.MEDIA && needsAccess -> {
                    onStartIntent(
                        Intent(context, OnboardingActivity::class.java)
                            .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.NOTIFICATIONS.name),
                    )
                }

                widget == GlanceWidgets.CALENDAR -> {
                    openDefault(DefaultApps.CALENDAR)
                }

                widget == GlanceWidgets.MEDIA -> {
                    openDefault(DefaultApps.MUSIC)
                }

                else -> {
                    openDefault(DefaultApps.EMAIL)
                }
            }
        },
        modifier = modifier,
    )
}

internal tailrec fun Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** A Glance widget drawn as an Active Frame: its value large, its name along the bottom. */
@Composable
internal fun GlanceFrameCard(
    title: String,
    headline: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(Bb10Card)
                .border(if (focused) 3.dp else 1.dp, if (focused) Bb10Blue else Bb10Line, shape)
                .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(accent))
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = FRAME_LABEL_HEIGHT),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = Bb10Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Bb10Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = title,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(FRAME_LABEL_HEIGHT)
                    .background(if (focused) Bb10Blue else Color(0xE6000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Bb10OnBlue else Color.White,
            maxLines = 1,
        )
    }
}

/**
 * One app grid page (Ripe 10 / BB10 page model): five columns of labelled,
 * tile-free icons in the user's order (Edit Home reorders them), as many rows
 * as the window fits ([AppGrid.rowsFor]); the icon size follows the cell.
 * Arrows move focus, Enter opens, Shift+Enter opens the Dual copy, Space, Menu
 * or Alt+Enter opens the app's actions, long press on an icon its actions and
 * on an empty place Edit Home; typing searches.
 */
internal fun liveDetail(
    datum: LiveDatum,
    app: AppEntry?,
    noApp: String,
): String =
    when (datum.availability) {
        LiveAvailability.LIVE, LiveAvailability.EMPTY -> datum.detail
        else -> if (app == null) noApp else t("open_2", app.label)
    }

/** Battery level and charging from the sticky battery broadcast (no receiver kept). */
internal fun readBattery(context: Context): Pair<Int?, Boolean> =
    runCatching {
        val intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return@runCatching null to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) level * PERCENT / scale else null
        percent to
            (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }.getOrDefault(null to false)

@Composable
internal fun GlanceCard(
    title: String,
    detail: String,
    accent: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    large: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (large) 72.dp else 56.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.focusable())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (large) 34.dp else 26.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(6.dp))
                    .background(accent),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/*
 * Hub page (left of Active Frames; visual "5. Hub"): the one Sable Hub. Under the "Sable Hub"
 * title the unread bar (after the Ripe launcher's BB7 row: each app with unread notifications as
 * its small icon and count, "+N" for the rest, from Sable Hub's listener), then the All /
 * Messages / Calls / Contacts tabs. Start shows counts and names only (the snapshots carry no
 * message text) and opens Sable Messages, Phone, Contacts or the owning app for the rest.
 */
