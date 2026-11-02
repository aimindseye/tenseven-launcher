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
import java.util.concurrent.Executors

/** Owns Android task I/O and releases all presentation references when the host stops. */
class ActiveFramesHost(context: Context) {
    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val inventory = LauncherAppsRepository(app)
    private val native = RecentTasksRepository(app)
    private val usage = UsageRecentsRepository(app)
    private val store = StartStateRepository(app)
    @Volatile private var entries = emptyList<AppEntry>()
    val state = mutableStateOf(FramesState())
    val coordinator = FramesCoordinator(object : FrameSource {
        override fun read(): FrameInput {
            val caps = PlatformCapabilityProbe.detect(app)
            entries = inventory.loadApps().filter(::available)
            return FrameInput(native.loadFrames(), usage.recentEvents(),
                entries.map { FrameApp(it.component.packageName, it.user.hashCode()) }.toSet(),
                Process.myUserHandle().hashCode(), caps, store.loadFrameSettings())
        }
        override fun loadDismissals() = store.loadFrameDismissals()
        override fun saveDismissals(value: Map<String, Long>) = store.saveFrameDismissals(value)
        override fun resume(item: FrameItem) = item.task?.let { native.resume(it.taskId) } ?: false
        override fun launch(app: FrameApp) {
            entries.firstOrNull { it.component.packageName == app.packageName && it.user.hashCode() == app.userId && available(it) }
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
