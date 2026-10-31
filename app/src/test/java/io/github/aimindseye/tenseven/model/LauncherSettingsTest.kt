package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsTest {
    @Test
    fun iconSizeRoundTripsAndFallsBackToMedium() {
        Bb7IconSize.entries.forEach { assertEquals(it, Bb7IconSize.fromStable(it.stable)) }
        assertEquals(Bb7IconSize.Medium, Bb7IconSize.fromStable(99))
        assertEquals(Bb7IconSize.Medium, Bb7IconSize.fromStable(null))
    }

    @Test
    fun biggerIconsFitFewerColumns() {
        val small = Bb7Home.columns(411, Bb7IconSize.Small.cellDp)
        val medium = Bb7Home.columns(411, Bb7IconSize.Medium.cellDp)
        val large = Bb7Home.columns(411, Bb7IconSize.Large.cellDp)
        assertTrue(small >= medium && medium >= large)
        assertEquals(6, medium)
        assertEquals(Bb7Home.columns(411), medium)
    }

    @Test
    fun stripKeepsToggleableWidgetsInFixedOrder() {
        assertEquals(
            listOf(GlanceWidgets.CALENDAR, GlanceWidgets.ALARM, GlanceWidgets.MEDIA),
            SettingsWidgets.strip(listOf(GlanceWidgets.MEDIA, GlanceWidgets.BATTERY, GlanceWidgets.ALARM, GlanceWidgets.CALENDAR)),
        )
        assertEquals(emptyList<String>(), SettingsWidgets.strip(listOf(GlanceWidgets.BATTERY)))
    }

    @Test
    fun alarmIsAGlanceWidgetThatCanBeSwitchedOn() {
        assertTrue(GlanceWidgets.ALARM in GlanceWidgets.ALL)
        assertEquals(listOf("alarm"), GlanceWidgets.toggle(GlanceWidgets.DEFAULT, GlanceWidgets.ALARM))
    }
}
