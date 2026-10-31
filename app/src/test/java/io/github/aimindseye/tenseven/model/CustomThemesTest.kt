package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemesTest {
    private val full =
        """
        { "format": "tenseven-theme", "version": 1, "name": "Ocean", "mode": "dark",
          "accent": "#00A8DF", "onAccent": "#FFFFFF",
          "palette": { "background": "#001018", "text": "#FFFFFF", "muted": "#9EB4C0" },
          "wallpaper": { "top": "#1F6FEB", "bottom": "#9CD3FF", "glow": "#FFF4C2", "ridge": "#0B3C8C", "seed": 11 } }
        """.trimIndent()

    private fun ok(text: String) = (CustomThemes.parse(text) as ThemeParse.Ok).theme

    private fun reason(text: String) = (CustomThemes.parse(text) as ThemeParse.Refused).reason

    @Test
    fun aFullFileIsRead() {
        val theme = ok(full)
        assertEquals("Ocean", theme.name)
        assertEquals("custom-ocean", theme.id)
        assertEquals("dark", theme.mode)
        assertEquals(0xFF00A8DF.toInt(), theme.accent)
        assertEquals(0xFF001018.toInt(), theme.palette.background)
        assertNull(theme.palette.card)
        assertEquals(11, theme.wallpaper?.seed)
    }

    @Test
    fun onlyTheRequiredPartsAreNeeded() {
        val theme = ok("""{"format":"tenseven-theme","version":1,"name":"Plain","mode":"light","accent":"#1F6FEB"}""")
        assertEquals(0xFFFFFFFF.toInt(), theme.onAccent)
        assertNull(theme.wallpaper)
    }

    @Test
    fun exportThenImportGivesTheSameTheme() {
        val theme = ok(full)
        assertEquals(theme, ok(CustomThemes.toJson(theme)))
    }

    @Test
    fun storedThemesKeepTheirIds() {
        val a = ok(full)
        val b = ok(full.replace("Ocean", "Reef")).copy(id = "custom-reef")
        assertEquals(listOf(a, b), CustomThemes.parseStored(CustomThemes.formatStored(listOf(a, b))))
        assertTrue(CustomThemes.parseStored(null).isEmpty())
        assertTrue(CustomThemes.parseStored("not json").isEmpty())
    }

    @Test
    fun aTakenNameGetsANewId() {
        val parsed = CustomThemes.parse(full, takenIds = setOf("custom-ocean", "custom-ocean-2")) as ThemeParse.Ok
        assertEquals("custom-ocean-3", parsed.theme.id)
    }

    @Test
    fun notThemeJsonIsRefused() {
        assertTrue(reason("hello").contains("not valid"))
        assertTrue(reason("""{"format":"other","version":1}""").contains("not a Tenseven theme"))
        assertTrue(reason(full.replace("\"version\": 1", "\"version\": 2")).contains("newer"))
        assertTrue(reason(full + " trailing").contains("not valid"))
    }

    @Test
    fun badValuesAreRefusedWithAReason() {
        assertTrue(reason(full.replace("\"Ocean\"", "\"\"")).contains("name"))
        assertTrue(reason(full.replace("\"dark\"", "\"blue\"")).contains("mode"))
        assertTrue(reason(full.replace("#00A8DF", "00A8DF")).contains("accent"))
        assertTrue(reason(full.replace("#001018", "#00101")).contains("palette"))
        assertTrue(reason(full.replace("\"seed\": 11", "\"seed\": 100000")).contains("wallpaper"))
    }

    @Test
    fun unreadableColoursAreRefused() {
        // Dark grey text on the dark background.
        assertTrue(reason(full.replace("\"text\": \"#FFFFFF\"", "\"text\": \"#202830\"")).contains("text colour"))
        // Accent the same as the default black background.
        assertTrue(reason(full.replace("#00A8DF", "#050505").replace("\"background\": \"#001018\", ", "")).contains("accent"))
        // White label on a pale accent.
        assertTrue(reason(full.replace("#00A8DF", "#CFEFFF")).contains("onAccent"))
    }

    @Test
    fun aFileOverTheSizeLimitIsRefused() {
        assertTrue(reason(full + " ".repeat(CustomThemes.MAX_BYTES)).contains("larger"))
    }

    @Test
    fun contrastMatchesTheKnownExtremes() {
        assertEquals(21.0, CustomThemes.contrast(0xFFFFFF, 0x000000), 0.01)
        assertEquals(1.0, CustomThemes.contrast(0x123456, 0x123456), 0.001)
        assertNotNull(CustomThemes.color("#0a1B2c"))
        assertNull(CustomThemes.color("#0a1B2"))
        assertNull(CustomThemes.color("#0a1B2g"))
    }

    @Test
    fun deepNestingDoesNotCrash() {
        assertTrue(reason("[".repeat(500)).contains("not valid"))
    }
}
