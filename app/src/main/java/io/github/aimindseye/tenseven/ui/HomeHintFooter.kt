package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.live.AppUnreadCount

/** The one-line key hints at the foot of each home, as on the approved Home boards. */
internal object HomeHints {
    const val BB10 = "Just start typing to search · Hub key: Hub · Back: Frames"
    const val BB7_FIRST = "Trackpad: move · Click: open · Menu: full menu"
    const val BB7_SECOND = "Type to search · Hold Menu: switch"
}

@Composable
internal fun HomeHintFooter(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Unread counts for the BBOS 7 status row: Mail, Texts and Missed calls, from the Hub's opted-in apps. */
internal data class HomeStatusCounts(
    val mail: Int,
    val texts: Int,
    val missed: Int,
) {
    companion object {
        fun from(counts: List<AppUnreadCount>): HomeStatusCounts {
            fun sum(vararg needles: String) =
                counts
                    .filter { c -> needles.any { c.packageName.contains(it, ignoreCase = true) } }
                    .sumOf { it.count }
            return HomeStatusCounts(
                mail = sum("mail"),
                texts = sum("messag", "sms", "mms"),
                missed = sum("dialer", "telecom", "incallui", "phone"),
            )
        }
    }
}
