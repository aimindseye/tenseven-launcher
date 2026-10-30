package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.CalendarContract
import android.provider.MediaStore
import android.text.format.DateUtils
import android.text.format.DateFormat
import io.github.aimindseye.tenseven.model.StartText.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.MailSummary
import io.github.aimindseye.tenseven.conversations.ConversationListenerService
import io.github.aimindseye.tenseven.live.AppUnreadCount
import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveDatum
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import java.util.Date

class LiveSurfaceRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun initialSnapshot(
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LiveSurfaceSnapshot {
        val loading =
            LiveDatum(
                title = t("loading"),
                detail = "loading",
                availability = LiveAvailability.LOADING,
                observedAtEpochMs = nowEpochMs,
            )

        return LiveSurfaceSnapshot(
            dateText =
                DateFormat.getMediumDateFormat(appContext)
                    .format(Date(nowEpochMs)),
            timeText =
                DateFormat.getTimeFormat(appContext)
                    .format(Date(nowEpochMs)),
            weather = loading.copy(title = t("weather")),
            calendar = loading.copy(title = t("calendar")),
            messages = loading.copy(title = t("hub")),
            mail = loading.copy(title = t("mail")),
            music = loading.copy(title = t("media")),
            photos = loading.copy(title = t("photos")),
            observedAtEpochMs = nowEpochMs,
        )
    }

    suspend fun snapshot(
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LiveSurfaceSnapshot =
        withContext(Dispatchers.IO) {
            LiveSurfaceSnapshot(
                dateText =
                    DateFormat.getMediumDateFormat(appContext)
                        .format(Date(nowEpochMs)),
                timeText =
                    DateFormat.getTimeFormat(appContext)
                        .format(Date(nowEpochMs)),
                // Android has no weather source of its own: the tile opens the default weather app.
                weather =
                    LiveDatum(
                        title = t("weather"),
                        detail = "unavailable",
                        availability = LiveAvailability.UNAVAILABLE,
                    ),
                calendar = nextCalendarEvent(nowEpochMs),
                messages =
                    providerSnapshot(
                        uri = MESSAGES_URI,
                        fallbackTitle = t("hub"),
                    ),
                mail = latestMail(nowEpochMs),
                music = nowPlaying(),
                photos =
                    mediaCount(
                        permission =
                            if (Build.VERSION.SDK_INT >= 33) {
                                Manifest.permission.READ_MEDIA_IMAGES
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            },
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        title = t("photos"),
                        noun = "photo",
                    ),
                observedAtEpochMs = nowEpochMs,
                hubApps = hubAppCounts(),
            )
        }

    /** The next calendar event in the coming week, from the phone's calendar (needs the Calendar permission). */
    private fun nextCalendarEvent(nowEpochMs: Long): LiveDatum {
        val title = t("calendar")
        if (appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return LiveDatum(title, t("permission_required"), LiveAvailability.PERMISSION_REQUIRED)
        }
        return try {
            val window = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(window, nowEpochMs)
            ContentUris.appendId(window, nowEpochMs + CALENDAR_WINDOW_MS)
            val event =
                resolver.query(
                    window.build(),
                    arrayOf(
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY,
                    ),
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC",
                )?.use { cursor ->
                    var found: Triple<String, Long, Boolean>? = null
                    while (found == null && cursor.moveToNext()) {
                        if (cursor.getLong(2) > nowEpochMs) {
                            found = Triple(cursor.getString(0).orEmpty().ifBlank { t("calendar") }, cursor.getLong(1), cursor.getInt(3) != 0)
                        }
                    }
                    found
                }
            if (event == null) {
                LiveDatum(title, NO_EVENTS, LiveAvailability.EMPTY, nowEpochMs)
            } else {
                val (name, begin, allDay) = event
                val flags =
                    if (allDay) {
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
                    } else {
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
                    }
                // An all-day event's times are midnight UTC, so format it in UTC to keep its date.
                val whenText =
                    if (allDay) {
                        DateUtils.formatDateRange(appContext, java.util.Formatter(StringBuilder(), java.util.Locale.getDefault()), begin, begin, flags, "UTC").toString()
                    } else {
                        DateUtils.formatDateTime(appContext, begin, flags)
                    }
                LiveDatum(title, "$name · $whenText", LiveAvailability.LIVE, nowEpochMs)
            }
        } catch (_: SecurityException) {
            LiveDatum(title, t("permission_required"), LiveAvailability.PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            LiveDatum(title, t("provider_error"), LiveAvailability.ERROR)
        }
    }

    /**
     * What is playing, from the active media sessions. Android only lists them to an app that holds notification
     * access, which Tenseven's own listener provides once the user turns it on.
     */
    private fun nowPlaying(): LiveDatum {
        val title = t("media")
        return try {
            val manager = appContext.getSystemService(MediaSessionManager::class.java)
            val listener = ComponentName(appContext, ConversationListenerService::class.java)
            val controllers = manager?.getActiveSessions(listener).orEmpty()
            val active =
                controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
            val metadata = active?.metadata
            val track = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            if (active == null || track.isBlank()) {
                LiveDatum(title, NOTHING_PLAYING, LiveAvailability.EMPTY, System.currentTimeMillis())
            } else {
                val line = if (artist.isBlank()) track else "$track · $artist"
                val playing = active.playbackState?.state == PlaybackState.STATE_PLAYING
                LiveDatum(title, if (playing) line else "Paused: $line", LiveAvailability.LIVE, System.currentTimeMillis())
            }
        } catch (_: SecurityException) {
            LiveDatum(title, t("permission_required"), LiveAvailability.PERMISSION_REQUIRED)
        } catch (_: RuntimeException) {
            LiveDatum(title, t("provider_error"), LiveAvailability.ERROR)
        }
    }

    /**
     * Per-app unread counts (package and count only). Sable Hub's when it is there; otherwise Tenseven's own
     * notification listener's, which is empty until the user grants notification access.
     */
    private fun hubAppCounts(): List<AppUnreadCount> {
        ConversationHub.ensureStateLoaded(appContext)
        return providerHubAppCounts().ifEmpty { ConversationHub.unreadCounts(MAX_HUB_APPS) }
    }

    /**
     * The Mail tile: the newest unread email, from the email notifications Tenseven's listener reads (Android has
     * no public mail data API). Without notification access the tile just opens the default email app. The text is
     * hidden while the phone is locked.
     */
    private fun latestMail(nowEpochMs: Long): LiveDatum {
        val title = t("mail")
        if (!ConversationAccess.isGranted(appContext)) return LiveDatum(title, "unavailable", LiveAvailability.UNAVAILABLE)
        ConversationHub.ensureStateLoaded(appContext)
        val state = ConversationHub.state()
        val mail = ConversationHub.conversations().filter { it.isEmail && !state.isSnoozed(it.id, nowEpochMs) }
        val unread = mail.filter { state.unreadCount(it) > 0 }
        val count = unread.sumOf { state.unreadCount(it) }
        val newest = unread.maxByOrNull { it.latestMessage?.timestamp ?: it.latestNotification.postedAt }
        val locked = appContext.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked == true
        return LiveDatum(
            title = title,
            detail = MailSummary.headline(count, newest?.name, newest?.latestMessage?.text, locked),
            availability = if (count > 0) LiveAvailability.LIVE else LiveAvailability.EMPTY,
            observedAtEpochMs = nowEpochMs,
        )
    }

    private fun providerHubAppCounts(): List<AppUnreadCount> =
        runCatching {
            resolver.query(HUB_APPS_URI, arrayOf("package", "count"), null, null, null)?.use { cursor ->
                val pkg = cursor.getColumnIndexOrThrow("package")
                val count = cursor.getColumnIndexOrThrow("count")
                buildList {
                    while (cursor.moveToNext() && size < MAX_HUB_APPS) {
                        val name = cursor.getString(pkg)?.trim().orEmpty()
                        val value = cursor.getInt(count)
                        if (name.isNotEmpty() && value > 0) add(AppUnreadCount(name, value))
                    }
                }
            }
        }.getOrNull().orEmpty()

    private fun providerSnapshot(
        uri: Uri,
        fallbackTitle: String,
    ): LiveDatum =
        runCatching {
            resolver.query(
                uri,
                arrayOf(
                    "title",
                    "detail",
                    "availability",
                    "observed_at",
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val title =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("title"),
                    )
                        ?.trim()
                        .orEmpty()
                        .ifEmpty { fallbackTitle }
                val detail =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("detail"),
                    )
                        ?.trim()
                        .orEmpty()
                        .ifEmpty { "unavailable" }
                val availability =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow("availability"),
                    ).toLiveAvailability()
                val observedAt =
                    cursor.getColumnIndex("observed_at")
                        .takeIf { it >= 0 }
                        ?.let(cursor::getLong)
                        ?: 0L

                LiveDatum(
                    title = title,
                    detail = detail,
                    availability = availability,
                    observedAtEpochMs = observedAt,
                )
            }
        }.getOrNull()
            ?: LiveDatum(
                title = fallbackTitle,
                detail = "unavailable",
                availability = LiveAvailability.UNAVAILABLE,
            )

    private fun String?.toLiveAvailability(): LiveAvailability =
        when (this?.uppercase()) {
            "LIVE", "STALE" -> LiveAvailability.LIVE
            "EMPTY" -> LiveAvailability.EMPTY
            "PERMISSION_REQUIRED" -> LiveAvailability.PERMISSION_REQUIRED
            "LOADING", "FETCHING", "LOCAL" -> LiveAvailability.LOADING
            "ERROR", "FAILED" -> LiveAvailability.ERROR
            else -> LiveAvailability.UNAVAILABLE
        }

    private fun mediaCount(
        permission: String,
        uri: Uri,
        title: String,
        noun: String,
    ): LiveDatum {
        if (
            appContext.checkSelfPermission(permission) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return LiveDatum(
                title = title,
                detail = t("permission_required"),
                availability = LiveAvailability.PERMISSION_REQUIRED,
            )
        }

        return try {
            val count =
                resolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    null,
                    null,
                    null,
                )?.use { cursor -> cursor.count } ?: 0

            LiveDatum(
                title = title,
                detail =
                    if (count == 0) {
                        "no " + noun + "s found"
                    } else {
                        count.toString() +
                            " " +
                            if (count == 1) noun else noun + "s"
                    },
                availability =
                    if (count == 0) {
                        LiveAvailability.EMPTY
                    } else {
                        LiveAvailability.LIVE
                    },
                observedAtEpochMs = System.currentTimeMillis(),
            )
        } catch (_: SecurityException) {
            LiveDatum(
                title = title,
                detail = t("permission_required"),
                availability = LiveAvailability.PERMISSION_REQUIRED,
            )
        } catch (_: RuntimeException) {
            LiveDatum(
                title = title,
                detail = t("provider_error"),
                availability = LiveAvailability.ERROR,
            )
        }
    }

    companion object {
        val WEATHER_URI: Uri =
            Uri.parse("content://org.sableos.weather.snapshot/current")
        val CALENDAR_URI: Uri =
            Uri.parse("content://org.sableos.calendar.snapshot/current")
        val MESSAGES_URI: Uri =
            Uri.parse("content://org.sableos.hub.snapshot/current")

        /** Per-app unread counts; Hub notifies [MESSAGES_URI] when they change. */
        val HUB_APPS_URI: Uri =
            Uri.parse("content://org.sableos.hub.snapshot/apps")
        private const val MAX_HUB_APPS = 32
        private const val CALENDAR_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private const val NO_EVENTS = "no events this week"
        private const val NOTHING_PLAYING = "nothing playing"
        val MAIL_URI: Uri =
            Uri.parse("content://org.sableos.mail.snapshot/current")
        val MEDIA_URI: Uri =
            Uri.parse("content://org.sableos.media.snapshot/current")

        val OBSERVED_URIS: List<Uri> =
            listOf(
                WEATHER_URI,
                CALENDAR_URI,
                MESSAGES_URI,
                MAIL_URI,
                MEDIA_URI,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            )
    }
}
