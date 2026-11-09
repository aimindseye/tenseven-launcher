package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Settings2FlowTest {
    @Test fun sevenPagesInImageOneOrder() {
        assertEquals(listOf("permissions", "appearance_home", "hub", "lock", "frames", "apps", "review"), Settings2Flow.ordered)
    }

    @Test fun footerCanTraverseEachPage() {
        assertEquals("permissions", Settings2Flow.next("root"))
        assertEquals("appearance_home", Settings2Flow.next("permissions"))
        assertEquals("hub", Settings2Flow.next("appearance_home"))
        assertEquals("lock", Settings2Flow.next("hub"))
        assertEquals("frames", Settings2Flow.next("lock"))
        assertEquals("apps", Settings2Flow.next("frames"))
        assertEquals("review", Settings2Flow.next("apps"))
        assertNull(Settings2Flow.next("review"))
        assertEquals("hub", Settings2Flow.previous("lock"))
        assertEquals("root", Settings2Flow.previous("permissions"))
        assertNull(Settings2Flow.previous("root"))
    }

    @Test fun themesReturnToAppearanceHome() {
        assertEquals("appearance_home", Settings2Flow.previous("themes"))
        assertEquals("appearance_home", Settings2Flow.next("themes"))
    }
}
