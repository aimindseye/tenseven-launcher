package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.design.sableFocusRing

/** Transport icons on a 24-unit grid, the same shapes Sable Media draws, so every player card looks alike. */
internal object SableMediaIcons {
    val Play = filled("M7 5v14l12-7z")
    val Pause = filled("M6 5h4v14H6zM14 5h4v14h-4z")
    val Next = filled("M16 5h2v14h-2zM4 5v14l11-7z")
    val Previous = filled("M6 5h2v14H6zM20 5v14L9 12z")
    val Rewind = filled("M11 5v14L3 12zM21 5v14l-8-7z")
    val Forward = filled("M13 5v14l8-7zM3 5v14l8-7z")
    val Note = stroked("M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0a3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0a3 3 0 0 1 6 0z")

    private fun filled(path: String) = icon(path, stroke = false)

    private fun stroked(path: String) = icon(path, stroke = true)

    private fun icon(
        path: String,
        stroke: Boolean,
    ): ImageVector =
        ImageVector
            .Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = if (stroke) null else SolidColor(Color.Black),
                stroke = if (stroke) SolidColor(Color.Black) else null,
                strokeLineWidth = if (stroke) 2f else 0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
}

/** The name of the app that owns the media session ("Sable Media", "Reader", "Libby"), or empty. */
private fun appLabel(
    context: Context,
    packageName: String,
): String =
    runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault("")

/**
 * The one player card of the Sable launcher, used on the lock screen and on the Home screens: the cover, the title,
 * the artist and the app that is playing, a progress bar with the times when the session reports a length, and
 * Previous, Play or Pause and Next as line-icon buttons (Rewind and Fast forward too for audiobooks). [compact] is
 * the slim strip for the Home screens; tapping its title opens the app that is playing.
 */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun SableMediaCard(
    media: LockMediaNow,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val subtitle =
        if (compact) {
            media.artist.ifBlank { media.album }
        } else {
            listOf(media.artist, media.album).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        }
    val app = if (compact) "" else appLabel(context, media.controller.packageName)
    val coverSize = if (compact) 40.dp else 72.dp
    Column(
        modifier
            .fillMaxWidth()
            .background(Bb10Text.copy(alpha = 0.08f))
            .padding(if (compact) 6.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SableMediaCover(media, coverSize)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .then(
                        if (compact) {
                            Modifier.sableFocusRing().clickable {
                                context.packageManager.getLaunchIntentForPackage(media.controller.packageName)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ?.let { context.startActivity(it) }
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (app.isNotBlank()) {
                    Text(app, color = Bb10Blue, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    media.title,
                    color = Bb10Text,
                    fontSize = if (compact) 14.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = Bb10Text.copy(alpha = 0.7f),
                        fontSize = if (compact) 11.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (compact) {
                MediaControls(media, compact = true, modifier = Modifier)
            }
        }
        if (!compact) {
            if (media.durationMs > 0L) {
                val fraction = (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(3.dp)
                        .background(Bb10Text.copy(alpha = 0.2f)),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Bb10Blue))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(media.positionMs), color = Bb10Text.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(clock(media.durationMs), color = Bb10Text.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
            MediaControls(media, compact = false, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun SableMediaCover(
    media: LockMediaNow,
    size: Dp,
) {
    val art = media.art
    if (art != null) {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = "Cover",
            modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Box(
            Modifier.size(size).background(Bb10Text.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SableMediaIcons.Note, contentDescription = null, tint = Bb10Text.copy(alpha = 0.7f), modifier = Modifier.size(size / 2))
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun MediaControls(
    media: LockMediaNow,
    compact: Boolean,
    modifier: Modifier,
) {
    val controls = media.controller.transportControls
    val side = if (compact) 36.dp else 44.dp
    val main = if (compact) 36.dp else 56.dp
    Row(
        modifier,
        horizontalArrangement = if (compact) Arrangement.End else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaControlButton(SableMediaIcons.Previous, "Previous", side, Bb10Text, null) { controls.skipToPrevious() }
        if (media.canSeekSteps && !compact) {
            MediaControlButton(SableMediaIcons.Rewind, "Rewind", side, Bb10Text, null) { controls.rewind() }
        }
        MediaControlButton(
            icon = if (media.playing) SableMediaIcons.Pause else SableMediaIcons.Play,
            description = if (media.playing) "Pause" else "Play",
            size = main,
            tint = Bb10OnBlue,
            background = Bb10Blue,
        ) {
            if (media.playing) controls.pause() else controls.play()
        }
        if (media.canSeekSteps && !compact) {
            MediaControlButton(SableMediaIcons.Forward, "Fast forward", side, Bb10Text, null) { controls.fastForward() }
        }
        MediaControlButton(SableMediaIcons.Next, "Next", side, Bb10Text, null) { controls.skipToNext() }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun MediaControlButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    tint: Color,
    background: Color?,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .sableFocusRing()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}
