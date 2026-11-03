package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.live.*
import org.junit.Assert.*
import org.junit.Test

class FrameLiveSummaryTest {
    private val empty = LiveDatum("", "", LiveAvailability.EMPTY)
    private val snapshot = LiveSurfaceSnapshot("Today", "12:00", empty,
        LiveDatum("Calendar", "Meeting at 3", LiveAvailability.LIVE), empty, empty,
        LiveDatum("Media", "Track · Artist", LiveAvailability.LIVE, sourcePackage = "yt.music"), empty, 0,
        calendarPackages = setOf("calendar"))
    private val state = FramesState(calendarAllowed = true, notificationsAllowed = true)
    private fun detail(pkg: String, user: Int = 0, value: FramesState = state, data: LiveSurfaceSnapshot = snapshot) =
        FrameLiveSummary.detail(value, FrameItem(FrameApp(pkg, user)), data, 0)

    @Test fun calendarAndPlayingPackageGetSummariesWithoutWidgetOrSpareSlotRequirements() {
        assertEquals("Meeting at 3", detail("calendar"))
        assertEquals("Track · Artist", detail("yt.music"))
        assertNull(detail("other.music"))
        assertNull(detail("other.calendar"))
        assertNull(detail("yt.music", 10))
        assertNull(detail("calendar", 10))
        assertNull(detail("calendar", value = state.copy(settings = FrameSettings(liveSummaries = false))))
    }
    @Test fun accessLossAndMissingDataNeverReuseOldSummaries() {
        assertNull(detail("calendar", value = state.copy(calendarAllowed = false)))
        assertNull(detail("yt.music", value = state.copy(notificationsAllowed = false)))
        for (availability in LiveAvailability.entries) {
            val value = snapshot.copy(music = snapshot.music.copy(availability = availability))
            if (availability in setOf(LiveAvailability.LIVE, LiveAvailability.EMPTY))
                assertEquals("Track · Artist", detail("yt.music", data = value))
            else assertNull(detail("yt.music", data = value))
        }
        assertNull(detail("yt.music", data = snapshot.copy(music = empty)))
        assertEquals("No events", detail("calendar", data = snapshot.copy(calendar = empty.copy(detail = "No events"))))
    }
    @Test fun captureThumbnailsAreBoundedAndNeverAcceptHomeOrLockedPixels() {
        assertEquals(320 to 640, CapturePolicy.size(1080, 2160))
        assertEquals(640 to 640, CapturePolicy.size(720, 720))
        assertEquals(320 to 240, CapturePolicy.size(320, 240))
        assertEquals(1 to 1, CapturePolicy.size(0, -1))
        assertFalse(CapturePolicy.accept(true, false, 1000, 0))
        assertFalse(CapturePolicy.accept(false, true, 1000, 0))
        assertFalse(CapturePolicy.accept(false, false, 999, 0))
        assertTrue(CapturePolicy.accept(false, false, 1000, 0))
    }
}
