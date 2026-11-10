package io.github.aimindseye.tenseven.onboarding

import android.content.Context

/**
 * Tracks the first user-initiated Android special-access attempt, not a permission grant.
 * On some Android devices (confirmed on Titan 2), App Info does not expose
 * "Allow restricted settings" until the first request has been blocked.
 *
 * The stored bit simply controls instructional UI. Android remains the sole authority.
 */
object SpecialAccessAttemptStore {
    private const val PREFS = "tenseven_special_access_flow"

    fun attempted(context: Context): Set<SetupStep> =
        listOf(SetupStep.NOTIFICATIONS, SetupStep.USAGE)
            .filter { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(it.name, false) }
            .toSet()

    fun markAttempted(
        context: Context,
        step: SetupStep,
    ) {
        if (!step.isSpecialAccess) return
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(step.name, true)
            .apply()
    }
}
