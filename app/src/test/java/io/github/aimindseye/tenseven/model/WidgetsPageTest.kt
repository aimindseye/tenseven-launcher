package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetsPageTest {
    @Test
    fun parsesAndFormats() {
        assertEquals(listOf(3, 1, 2), WidgetList.parse("3, 1,2,x,-4,0,3"))
        assertEquals(emptyList<Int>(), WidgetList.parse(null))
        assertEquals("3,1,2", WidgetList.format(listOf(3, 1, 2)))
    }

    @Test
    fun addsOnceAndRemoves() {
        assertEquals(listOf(1, 2), WidgetList.add(listOf(1), 2))
        assertEquals(listOf(1, 2), WidgetList.add(listOf(1, 2), 2))
        assertEquals(listOf(1), WidgetList.add(listOf(1), 0))
        assertEquals(listOf(1), WidgetList.remove(listOf(1, 2), 2))
    }

    @Test
    fun movesWithinTheListOnly() {
        assertEquals(listOf(2, 1, 3), WidgetList.move(listOf(1, 2, 3), 2, -1))
        assertEquals(listOf(1, 3, 2), WidgetList.move(listOf(1, 2, 3), 2, 1))
        assertEquals(listOf(1, 2, 3), WidgetList.move(listOf(1, 2, 3), 1, -1))
        assertEquals(listOf(1, 2, 3), WidgetList.move(listOf(1, 2, 3), 3, 1))
        assertEquals(listOf(1, 2, 3), WidgetList.move(listOf(1, 2, 3), 9, 1))
    }

    @Test
    fun dropsIdsAndroidForgot() {
        assertEquals(listOf(1, 3), WidgetList.keepKnown(listOf(1, 2, 3)) { it != 2 })
    }

    @Test
    fun sizesAreKeptInRange() {
        assertEquals(WidgetSizing.DEFAULT_DP, WidgetSizing.heightDp(0, 2f))
        assertEquals(WidgetSizing.MIN_DP, WidgetSizing.heightDp(20, 2f))
        assertEquals(WidgetSizing.MAX_DP, WidgetSizing.heightDp(4000, 2f))
        assertEquals(110, WidgetSizing.heightDp(220, 2f))
        assertEquals(1, WidgetSizing.cells(0, 2f))
        assertEquals(2, WidgetSizing.cells(220, 2f))
    }
}
