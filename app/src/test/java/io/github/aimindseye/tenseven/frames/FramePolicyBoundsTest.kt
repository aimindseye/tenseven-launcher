package io.github.aimindseye.tenseven.frames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePolicyBoundsTest {
    @Test fun captureIntervalRetainsInclusiveBoundaryAndPrivacyGuards() {
        assertFalse(CapturePolicy.accept(false, false, 1199L, 200L))
        assertTrue(CapturePolicy.accept(false, false, 1200L, 200L))
        assertTrue(CapturePolicy.accept(false, false, 1201L, 200L))
        assertFalse(CapturePolicy.accept(true, false, 1200L, 200L))
        assertFalse(CapturePolicy.accept(false, true, 1200L, 200L))
        assertFalse(CapturePolicy.accept(false, false, 199L, 200L))
    }

    @Test fun summaryCapPreservesFilteringFirstDuplicateAndNewestOrdering() {
        val values = (1..30).map { AppSummary("$it", "Title", "Text", SummarySource.Local, updatedAt = it.toLong()) }
        val first = values.last().copy(detail = "First duplicate")
        val blank = AppSummary("blank", "Title", " ", SummarySource.Local)
        val selected = AppSummaryPolicy.select(listOf(blank, first) + values.reversed(), true)
        assertEquals(24, selected.size)
        assertEquals((30 downTo 7).map { "$it" }, selected.map { it.key })
        assertEquals("First duplicate", selected.first().detail)
        val external = AppSummary("external", "Title", "Text", SummarySource.Notification)
        assertEquals(selected, AppSummaryPolicy.select(listOf(external, blank, first) + values.reversed(), false))
    }

    @Test fun windowCapsPreserveNewestEntriesAndCloseReopenOrdering() {
        val events = (1..100).map { FrameOpening(FrameApp("app$it", 0), it.toLong()) }
        val window = FrameWindow().record(events.reversed(), 1L)
        assertEquals(8, window.active.size)
        assertEquals(96, window.seen.size)
        assertEquals((100 downTo 93).map { "0:app$it" }, window.active.keys.toList())
        assertEquals((100 downTo 5).map { "0:app$it" }, window.seen.keys.toList())
        val app = FrameApp("app100", 0)
        val closed = window.close(app, 101L)
        assertFalse(closed.active.containsKey(app.key))
        assertEquals(closed, closed.record(listOf(FrameOpening(app, 100L)), 1L))
        val reopened = closed.record(listOf(FrameOpening(app, 102L)), 1L)
        assertEquals(app, reopened.openings().first().app)
        assertEquals(8, reopened.active.size)
        assertEquals(96, reopened.seen.size)
    }
}
