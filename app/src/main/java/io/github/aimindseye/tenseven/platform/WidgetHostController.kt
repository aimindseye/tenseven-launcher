package io.github.aimindseye.tenseven.platform

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Process
import android.os.UserManager
import io.github.aimindseye.tenseven.model.WidgetBinding
import io.github.aimindseye.tenseven.model.WidgetList
import io.github.aimindseye.tenseven.model.WidgetRecovery

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
    private val journal = WidgetHostStore(activity)
    private val users = activity.getSystemService(UserManager::class.java)
    private var listening = false

    /** The widgets on offer: every provider Android lists for this user and profiles. */
    fun providers(): List<AppWidgetProviderInfo> =
        users.userProfiles.filter { users.isUserUnlocked(it) && !users.isQuietModeEnabled(it) }.flatMap { profile ->
            runCatching { manager.getInstalledProvidersForProfile(profile) }.getOrDefault(emptyList())
        }

    fun info(id: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(id) }.getOrNull()?.takeIf {
            users.isUserUnlocked(it.profile) && !users.isQuietModeEnabled(it.profile)
        }

    /** Saved ids that Android still has a provider for; stale ones are released and forgotten. */
    fun load(target: WidgetTarget = WidgetTarget.PAGE): List<Int> {
        val saved = idsOf(target)
        val allocated = runCatching { host.appWidgetIds.toSet() }.getOrNull()
        val known = WidgetRecovery.retained(saved, allocated)
        allocated?.let { ids ->
            val committed = WidgetTarget.entries.flatMap(::idsOf).toSet()
            WidgetRecovery.orphaned(ids, committed, journal.pending()?.id).forEach(::release)
        }
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
        if (listening || PlatformCapabilityProbe.detect(activity).widgetHosting != CapabilityStatus.Ready) return
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
        if (PlatformCapabilityProbe.detect(activity).widgetHosting != CapabilityStatus.Ready) return
        cancelPending()
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return
        val pending =
            WidgetBinding(
                id,
                info.provider.flattenToString(),
                users.getSerialNumberForUser(info.profile),
                target.name,
                WidgetBinding.Stage.Bind,
            )
        if (journal.save(pending)) bind(pending, info) else release(id)
    }

    private fun bind(
        pending: WidgetBinding,
        info: AppWidgetProviderInfo,
    ) {
        val id = pending.id
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
        data: Intent? = null,
    ): Boolean {
        val stage =
            when (requestCode) {
                REQUEST_BIND -> WidgetBinding.Stage.Bind
                REQUEST_CONFIGURE -> WidgetBinding.Stage.Configure
                else -> return false
            }
        journal.pending()?.let { handleResult(it, stage, resultCode, data) }
        return true
    }

    private fun handleResult(
        pending: WidgetBinding,
        stage: WidgetBinding.Stage,
        resultCode: Int,
        data: Intent?,
    ) {
        val returned =
            data
                ?.takeIf { it.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) }
                ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, NO_ID)
        when (pending.result(stage, returned, resultCode == Activity.RESULT_OK)) {
            WidgetBinding.Result.Ignore -> Unit
            WidgetBinding.Result.Cancel -> cancelPending()
            WidgetBinding.Result.Configure -> configureOrFinish()
            WidgetBinding.Result.Commit -> finish()
        }
    }

    fun remove(id: Int) {
        release(id)
        WidgetTarget.entries.filter { id in idsOf(it) }.forEach { save(it, WidgetList.remove(idsOf(it), id)) }
    }

    fun move(
        id: Int,
        delta: Int,
    ) {
        WidgetTarget.entries.filter { id in idsOf(it) }.forEach { save(it, WidgetList.move(idsOf(it), id, delta)) }
    }

    private fun configureOrFinish() {
        val pending = journal.pending() ?: return
        val info = bindingInfo(pending)
        if (info == null) {
            cancelPending()
        } else if (info.configure == null) {
            finish()
        } else {
            if (!journal.save(pending.copy(stage = WidgetBinding.Stage.Configure))) {
                cancelPending()
                return
            }
            val started =
                runCatching {
                    host.startAppWidgetConfigureActivityForResult(activity, pending.id, 0, REQUEST_CONFIGURE, null)
                }
            if (started.isFailure) cancelPending()
        }
    }

    private fun bindingInfo(pending: WidgetBinding): AppWidgetProviderInfo? =
        info(pending.id)?.takeIf {
            it.provider.flattenToString() == pending.provider &&
                users.getSerialNumberForUser(it.profile) == pending.profileSerial
        }

    private fun finish() {
        val pending = journal.pending() ?: return
        val target = WidgetTarget.entries.firstOrNull { it.name == pending.target }
        if (target != null && bindingInfo(pending) != null) {
            save(target, WidgetList.add(idsOf(target), pending.id))
            journal.save(null)
        } else {
            cancelPending()
        }
    }

    private fun cancelPending() {
        journal.pending()?.let { release(it.id) }
        journal.save(null)
    }

    private fun release(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        journal.forget(id)
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
