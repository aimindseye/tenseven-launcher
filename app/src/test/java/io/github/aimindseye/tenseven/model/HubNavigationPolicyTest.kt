package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubNavigationPolicyTest {
    @Test fun selectedSnoozedAndReminderViewsKeepTheirControlsVisibleAfterCollapse() {
        assertTrue(HubNavigationPolicy.showSecondaryControls(false, HubFilter.All, true, false, null))
        assertTrue(HubNavigationPolicy.showSecondaryControls(false, HubFilter.All, false, true, null))
        assertTrue(HubNavigationPolicy.showSecondaryControls(false, HubFilter.All, false, false, "mail"))
        assertTrue(HubNavigationPolicy.showSecondaryControls(false, HubFilter.Calls, false, false, null))
        assertTrue(HubNavigationPolicy.showSecondaryControls(false, HubFilter.Contacts, false, false, null))
    }

    @Test fun returningToHubCanCollapseTheControlsWhileMoreCanStillExpandThem() {
        assertFalse(HubNavigationPolicy.showSecondaryControls(false, HubFilter.All, false, false, null))
        assertTrue(HubNavigationPolicy.showSecondaryControls(true, HubFilter.All, false, false, null))
    }
}
