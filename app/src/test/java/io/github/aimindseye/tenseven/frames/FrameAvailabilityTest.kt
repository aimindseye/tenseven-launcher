package io.github.aimindseye.tenseven.frames

import org.junit.Assert.*
import org.junit.Test

class FrameAvailabilityTest {
    @Test fun basicFramesAreBb10OnlyAndSetupFollowsNotificationGrant() {
        for (bb10 in listOf(false, true)) {
            for (notifications in listOf(false, true)) {
                assertEquals(bb10, FrameAvailability.frames(bb10))
                assertEquals(bb10 && notifications, FrameAvailability.setup(bb10, notifications))
            }
        }
    }
}
