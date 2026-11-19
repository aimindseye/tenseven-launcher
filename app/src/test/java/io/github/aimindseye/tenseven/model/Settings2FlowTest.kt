package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Settings2FlowTest {
    @Test fun sixPagesInImageOneOrder() {
        assertEquals(
            listOf("permissions", "appearance_home", "hub", "lock", "frames", "review"),
            Settings2Flow.ordered,
        )
    }

    @Test fun footerCanTraverseEachPage() {
        assertEquals("permissions", Settings2Flow.next("root"))
        assertEquals("appearance_home", Settings2Flow.next("permissions"))
        assertEquals("hub", Settings2Flow.next("appearance_home"))
        assertEquals("lock", Settings2Flow.next("hub"))
        assertEquals("frames", Settings2Flow.next("lock"))
        assertEquals("review", Settings2Flow.next("frames"))
        assertNull(Settings2Flow.next("review"))
        assertEquals("hub", Settings2Flow.previous("lock"))
        assertEquals("root", Settings2Flow.previous("permissions"))
        assertNull(Settings2Flow.previous("root"))
    }

    @Test fun themesReturnToAppearanceHome() {
        assertEquals("appearance_home", Settings2Flow.previous("themes"))
        assertEquals("appearance_home", Settings2Flow.next("themes"))
    }

    @Test
    fun optionalCompanionsAreOutsideTheNumberedFlow() {
        assertEquals("root", Settings2Flow.previous("companions"))
        assertEquals("root", Settings2Flow.next("companions"))
        assertNull(Settings2Flow.position("companions"))
        assertEquals(6, Settings2Flow.position("review"))
    }

    @Test
    fun oldSavedDestinationsStillResolve() {
        assertEquals("companions", Settings2Flow.destination("apps"))
        assertEquals("appearance_home", Settings2Flow.destination("home"))
        assertEquals("appearance_home", Settings2Flow.destination("appearance_home"))
        assertEquals("root", Settings2Flow.destination("unavailable"))
        assertEquals("root", Settings2Flow.destination(null))
    }
}
