package io.github.aimindseye.tenseven.platform

import android.app.AlarmManager
import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The phone's next alarm clock, as the BlackBerry 7 widget strip and the BlackBerry 10 Alarm frame show it. */
object NextAlarm {
    /** "Fri 7:00 AM" for the next alarm, or null when none is set. Needs no permission. */
    fun text(context: Context): String? =
        runCatching {
            val trigger = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: return null
            val locale = Locale.getDefault()
            val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "EEEHm" else "EEEjm")
            SimpleDateFormat(pattern, locale).format(Date(trigger))
        }.getOrNull()
}
