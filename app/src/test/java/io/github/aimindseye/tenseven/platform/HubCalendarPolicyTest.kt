package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class HubCalendarPolicyTest {
    @Test fun multipleCalendarsAreMergedChronologically() {
        fun event(title: String, time: Long, calendar: String) =
            HubCalendarEvent(title, time, time + 100, calendar, false)
        val rows = listOf(
            event("Team", 300, "Outlook"),
            event("Dentist", 100, "Google"),
            event("Birthday", 200, "Local"),
            event("Meeting", 400, "Work"),
        )
        assertEquals(listOf("Dentist", "Birthday", "Team"),
            HubCalendarPolicy.next(rows).map { it.title })
    }
    @Test fun duplicateEventsAreNotRepeated() {
        val event = HubCalendarEvent("Meeting", 100, 200, "Work", false)
        assertEquals(listOf(event), HubCalendarPolicy.next(listOf(event, event)))
    }
}
