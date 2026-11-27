package io.github.aimindseye.tenseven.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.TablerGlyphPaths

@Composable
private fun rememberReferenceUnread(): Int {
    val capabilities = rememberPlatformCapabilities()
    val revision = rememberHubRevision()
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled =
        remember(revision) {
            io.github.aimindseye.tenseven.platform
                .StartStateRepository(context)
                .loadHubEnabledApps()
        }
    return if (capabilities.hubNotifications == io.github.aimindseye.tenseven.platform.CapabilityStatus.Ready) {
        io.github.aimindseye.tenseven.platform.LockLayerState.counts
            .filterKeys { it in enabled }
            .values
            .sum()
    } else {
        0
    }
}

/** The BBOS 10 home bar: Hub, the page dots and Camera. */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun ReferenceHomeBar(
    slots: List<QuickSlot>,
    pages: HomeBarPages,
    actions: HomeBarActions,
) {
    Bb10BottomBar(slots, pages, actions, rememberReferenceUnread())
}

internal data class HomeBarControlSpec(
    val label: String,
    val glyph: String,
    val selected: Boolean = false,
    val unread: Int = 0,
)

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun HomeBarControl(
    spec: HomeBarControlSpec,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val label = spec.label
    val glyph = spec.glyph
    val isSelected = spec.selected
    val unread = spec.unread
    Column(
        Modifier
            .widthIn(min = 44.dp)
            .heightIn(min = 48.dp)
            .sableFocusRing()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = if (unread > 0) "$label, $unread unread" else label
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.heightIn(min = 43.dp).widthIn(min = 44.dp), contentAlignment = Alignment.Center) {
            if (glyph == "circle") {
                // Read theme colors while still inside the composable scope, not Canvas's DrawScope.
                val dotColor = if (isSelected) Bb10Text else Bb10Muted
                androidx.compose.foundation.Canvas(Modifier.size(6.dp)) {
                    drawCircle(dotColor)
                }
            } else {
                LineIcon(homeControlPaths(glyph), Modifier.size(22.dp), Bb10Text)
            }
            if (unread > 0) {
                Text(
                    if (unread > MAX_BAR_COUNT) "99+" else unread.toString(),
                    Modifier.align(Alignment.TopEnd),
                    color = Bb10Blue,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        // One consistent BB10 selection underline across BB10 Home, Active Frames, All Apps and BB7.
        Box(Modifier.width(19.dp).height(2.dp).background(if (isSelected) Bb10Blue else Color.Transparent))
    }
}

internal data class HomeBarPages(
    val current: Int,
    val count: Int,
    val lead: Int,
)

internal class HomeBarActions(
    val showPage: (Int) -> Unit,
    val openSlot: (QuickSlot) -> Unit,
    val peek: (() -> Unit)? = null,
)

private const val MAX_BAR_COUNT = 99

private fun homeControlPaths(glyph: String): List<String> =
    when (glyph) {
        "menu-2" -> listOf("M4 6h16", "M4 12h16", "M4 18h16")
        "home" -> listOf("M3 11l9 -8 9 8", "M5 10v10h14v-10", "M9 20v-7h6v7")
        else -> TablerGlyphPaths.BY_NAME[glyph] ?: LineIconPaths.SEARCH
    }

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubBarTarget(pages: HomeBarPages, unread: Int, actions: HomeBarActions, star: Boolean = false) {
    Box(
        Modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val peek = actions.peek
            val space =
                native.keyCode == KeyEvent.KEYCODE_SPACE &&
                    native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) == 0
            if (space && peek != null) {
                if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) peek()
            }
            space && peek != null
        },
    ) {
        if (star) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .sableFocusRing()
                    .combinedClickable(onClick = { actions.showPage(StartPages.HUB + pages.lead) })
                    .semantics { contentDescription = if (unread > 0) "Hub, $unread unread" else "Hub" },
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(homeControlPaths("mail"), Modifier.size(26.dp), Bb10Text)
                if (unread > 0) BbUnreadStar(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 14.dp))
            }
        } else {
            HomeBarControl(HomeBarControlSpec("Hub", "mail", pages.current == StartPages.HUB + pages.lead, unread)) {
                actions.showPage(StartPages.HUB + pages.lead)
            }
        }
    }
}

/**
 * The BBOS 10 bottom bar from the Sable home UI proposal: Hub with a blue star when something is
 * unread, the page dots in the middle (the current page a blue pill) and Camera. Nothing else.
 */
@Suppress("FunctionNaming")
@Composable
private fun Bb10BottomBar(
    slots: List<QuickSlot>,
    pages: HomeBarPages,
    actions: HomeBarActions,
    unread: Int,
) {
    Row(
        Modifier
            .testTag("home-reference-bar")
            .fillMaxWidth()
            .height(48.dp)
            .background(Bb10Background)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HubBarTarget(pages, unread, actions, star = true)
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 0 until pages.count) {
                val here = index == pages.current
                Box(
                    Modifier
                        .size(width = 28.dp, height = 48.dp)
                        .combinedClickable(onClick = { actions.showPage(index) })
                        .semantics {
                            selected = here
                            contentDescription = pageDescription(index - pages.lead, "Widgets")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = if (here) 16.dp else 6.dp, height = 6.dp)
                            .background(
                                if (here) Bb10Blue else Bb10Muted,
                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(width = 64.dp, height = 48.dp)
                .sableFocusRing()
                .combinedClickable(onClick = {
                    val camera = slots.firstOrNull { it.token == QuickBar.CAMERA }
                    actions.openSlot(camera ?: QuickSlot(QuickBar.CAMERA, "Camera", null))
                })
                .semantics { contentDescription = "Camera" },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(homeControlPaths("camera"), Modifier.size(26.dp), Bb10Text)
        }
    }
}

/** The blue star BlackBerry 10 puts on an icon with something new. */
@Suppress("FunctionNaming")
@Composable
internal fun BbUnreadStar(modifier: Modifier = Modifier) {
    val blue = Bb10Blue
    androidx.compose.foundation.Canvas(modifier.size(13.dp)) {
        val outer = size.minDimension / 2f
        val star = androidx.compose.ui.graphics.Path()
        for (i in 0 until 16) {
            val r = if (i % 2 == 0) outer else outer * 0.6f
            val a = Math.PI * i / 8.0 - Math.PI / 2.0
            val x = center.x + (r * Math.cos(a)).toFloat()
            val y = center.y + (r * Math.sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(star, blue)
        drawPath(star, androidx.compose.ui.graphics.Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
    }
}
