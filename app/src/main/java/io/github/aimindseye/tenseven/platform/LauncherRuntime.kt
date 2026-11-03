package io.github.aimindseye.tenseven.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.runtime.InventoryCoordinator
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal data class LauncherInventory(
    val apps: List<AppEntry>,
    val privateSpaceLocked: Boolean?,
    val workPaused: Boolean?,
)

/** Android registrations and bounded inventory I/O belong to the lifecycle, not the Activity UI. */
internal class LauncherRuntime(
    context: Context,
    private val inventory: LauncherAppsRepository,
    private val onInventory: (LauncherInventory) -> Unit,
    private val onRefresh: () -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )
    private val profileExecutor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(4),
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )
    private val coordinator =
        InventoryCoordinator(
            load = {
                LauncherInventory(
                    inventory.loadApps(),
                    inventory.privateSpace()?.let(inventory::isLocked),
                    inventory.workProfilesPaused(),
                )
            },
            execute = { executor.execute(it) },
            post = { main.post(it) },
            deliver = onInventory,
        )

    @Volatile private var started = false

    @Volatile private var closed = false
    private var callbackRegistered = false
    private var profileRegistered = false
    private val callback =
        object : LauncherApps.Callback() {
            private fun changed(
                packages: List<String>,
                user: UserHandle,
            ) {
                packages.forEach { inventory.invalidatePrivacy(it, user) }
                refresh()
            }

            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) = changed(listOf(packageName), user)

            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) = changed(listOf(packageName), user)

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) = changed(listOf(packageName), user)

            override fun onPackagesAvailable(
                vararg packageNames: String,
                user: UserHandle,
                replacing: Boolean,
            ) = changed(packageNames.toList(), user)

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = changed(packageNames.toList(), user)

            override fun onPackagesSuspended(
                vararg packageNames: String,
                user: UserHandle,
            ) = refresh()

            override fun onPackagesUnsuspended(
                vararg packageNames: String,
                user: UserHandle,
            ) = refresh()
        }
    private val permissionSignal =
        PermissionChangeSignal(app.packageManager) { uid ->
            inventory.invalidatePrivacyForUid(uid)
            refresh()
        }
    private val profileReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                @Suppress("DEPRECATION")
                val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
                inventory.invalidatePrivacyForUser(user)
                if (started && !closed) {
                    coordinator.stop()
                    onInventory(LauncherInventory(emptyList(), null, null))
                    onRefresh()
                    coordinator.start()
                }
            }
        }
    private val liveObserver =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
    private val appObserver =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
    private val conversationsChanged: () -> Unit = { refresh() }

    fun start() {
        if (closed || started) return
        started = true
        callbackRegistered =
            runCatching {
                inventory.registerCallback(callback)
                true
            }.getOrDefault(false)
        permissionSignal.register()
        profileRegistered =
            runCatching {
                val filter = IntentFilter().apply { PROFILE_ACTIONS.forEach(::addAction) }
                app.registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                true
            }.getOrDefault(false)
        LiveSurfaceRepository.OBSERVED_URIS.forEach { uri ->
            runCatching { app.contentResolver.registerContentObserver(uri, true, liveObserver) }
        }
        runCatching { app.contentResolver.registerContentObserver(LauncherAppsRepository.HIDDEN_APPS_URI, false, appObserver) }
        ConversationHub.addChangeListener(conversationsChanged)
        coordinator.start()
        onRefresh()
    }

    fun refresh() {
        main.post {
            if (started && !closed) {
                onRefresh()
                coordinator.refresh()
            }
        }
    }

    fun setPrivateLocked(locked: Boolean) =
        profileAction {
            inventory.privateSpace()?.let { inventory.setLocked(it, locked) }
        }

    fun setWorkPaused(paused: Boolean) = profileAction { inventory.setWorkProfilesPaused(paused) }

    private fun profileAction(action: () -> Unit) {
        if (!started || closed) return
        runCatching {
            profileExecutor.execute {
                if (!closed) {
                    runCatching(action)
                    refresh()
                }
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        coordinator.stop()
        if (callbackRegistered) runCatching { inventory.unregisterCallback(callback) }
        callbackRegistered = false
        if (profileRegistered) runCatching { app.unregisterReceiver(profileReceiver) }
        profileRegistered = false
        permissionSignal.unregister()
        runCatching { app.contentResolver.unregisterContentObserver(liveObserver) }
        runCatching { app.contentResolver.unregisterContentObserver(appObserver) }
        ConversationHub.removeChangeListener(conversationsChanged)
        main.removeCallbacksAndMessages(null)
    }

    fun close() {
        stop()
        closed = true
        coordinator.close()
        executor.shutdownNow()
        profileExecutor.shutdownNow()
    }

    private companion object {
        val PROFILE_ACTIONS =
            listOf(
                Intent.ACTION_MANAGED_PROFILE_ADDED,
                Intent.ACTION_MANAGED_PROFILE_REMOVED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
                Intent.ACTION_PROFILE_ACCESSIBLE,
                Intent.ACTION_PROFILE_INACCESSIBLE,
                Intent.ACTION_PROFILE_AVAILABLE,
                Intent.ACTION_PROFILE_UNAVAILABLE,
                Intent.ACTION_PROFILE_ADDED,
                Intent.ACTION_PROFILE_REMOVED,
            )
    }
}
