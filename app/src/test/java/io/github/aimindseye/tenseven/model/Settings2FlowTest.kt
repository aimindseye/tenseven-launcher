package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Settings2FlowTest {
    @Test fun fivePagesWithoutPermissionsOrFrames() {
        assertEquals(listOf("appearance_home", "hub", "lock", "apps", "review"), Settings2Flow.ordered)
    }

    @Test fun footerCanTraverseEachPage() {
        assertEquals("appearance_home", Settings2Flow.next("root"))
        assertEquals("hub", Settings2Flow.next("appearance_home"))
        assertEquals("lock", Settings2Flow.next("hub"))
        assertEquals("apps", Settings2Flow.next("lock"))
        assertEquals("review", Settings2Flow.next("apps"))
        assertNull(Settings2Flow.next("review"))
        assertEquals("root", Settings2Flow.previous("appearance_home"))
        assertEquals(1, Settings2Flow.position("appearance_home"))
        assertNull(Settings2Flow.previous("root"))
    }

    @Test fun themesReturnToAppearanceHome() {
        assertEquals("appearance_home", Settings2Flow.previous("themes"))
        assertEquals("appearance_home", Settings2Flow.next("themes"))
    }
}
