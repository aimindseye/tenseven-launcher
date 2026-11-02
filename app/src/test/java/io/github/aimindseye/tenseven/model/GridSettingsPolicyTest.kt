package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridSettingsPolicyTest {
    @Test fun bb10NeverShowsBb7OnlyPreviewOrRowCount() {
        assertFalse(GridSettingsPolicy.showBb7OnlyOptions(HomeStyle.Bb10))
        assertTrue(GridSettingsPolicy.showSharedGridOptions(HomeStyle.Bb10))
    }

    @Test fun bb7RetainsItsPreviewAndRowCount() {
        assertTrue(GridSettingsPolicy.showBb7OnlyOptions(HomeStyle.Bb7))
        assertTrue(GridSettingsPolicy.showSharedGridOptions(HomeStyle.Bb7))
    }
}
