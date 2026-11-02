package io.github.aimindseye.tenseven.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubContrastTest {
    @Test fun keepsUserForegroundWhenAlreadyReadable() {
        assertEquals(Color.White, HubContrast.readable(Color(0xFF11263B), Color.White))
        assertEquals(Color.Black, HubContrast.readable(Color.White, Color.Black))
    }

    @Test fun repairsDarkTextOnDarkWallpaperMatchedSurface() {
        val background = Color(0xFF152F4D)
        val text = HubContrast.readable(background, Color(0xFF17202B))
        assertTrue(HubContrast.ratio(background, text) >= 4.5f)
    }

    @Test fun repairsLightTextOnLightThemeSurface() {
        val background = Color(0xFFF2EEE9)
        val text = HubContrast.readable(background, Color.White)
        assertTrue(HubContrast.ratio(background, text) >= 4.5f)
    }
}
