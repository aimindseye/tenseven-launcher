package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.live.*
import org.junit.Assert.*
import org.junit.Test

class AppSummaryTest {
    private fun summary(
        source: SummarySource,
        key: String = "one",
    ) = AppSummary(key, "Title", "Text", source, "app", 10)

    @Test fun permissionsAndOptOutGateEveryExternalSourceButNotLocalData() {
        for (source in SummarySource.entries) {
            val value = summary(source)
            assertTrue(AppSummaryPolicy.allowed(value, true))
            assertEquals(source in setOf(SummarySource.Local), AppSummaryPolicy.allowed(value, false))
            val datum = LiveDatum("", "", LiveAvailability.EMPTY)
            val snapshot = LiveSurfaceSnapshot("Day", "Time", datum, datum, datum, datum, datum, datum, 0, appSummaries = listOf(value))
            val item = FrameItem(FrameApp("app", 0))
            assertEquals(value, FrameLiveSummary.summary(FramesState(notificationsAllowed = true), item, snapshot, 0))
            assertEquals("Text", FrameLiveSummary.detail(FramesState(notificationsAllowed = true), item, snapshot, 0))
            assertNull(FrameLiveSummary.summary(FramesState(notificationsAllowed = true), FrameItem(FrameApp("app", 10)), snapshot, 0))
            assertNull(FrameLiveSummary.summary(FramesState(settings = FrameSettings(liveSummaries = false)), item, snapshot, 0))
            assertNull(FrameLiveSummary.summary(FramesState(), FrameItem(FrameApp("different", 0)), snapshot, 0))
        }
        assertEquals("hello world", AppSummaryPolicy.text(" hello\n\tworld "))
        assertEquals(240, AppSummaryPolicy.text("a".repeat(300)).length)
        val values =
            listOf(
                summary(SummarySource.Local),
                summary(SummarySource.Local),
                summary(SummarySource.Media, "two"),
                summary(SummarySource.Local, "blank").copy(detail = " "),
            )
        assertEquals(listOf("one"), AppSummaryPolicy.select(values, false).map { it.key })
        assertEquals(
            24,
            AppSummaryPolicy
                .select((1..30).map { summary(SummarySource.Local, "$it").copy(updatedAt = it.toLong()) }, true)
                .size,
        )
    }
}
