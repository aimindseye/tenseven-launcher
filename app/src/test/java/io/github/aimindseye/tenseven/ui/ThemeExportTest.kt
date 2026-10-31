package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.model.CustomThemes
import io.github.aimindseye.tenseven.model.ThemeParse
import io.github.aimindseye.tenseven.model.ThemePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeExportTest {
    @Test
    fun phoneColoursHasNoFile() {
        assertNull(presetAsCustomTheme(ThemePresets.byId(ThemePresets.PHONE_ID)))
    }

    @Test
    fun everyOtherBuiltInThemeExportsAFileThatImportsBack() {
        ThemePresets.ALL.filter { it.id != ThemePresets.PHONE_ID }.forEach { preset ->
            val theme = presetAsCustomTheme(preset)
            assertNotNull(preset.id, theme)
            val parsed = CustomThemes.parse(CustomThemes.toJson(theme!!))
            assertEquals(preset.id, true, parsed is ThemeParse.Ok)
            assertEquals(preset.id, theme.copy(id = "custom-" + preset.title.lowercase()), (parsed as ThemeParse.Ok).theme)
        }
    }
}
