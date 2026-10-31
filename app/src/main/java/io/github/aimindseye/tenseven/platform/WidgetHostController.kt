package io.github.aimindseye.tenseven.platform

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Process
import io.github.aimindseye.tenseven.model.WidgetList

/**
 * Hosts other apps' Android widgets, on the Widgets page and as tiles on the home screen. One host serves both lists
 * (a second host with the same id would take the updates from the first).
 *
 * Adding one: allocate an id, ask Android to bind it to the chosen provider (the first time it shows its own
 * permission screen), run the widget's setup screen if it has one, then keep the id. Android reports each step
 * through [onActivityResult], which the Home activity forwards. Ids are saved in [StartStateRepository] and are
 * never part of a backup. Every Android call is wrapped: a widget app that misbehaves must not take Home down.
 */
class WidgetHostController(
    private val activity: Activity,
    private val state: StartStateRepository,
    private val onChanged: (WidgetTarget, List<Int>) -> Unit,
) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity.applicationContext, HOST_ID)
    private var pendingId = NO_ID
    private var pendingInfo: AppWidgetProviderInfo? = null
    private var pendingTarget = WidgetTarget.PAGE
    private var listening = false

    /** The widgets on offer: every provider Android lists for this user and profiles. */
    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /** Saved ids that Android still has a provider for; stale ones are released and forgotten. */
    fun load(target: WidgetTarget = WidgetTarget.PAGE): List<Int> {
        val saved = idsOf(target)
        val known = WidgetList.keepKnown(saved) { info(it) != null }
        (saved - known.toSet()).forEach { runCatching { host.deleteAppWidgetId(it) } }
        if (known != saved) store(target, known)
        return known
    }

    private fun idsOf(target: WidgetTarget): List<Int> =
        when (target) {
            WidgetTarget.PAGE -> state.loadWidgetIds()
            WidgetTarget.HOME -> state.loadHomeWidgetIds()
        }

    private fun store(
        target: WidgetTarget,
        ids: List<Int>,
    ) = when (target) {
        WidgetTarget.PAGE -> state.saveWidgetIds(ids)
        WidgetTarget.HOME -> state.saveHomeWidgetIds(ids)
    }

    fun startListening() {
        if (listening) return
        listening = runCatching { host.startListening() }.isSuccess
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    /** The view that draws widget [id], or null when Android no longer knows it. */
    fun createView(id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(activity, id, info) }.getOrNull()
    }

    fun begin(
        info: AppWidgetProviderInfo,
        target: WidgetTarget = WidgetTarget.PAGE,
    ) {
        cancelPending()
        pendingTarget = target
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return
        pendingId = id
        pendingInfo = info
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (bound) {
            configureOrFinish()
        } else {
            val intent =
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(EXTRA_PROVIDER_PROFILE, info.profile ?: Process.myUserHandle())
            if (runCatching { activity.startActivityForResult(intent, REQUEST_BIND) }.isFailure) cancelPending()
        }
    }

    /** Forwarded from the activity; true when the result was one of ours. */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
    ): Boolean {
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == Activity.RESULT_OK) configureOrFinish() else cancelPending()
            REQUEST_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finish() else cancelPending()
            else -> return false
        }
        return true
    }

    fun remove(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        WidgetTarget.entries.filter { id in idsOf(it) }.forEach { save(it, WidgetList.remove(idsOf(it), id)) }
    }

    fun move(
        id: Int,
        delta: Int,
    ) {
        WidgetTarget.entries.filter { id in idsOf(it) }.forEach { save(it, WidgetList.move(idsOf(it), id, delta)) }
    }

    private fun configureOrFinish() {
        val info = pendingInfo
        if (info?.configure == null) {
            finish()
            return
        }
        val started =
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQUEST_CONFIGURE, null) }
        if (started.isFailure) cancelPending()
    }

    private fun finish() {
        val id = pendingId
        pendingId = NO_ID
        pendingInfo = null
        if (id != NO_ID) save(pendingTarget, WidgetList.add(idsOf(pendingTarget), id))
    }

    private fun cancelPending() {
        if (pendingId != NO_ID) runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = NO_ID
        pendingInfo = null
    }

    private fun save(
        target: WidgetTarget,
        ids: List<Int>,
    ) {
        store(target, ids)
        onChanged(target, ids)
    }

    companion object {
        const val REQUEST_BIND = 0x7E01
        const val REQUEST_CONFIGURE = 0x7E02
        private const val HOST_ID = 0x7E57
        private const val NO_ID = -1

        /** AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE; named here because the constant is annotated hidden in some SDKs. */
        private const val EXTRA_PROVIDER_PROFILE = "appWidgetProviderProfile"
    }
}

/** Where a widget is shown: its own page beside Home and the Hub, or as a tile on the home screen. */
enum class WidgetTarget { PAGE, HOME }
