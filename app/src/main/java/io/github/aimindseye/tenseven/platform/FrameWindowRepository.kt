package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.core.content.edit
import io.github.aimindseye.tenseven.frames.*

/** New history boundary for this repair; persists through process recreation, resets on reboot or Clear. */
class FrameWindowRepository(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("active_frame_window_v12", Context.MODE_PRIVATE)

    fun since(): Long =
        synchronized(LOCK) {
            val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            if (!prefs.contains("since") || prefs.getInt("boot", -2) != boot) clear()
            prefs.getLong("since", System.currentTimeMillis())
        }

    fun clear() =
        synchronized(LOCK) {
            prefs.edit {
                clear()
                putLong("since", System.currentTimeMillis())
                putInt("boot", Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
            }
            StartStateRepository(context).saveFrameDismissals(emptyMap())
        }

    fun update(events: List<FrameOpening>): List<FrameOpening> =
        synchronized(LOCK) {
            val start = since()
            val next = load().record(events, start)
            save(next)
            next.openings()
        }

    fun record(app: FrameApp) {
        update(listOf(FrameOpening(app, System.currentTimeMillis())))
    }

    fun close(app: FrameApp) =
        synchronized(LOCK) {
            since()
            save(load().close(app, System.currentTimeMillis()))
        }

    private fun load() =
        FrameWindow(FrameDismissals.decode(prefs.getString("active", null)), FrameDismissals.decode(prefs.getString("seen", null)))

    private fun save(window: FrameWindow) {
        prefs.edit {
            putString("active", FrameDismissals.encode(window.active))
            putString("seen", FrameDismissals.encode(window.seen))
        }
    }

    companion object {
        private val LOCK = Any()

        fun local(
            context: Context,
            activity: String,
        ) {
            if (StartStateRepository(context).loadFrameSettings().rememberReturnedApps) {
                FrameWindowRepository(context).record(FrameApp(context.packageName, Process.myUserHandle().hashCode(), activity))
            }
        }
    }
}
