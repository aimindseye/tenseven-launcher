package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.Intent

/** User-initiated app opening only. Package presence is not signer verification or an install request. */
object CompanionLaunches {
    fun intent(
        context: Context,
        id: String,
    ): Intent? {
        val suffix =
            when (id) {
                "tenseven_phone" -> "phone"
                "tenseven_calendar" -> "calendar"
                else -> id.removePrefix("local:")
            }
        if (suffix !in setOf("phone", "calendar", "tasks", "notes", "calculator", "profiles")) return null
        val root = "io.github.aimindseye.tenseven.$suffix"
        return listOf(root + ".debug", root).firstNotNullOfOrNull {
            context.packageManager.getLaunchIntentForPackage(it)
        }
    }
}
