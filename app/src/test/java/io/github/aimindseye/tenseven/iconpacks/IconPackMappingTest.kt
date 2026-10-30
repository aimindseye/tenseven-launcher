package io.github.aimindseye.tenseven.iconpacks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackMappingTest {
    @Test
    fun normalizesComponentForms() {
        assertEquals("a.b/a.b.Main", IconPackComponents.normalized("ComponentInfo{a.b/a.b.Main}"))
        assertEquals("a.b/a.b.Main", IconPackComponents.normalized("a.b/.Main"))
        assertEquals("a.b/x.Y", IconPackComponents.normalized(" a.b/x.Y "))
    }

    @Test
    fun rejectsMalformedComponents() {
        assertNull(IconPackComponents.normalized("nopackage"))
        assertNull(IconPackComponents.normalized("/Cls"))
        assertNull(IconPackComponents.normalized("pkg/"))
    }

    @Test
    fun calendarWinsOverStaticForTheSameComponent() {
        val combined =
            IconPackComponents.combine(
                mapOf("a/a.Cal" to IconPackDrawable.Static("cal_static"), "a/a.Other" to IconPackDrawable.Static("o")),
                mapOf("a/a.Cal" to IconPackDrawable.Calendar("cal_")),
            )
        assertEquals(IconPackDrawable.Calendar("cal_"), combined["a/a.Cal"])
        assertEquals(IconPackDrawable.Static("o"), combined["a/a.Other"])
    }

    @Test
    fun componentMatchBeatsPackageFallback() {
        val definition =
            IconPackDefinition(
                mapOf(
                    "p/p.One" to IconPackDrawable.Static("one"),
                    "p/p.Two" to IconPackDrawable.Static("two"),
                ),
            )
        assertEquals(IconPackDrawable.Static("two"), definition.iconForComponent("p/p.Two", "p"))
        assertEquals(IconPackDrawable.Static("one"), definition.iconForComponent("p/p.Missing", "p"))
        assertNull(definition.iconForComponent("q/q.X", "q"))
    }

    @Test
    fun packageFallbackPrefersCalendar() {
        val definition =
            IconPackDefinition(
                mapOf("p/p.A" to IconPackDrawable.Static("a"), "p/p.B" to IconPackDrawable.Calendar("day_")),
            )
        assertEquals(IconPackDrawable.Calendar("day_"), definition.iconsByPackage["p"])
    }

    @Test
    fun calendarDrawableNameFollowsTheDay() {
        val calendar = IconPackDrawable.Calendar("day_")
        assertEquals("day_7", calendar.drawableName(7))
        assertEquals("day_31", calendar.drawableName(99))
        assertEquals("day_1", calendar.drawableName(0))
        assertEquals("x", IconPackDrawable.Static("x").drawableName(12))
        assertTrue(IconPackContract.discoveryActions.contains("com.novalauncher.THEME"))
    }
}
