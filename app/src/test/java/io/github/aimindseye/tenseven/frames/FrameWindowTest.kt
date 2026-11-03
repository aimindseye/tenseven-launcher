package io.github.aimindseye.tenseven.frames

import org.junit.Assert.*
import org.junit.Test

class FrameWindowTest {
    private fun event(index: Int, at: Long = index.toLong()) = FrameOpening(FrameApp("app$index", 0), at)
    @Test fun ninthOpeningEvictsOldestAndOldSnapshotsCannotRecreateIt() {
        val values = (1..9).map { event(it) }
        val window = FrameWindow().record(values, 1)
        assertEquals((9 downTo 2).map { "app$it" }, window.openings().map { it.app.packageName })
        assertEquals(window, window.record(values + event(0, 0), 1))
        val closed = window.close(FrameApp("app9", 0), 10)
        assertEquals(7, closed.openings().size)
        assertFalse(closed.record(values, 1).openings().any { it.app.packageName in setOf("app1", "app9") })
        assertEquals("app9", closed.record(listOf(event(9, 11)), 1).openings().first().app.packageName)
        assertEquals(8, closed.record(listOf(event(10, 12)), 1).openings().size)
    }
    @Test fun reopenedAppMovesToNewestAndProfilesAndLocalAppsRemainDistinct() {
        val window = FrameWindow().record((1..8).map { event(it) }, 1).record(listOf(event(1, 9)), 1).record(listOf(event(9, 10)), 1)
        assertFalse(window.openings().any { it.app.packageName == "app2" })
        assertTrue(window.openings().any { it.app.packageName == "app1" })
        val notes = FrameApp("io.github.aimindseye.tenseven", 0, LocalFrameApps.activities.getValue("local:notes"))
        val calc = notes.copy(activityClass = LocalFrameApps.activities.getValue("local:calculator"))
        val values = listOf(FrameOpening(notes, 2), FrameOpening(calc, 3), FrameOpening(FrameApp("app", 10), 4))
        val saved = FrameWindow().record(values, 1)
        val restored = FrameWindow(FrameDismissals.decode(FrameDismissals.encode(saved.active)), FrameDismissals.decode(FrameDismissals.encode(saved.seen)))
        assertEquals(saved, restored)
        assertEquals(3, restored.openings().size)
        assertEquals("local:notes", LocalFrameApps.key(notes.activityClass))
        assertNull(LocalFrameApps.key("unknown"))
    }
    @Test fun freshSessionIgnoresOldHistoryAndBoundsTombstones() {
        assertTrue(FrameWindow().record((1..8).map { event(it) }, 100).active.isEmpty())
        val window = FrameWindow().record((1..120).map { event(it) }, 1)
        assertEquals(8, window.active.size)
        assertEquals(96, window.seen.size)
        assertEquals(8, FrameSettings(maximum = 12).normalized().maximum)
        assertTrue(FrameWindow(mapOf("invalid" to 1L)).openings().isEmpty())
    }
}
