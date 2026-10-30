package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** BB7 home style: the saved choice, the tray grid and its panels. */
class Bb7HomeTest {
    @Test
    fun homeStyleRoundTripsAndDefaultsToBb10() {
        HomeStyle.entries.forEach { assertEquals(it, HomeStyle.fromStableValue(it.stableValue)) }
        assertEquals(HomeStyle.Bb10, HomeStyle.fromStableValue(null))
        assertEquals(HomeStyle.Bb10, HomeStyle.fromStableValue("bb5"))
    }

    @Test
    fun trayHasSixColumnsOnTheQ25AndStaysInRange() {
        assertEquals(6, Bb7Home.columns(411))
        assertEquals(Bb7Home.MIN_COLUMNS, Bb7Home.columns(200))
        assertEquals(Bb7Home.MAX_COLUMNS, Bb7Home.columns(1200))
    }

    @Test
    fun panelsWrapBothWays() {
        assertEquals(Bb7Panel.All, Bb7Home.step(Bb7Panel.Favorites, 1))
        assertEquals(Bb7Panel.Downloads, Bb7Home.step(Bb7Panel.Favorites, -1))
        assertEquals(Bb7Panel.Favorites, Bb7Home.step(Bb7Panel.Downloads, 1))
    }

    @Test
    fun selectionMovesByCellAndRowWithinTheItems() {
        assertEquals(1, Bb7Home.move(0, 10, 6, 1, 0))
        assertEquals(7, Bb7Home.move(1, 10, 6, 0, 1))
        assertEquals(9, Bb7Home.move(5, 10, 6, 0, 1))
        assertEquals(0, Bb7Home.move(3, 10, 6, 0, -1))
        assertEquals(0, Bb7Home.move(4, 0, 6, 1, 0))
    }

    @Test
    fun aSwipeRightOpensHubAndLeftTheAppsLikeTheBb10Home() {
        assertEquals(Bb7Swipe.Hub, Bb7Home.swipe(200f, 150f))
        assertEquals(Bb7Swipe.Apps, Bb7Home.swipe(-200f, 150f))
        assertEquals(null, Bb7Home.swipe(100f, 150f))
        assertEquals(null, Bb7Home.swipe(-149f, 150f))
    }

    @Test
    fun draggingTheTrayShowsMoreOrFewerRowsAndPastTheLastOpensIt() {
        assertEquals(2, Bb7Home.resizeRows(1, -70f, 64f))
        assertEquals(1, Bb7Home.resizeRows(2, 70f, 64f))
        assertEquals(1, Bb7Home.resizeRows(1, 300f, 64f))
        assertEquals(Bb7Home.MAX_HOME_ROWS + 1, Bb7Home.resizeRows(3, -500f, 64f))
        assertEquals(2, Bb7Home.resizeRows(2, -30f, 64f))
    }

    @Test
    fun addToFavoritesAppendsAndRemoveTakesItOff() {
        assertEquals(listOf("a", "b", "c"), Bb7Home.toggleFavorite(listOf("a", "b"), "c"))
        assertEquals(listOf("a"), Bb7Home.toggleFavorite(listOf("a", "b"), "b"))
    }

    @Test
    fun aHubVisitEndsBackOnTheBb7HomeOnlyAfterLeavingTheBb10HomePage() {
        val start = Bb7Detour(returnOnHome = true)
        // The BlackBerry 10 screen first composes on its home page, before the Hub request moves it.
        assertEquals(false, start.endsAt(false))
        val onHub = start.onCanNavigateBack(true)
        assertEquals(false, onHub.endsAt(true))
        // Swiping or Back from Hub to the BlackBerry 10 home page returns to the BlackBerry 7 home.
        assertEquals(true, onHub.onCanNavigateBack(false).endsAt(false))
    }

    @Test
    fun aRecentsVisitStaysOnActiveFrames() {
        val recents = Bb7Detour(returnOnHome = false).onCanNavigateBack(true).onCanNavigateBack(false)
        assertEquals(false, recents.endsAt(false))
    }

    @Test
    fun panelsFollowTheUsersChoiceAndKeepTheirOrder() {
        assertEquals(Bb7Panels.DEFAULT, Bb7Panels.normalize(null))
        assertEquals(Bb7Panels.DEFAULT, Bb7Panels.normalize(emptyList()))
        assertEquals(listOf(Bb7Panel.All, Bb7Panel.Frequent), Bb7Panels.normalize(listOf("Frequent", "bogus", "All")))
        val withFrequent = Bb7Panels.toggle(Bb7Panels.DEFAULT, Bb7Panel.Frequent)
        assertEquals(listOf(Bb7Panel.Favorites, Bb7Panel.All, Bb7Panel.Downloads, Bb7Panel.Frequent), withFrequent)
        assertEquals(Bb7Panels.DEFAULT, Bb7Panels.toggle(withFrequent, Bb7Panel.Frequent))
        // The last panel cannot be hidden.
        assertEquals(listOf(Bb7Panel.All), Bb7Panels.toggle(listOf(Bb7Panel.All), Bb7Panel.All))
        assertEquals(withFrequent, Bb7Panels.decode(Bb7Panels.encode(withFrequent)))
    }

    @Test
    fun steppingSkipsHiddenPanels() {
        val panels = listOf(Bb7Panel.Favorites, Bb7Panel.Frequent)
        assertEquals(Bb7Panel.Frequent, Bb7Home.step(Bb7Panel.Favorites, 1, panels))
        assertEquals(Bb7Panel.Favorites, Bb7Home.step(Bb7Panel.Frequent, 1, panels))
        // A hidden panel that was showing falls back to the first.
        assertEquals(Bb7Panel.Favorites, Bb7Home.step(Bb7Panel.Downloads, 1, panels))
    }

    @Test
    fun scrimLevelsStayInRangeAndGetDarker() {
        assertEquals(Bb7Scrim.MIN, Bb7Scrim.step(Bb7Scrim.MIN, -1))
        assertEquals(Bb7Scrim.MAX, Bb7Scrim.step(Bb7Scrim.MAX, 1))
        assertEquals(Bb7Scrim.MAX, Bb7Scrim.clamp(99))
        (Bb7Scrim.MIN until Bb7Scrim.MAX).forEach {
            assertEquals(true, Bb7Scrim.trayAlpha(it) < Bb7Scrim.trayAlpha(it + 1))
            assertEquals(true, Bb7Scrim.bannerAlpha(it) < Bb7Scrim.bannerAlpha(it + 1))
        }
    }

    @Test
    fun aDragDownOnBb10HomeOpensCommand() {
        assertEquals(Bb10Swipe.Command, Bb10Gestures.vertical(300f, 120f))
        assertEquals(null, Bb10Gestures.vertical(100f, 120f))
        assertEquals(null, Bb10Gestures.vertical(-300f, 120f))
        assertEquals(null, Bb10Gestures.vertical(300f, 0f))
    }

    @Test
    fun anAddedIconStaysVisible() {
        assertEquals(listOf("a", "b"), Bb7Home.addVisible(listOf("a"), "b", 4))
        assertEquals(listOf("a"), Bb7Home.addVisible(listOf("a"), "a", 4))
        // 2 columns x 3 rows = 6 places: a full home puts the new icon in the last place, not past it.
        val full = listOf("1", "2", "3", "4", "5", "6")
        assertEquals(listOf("1", "2", "3", "4", "5", "n", "6"), Bb7Home.addVisible(full, "n", 2))
    }

    @Test
    fun rowsGrowToFitTheIcons() {
        assertEquals(1, Bb7Home.rowsFor(0, 4))
        assertEquals(1, Bb7Home.rowsFor(4, 4))
        assertEquals(2, Bb7Home.rowsFor(5, 4))
        assertEquals(3, Bb7Home.rowsFor(40, 4))
    }
}
