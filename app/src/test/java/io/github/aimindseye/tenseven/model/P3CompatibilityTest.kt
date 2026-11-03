package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P3CompatibilityTest {
    @Test fun widgetResultsRejectStaleIdsAndPhasesButAcceptNullIdFromValidSystemResult() {
        val pending = WidgetBinding(7, "example/Widget", 0, "PAGE", WidgetBinding.Stage.Bind)
        assertEquals(WidgetBinding.Result.Ignore, pending.result(WidgetBinding.Stage.Bind, 8, true))
        assertEquals(WidgetBinding.Result.Ignore, pending.result(WidgetBinding.Stage.Configure, 7, true))
        assertEquals(WidgetBinding.Result.Cancel, pending.result(WidgetBinding.Stage.Bind, 7, false))
        assertEquals(WidgetBinding.Result.Configure, pending.result(WidgetBinding.Stage.Bind, null, true))
        assertEquals(WidgetBinding.Result.Commit, pending.copy(stage = WidgetBinding.Stage.Configure)
            .result(WidgetBinding.Stage.Configure, 7, true))
    }

    @Test fun lockedOrTemporarilyUnavailableProviderNeverDestroysSavedWidgetIds() {
        assertEquals(listOf(2, 3), WidgetRecovery.retained(listOf(2, 3), null))
        assertEquals(listOf(2), WidgetRecovery.retained(listOf(-1, 2, 2, 3), setOf(2)))
        assertEquals(setOf(4), WidgetRecovery.orphaned(setOf(2, 3, 4), setOf(2), 3))
        assertEquals(emptySet<Int>(), WidgetRecovery.orphaned(setOf(2, 3), setOf(2, 3), null))
    }

    @Test fun resizingIsBoundedAndCannotOverflowOrLoseTheRequestedDirection() {
        assertTrue(WidgetSizing.resize(110, 1) > 110)
        assertTrue(WidgetSizing.resize(110, -1) < 110)
        assertEquals(WidgetSizing.MAX_DP, WidgetSizing.resize(110, Int.MAX_VALUE))
        assertEquals(WidgetSizing.MIN_DP, WidgetSizing.resize(110, Int.MIN_VALUE))
    }

    @Test fun windowAndLargeTextMetricsReduceSlotsRatherThanClippingThreeForcedRows() {
        assertEquals(5, HomeViewport.columns(411, GridColumns.AUTO, 1f))
        assertEquals(2, HomeViewport.columns(320, GridColumns.AUTO, 2f))
        assertEquals(1, HomeViewport.rows(90, 74, 2f))
        assertEquals(2, HomeViewport.frameColumns(411, 1f))
        assertEquals(1, HomeViewport.frameColumns(320, 2f))
        assertEquals(1f, HomeViewport.textScale(Float.NaN))
        assertEquals(72, HomeViewport.minimumFrameHeight(1f))
        assertEquals(240, HomeViewport.minimumFrameHeight(2f))
        for (width in listOf(1, 240, 320, 411, 600, 840)) {
            for (scale in listOf(1f, 1.3f, 2f, 3f)) {
                assertTrue(HomeViewport.columns(width, GridColumns.AUTO, scale) > 0)
                assertTrue(HomeViewport.rows(width, 74, scale) in 1..5)
            }
        }
    }

    @Test fun keyboardProfilesAndFallbackChordsDoNotStealImeOrRepeatEvents() {
        assertEquals(LauncherKeys.Profile.Touch, LauncherKeys.profile(false, false))
        assertEquals(LauncherKeys.Profile.MenuKey, LauncherKeys.profile(true, true))
        assertEquals(LauncherKeys.Profile.ModifierMenu, LauncherKeys.profile(true, false))
        for (modifier in listOf(0x02, 0x08)) {
            assertEquals(LauncherKeys.Action.Menu, LauncherKeys.action(LauncherKeys.ENTER, modifier, false))
            assertEquals(LauncherKeys.Action.Command, LauncherKeys.action(LauncherKeys.SPACE, modifier, false))
        }
        assertEquals(LauncherKeys.Action.Menu, LauncherKeys.action(LauncherKeys.MENU, 0, false))
        assertEquals(LauncherKeys.Action.None, LauncherKeys.action(LauncherKeys.MENU, 0, true))
        assertEquals(LauncherKeys.Action.None, LauncherKeys.action(LauncherKeys.ENTER, 0x1002, false))
        assertFalse(LauncherKeys.action(LauncherKeys.SPACE, 0, false) == LauncherKeys.Action.Command)
    }
}
