package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import androidx.compose.runtime.mutableStateOf
import io.github.aimindseye.tenseven.frames.*
import io.github.aimindseye.tenseven.model.AppEntry
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Owns Android task I/O and releases all presentation references when the host stops. */
class ActiveFramesHost(context: Context) {
    private val app = context.applicationContext
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1), ThreadPoolExecutor.DiscardOldestPolicy())
    private val main = Handler(Looper.getMainLooper())
    private val inventory = LauncherAppsRepository(app)
    private val native = RecentTasksRepository(app)
    private val usage = UsageRecentsRepository(app)
    private val store = StartStateRepository(app)
    private val window = FrameWindowRepository(app).also { it.since() }
    @Volatile private var entries = emptyList<AppEntry>()
    val state = mutableStateOf(FramesState())
    val coordinator = FramesCoordinator(object : FrameSource {
        override fun read(): FrameInput {
            val caps = PlatformCapabilityProbe.detect(app)
            entries = inventory.loadApps().filter(::available)
            val settings = store.loadFrameSettings()
            val recents = if (settings.rememberReturnedApps) usage.recentEvents() else emptyList()
            val openings = window.update(recents.map { FrameOpening(FrameApp(it.packageName, Process.myUserHandle().hashCode()), it.timeMs) })
            return FrameInput(native.loadFrames(), recents,
                entries.flatMap { listOf(FrameApp(it.component.packageName, it.user.hashCode()),
                    FrameApp(it.component.packageName, it.user.hashCode(), it.component.className)) }.toSet(),
                Process.myUserHandle().hashCode(), caps, settings, if (settings.rememberReturnedApps) openings else emptyList())
        }
        override fun closed(item: FrameItem) = window.close(item.app)
        override fun loadDismissals() = store.loadFrameDismissals()
        override fun saveDismissals(value: Map<String, Long>) = store.saveFrameDismissals(value)
        override fun resume(item: FrameItem): Boolean {
            if (entries.none { it.component.packageName == item.app.packageName && it.user.hashCode() == item.app.userId && available(it) }) return false
            return item.task?.let { task -> native.loadFrames().any { it == task } && native.resume(task.taskId) } ?: false
        }
        override fun launch(app: FrameApp) {
            entries.firstOrNull { it.component.packageName == app.packageName && it.user.hashCode() == app.userId && (app.activityClass == null || it.component.className == app.activityClass) && available(it) }
                ?.let(inventory::launch)
        }
    }, background = { work -> runCatching { executor.execute(work) } }, foreground = { work -> main.post { work() } },
        now = System::currentTimeMillis, changed = { state.value = it })

    private fun available(entry: AppEntry): Boolean = entry.profileLabel != "private" && runCatching {
        val users = app.getSystemService(UserManager::class.java)
        users.isUserUnlocked(entry.user) && !users.isQuietModeEnabled(entry.user)
    }.getOrDefault(false)

    fun snapshot(item: FrameItem, generation: Long): Bitmap? {
        if (!coordinator.acceptsSnapshot(item, generation)) return null
        val entry = entries.firstOrNull { it.component.packageName == item.app.packageName && it.user.hashCode() == item.app.userId }
        if (entry == null || !available(entry)) return null
        val bitmap = item.task?.let { native.snapshot(it.taskId) }
        if (!coordinator.acceptsSnapshot(item, generation)) { bitmap?.recycle(); return null }
        return bitmap
    }

    fun destroy() { coordinator.destroy(); executor.shutdownNow() }
}
