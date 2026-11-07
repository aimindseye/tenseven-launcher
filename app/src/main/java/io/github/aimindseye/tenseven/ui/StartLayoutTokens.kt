package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.model.HubBar

internal const val MAX_SESSION_RECENTS = 8
internal const val KEYCODE_DEL = 67
internal const val HUB_PACKAGE = "org.sableos.hub"
internal const val MESSAGES_PACKAGE = HubBar.MESSAGES_PACKAGE
internal const val ACTION_NOTIFICATION_HISTORY = "android.settings.NOTIFICATION_HISTORY"
internal const val HUB_BAR_ICON_DP = 20
internal val HUB_BAR_ITEM_HEIGHT = 32.dp

/** Alt, Sym, Fn, Ctrl and Meta: with these held a letter is not a Contacts-tab jump. */
internal const val HUB_JUMP_BLOCKING_META = 0x02 or 0x04 or 0x08 or 0x1000 or 0x10000
internal const val MAIL_PACKAGE = "org.sableos.mail"
internal const val CALENDAR_PACKAGE = "org.sableos.calendar"
internal const val MEDIA_PACKAGE = "org.sableos.media"
internal const val DIALER_PACKAGE = "com.android.dialer"
internal const val GALLERY_PACKAGE = "com.android.gallery3d"
internal const val HUB_LABEL = "Hub"
internal const val MAIL_LABEL = "Sable Mail"
internal const val MESSAGES_LABEL = "Sable Messages"
internal const val CONTACTS_PACKAGE = "com.android.contacts"
internal const val KEYCODE_DPAD_LEFT = 21
internal const val KEYCODE_DPAD_RIGHT = 22
internal const val PERCENT = 100
internal val EditRowStep = 48.dp

// Matches LauncherAppsRepository.PROFILE_PRIVATE.
internal const val PROFILE_PRIVATE_LABEL = "private"
internal const val LAUNCHER_ICON_CACHE_ENTRIES = 160
internal const val ADAPTIVE_CANVAS_DP = 108f
internal const val ADAPTIVE_SAFE_ZONE_DP = 66f

/** An app's own (non-glyph) icon keeps the margin a glyph has in its box. */
internal const val OWN_ICON_INSET = 0.06f
internal const val FOCUS_SURFACE_ALL_APPS = "all-apps"
internal val PrivacyBadgeWidth = 18.dp

// BB10 strip metrics, designed for the Q25's 720x720 panel at density 280
// (411dp square, about 387dp tall under the status bar). The grid's rows and
// icon size, and the Active Frames card height, come from the window.

/** The built-in widget cards on the Widgets page. */
internal val BUILT_IN_WIDGET_HEIGHT = 110.dp
internal val FRAME_PADDING = 6.dp
internal val FRAME_GAP = 6.dp
internal val FRAME_LABEL_HEIGHT = 28.dp
internal val FRAME_CORNER = 6.dp
internal const val FRAME_STRIP_ICON_DP = 18

/** Two rows of frames always fit under the dials and the weather on a 411dp square. */
internal val FRAME_MIN_HEIGHT = 72.dp
internal const val FRAME_ICON_DP = 40
internal val STATUS_RING_SIZE = 34.dp

/** A grid cell is at least this tall: a 40dp icon, its one-line label and the focus padding. */
internal const val GRID_CELL_MIN_DP = 74
internal val GRID_PADDING = 4.dp
internal val GRID_CELL_GAP = 2.dp
internal val GRID_CELL_INNER_PADDING = 4.dp
internal val GRID_ICON_LABEL_GAP = 3.dp

/** labelMedium's line height. */
internal const val GRID_LABEL_LINE_DP = 16

/** Cell gap, inner padding and icon-to-label gap, top and bottom. */
internal const val GRID_CELL_CHROME_DP = 2 * 2 + 4 * 2 + 3
internal const val INDICATOR_HEIGHT_DP = 16
internal const val INDICATOR_SLOT_DP = 18
internal const val BAR_HEIGHT_DP = 48
internal const val BAR_GLYPH_DP = 26

/** Before the window is measured: the status bar the configuration's height still includes. */
internal const val STATUS_BAR_FALLBACK_DP = 24

/** Page indicator, bar and the grid's own padding: what the grid rows do not get. */
internal const val START_CHROME_DP = BAR_HEIGHT_DP + 2 * 4
internal const val NANOS_PER_MILLI = 1_000_000L

/** Typed `-todo`, `/note`, `+event` and web addresses become rows that hand the text to the app that handles it. */
internal const val ASSIGN_ROWS = 6

/** How far a swipe down on the BlackBerry 10 home must travel to open Command. */
internal const val HOME_SWIPE_DP = 120

/** How solid the BlackBerry 10 home is over the wallpaper: enough dim to keep text readable. */
internal const val HOME_BACKDROP_ALPHA = 0.6f
