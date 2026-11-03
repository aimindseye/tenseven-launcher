package io.github.aimindseye.tenseven.platform

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import io.github.aimindseye.tenseven.conversations.*
import io.github.aimindseye.tenseven.extras.notes.core.NoteStore
import io.github.aimindseye.tenseven.extras.notes.core.NoteText
import io.github.aimindseye.tenseven.frames.*
import java.io.File

/** Read-only adapters, refreshed off the UI thread. All third-party content stays in memory. */
class AppFrameSummaryRepository(private val context: Context) {
    private val choices = FrameIntegrationPreferences(context)
    private val documents = OfficeFrameDocuments(context)
    fun snapshot(now: Long): List<AppSummary> {
        val lock = context.getSystemService(KeyguardManager::class.java)
        if (lock.isDeviceLocked || lock.isKeyguardLocked) return emptyList()
        val allowed = ConversationAccess.isGranted(context)
        return AppSummaryPolicy.select(buildList {
            if (allowed) {
                addAll(media(now))
                val mediaPackages = mapNotNull { it.packageName }.toSet()
                addAll(FrameMediaNotifications.snapshot(context).filterNot { it.packageName in mediaPackages })
                if (choices.excerpts) addAll(messages(now))
            }
            if (choices.local) addAll(local(now))
            addAll(documents.summaries(now))
        }, allowed)
    }

    private fun media(now: Long): List<AppSummary> = runCatching {
        val listener = ComponentName(context, ConversationListenerService::class.java)
        context.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener)
            .filter { it.playbackState?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING) }
            .sortedBy { if (it.playbackState?.state == PlaybackState.STATE_PLAYING) 0 else 1 }
            .distinctBy { it.packageName }.mapNotNull { controller ->
                val metadata = controller.metadata
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                if (title.isBlank()) return@mapNotNull null
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                val prefix = if (controller.playbackState?.state == PlaybackState.STATE_PAUSED) "Paused: " else ""
                AppSummary("media:" + controller.packageName, appName(controller.packageName),
                    AppSummaryPolicy.text(prefix + listOf(title, artist).filter(String::isNotBlank).joinToString(" · ")),
                    SummarySource.Media, controller.packageName, now)
            }
    }.getOrDefault(emptyList())

    private fun messages(now: Long): List<AppSummary> {
        ConversationHub.ensureStateLoaded(context)
        val enabled = StartStateRepository(context).loadHubEnabledApps()
        val state = ConversationHub.state()
        return ConversationHub.notifications().filter {
            it.packageName in enabled && !it.isOngoing && !state.isSnoozed(it.conversationId, now) && state.unreadCount(it) > 0
        }.groupBy { it.packageName }.map { (pkg, items) ->
            val latest = items.maxBy { it.postedAt }
            val message = latest.messages.filter { !it.isOutgoing && !it.isHistoric }.maxByOrNull { it.timestamp }
            AppSummary("messages:" + pkg, latest.appName,
                AppSummaryPolicy.text(listOf(latest.conversationName, message?.text.orEmpty()).filter(String::isNotBlank).joinToString(": ")),
                SummarySource.Notification, pkg, latest.postedAt)
        }
    }

    private fun local(now: Long): List<AppSummary> = buildList {
        val notes = runCatching { NoteStore(File(context.filesDir, "notes")).list() }.getOrDefault(emptyList())
        notes.firstOrNull()?.let { note -> add(AppSummary("local:notes", "TenSeven Notes",
            AppSummaryPolicy.text(note.title + " · " + NoteText.preview(note.body)), SummarySource.Local, updatedAt = note.updatedAt)) }
        val tasks = taskRepository(context).list().filterNot { it.completed }
        tasks.sortedWith(compareBy({ it.dueAtEpochMs ?: it.remindAtEpochMs ?: Long.MAX_VALUE }, { -it.updatedAtEpochMs })).firstOrNull()?.let { task ->
            val at = task.dueAtEpochMs ?: task.remindAtEpochMs
            val whenText = at?.let { android.text.format.DateUtils.formatDateTime(context, it,
                android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME) }
            add(AppSummary("local:tasks", "Tasks / Remind", AppSummaryPolicy.text("${tasks.size} open · " + task.title +
                (whenText?.let { " · " + it } ?: "")), SummarySource.Local, updatedAt = now))
        }
        val last = context.getSharedPreferences("calculator_frame", Context.MODE_PRIVATE).getString("last_result", null)
        if (!last.isNullOrBlank()) add(AppSummary("local:calculator", "TenSeven Calculator", AppSummaryPolicy.text(last), SummarySource.Local, updatedAt = now))
    }

    private fun appName(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun open(key: String) {
        if (key.startsWith("office:")) { documents.open(key.removePrefix("office:")); return }
        val activity = when (key) {
            "local:notes" -> io.github.aimindseye.tenseven.extras.notes.MainActivity::class.java
            "local:tasks" -> io.github.aimindseye.tenseven.tasks.TasksActivity::class.java
            "local:calculator" -> io.github.aimindseye.tenseven.extras.calculator.MainActivity::class.java
            else -> null
        }
        if (activity != null) { runCatching { context.startActivity(Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; return }
        val pkg = key.substringAfter(':', "")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
