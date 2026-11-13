package io.github.aimindseye.tenseven.features.calendar

import java.time.LocalDate
import java.time.YearMonth

/** Today always means the device-local current date, never the previously selected day. */
internal object D6TodayNavigationPolicy {
    data class Target(
        val day: LocalDate,
        val month: YearMonth,
        val reloadEvents: Boolean,
    )

    fun reset(
        now: LocalDate,
        previouslyDisplayedMonth: YearMonth,
    ): Target =
        Target(
            day = now,
            month = YearMonth.from(now),
            reloadEvents = previouslyDisplayedMonth != YearMonth.from(now),
        )
}
