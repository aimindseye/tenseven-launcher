package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BB10 layout: Active Frames, the app grid and Dual apps. */
class Bb10HomeTest {
    private fun frame(
        id: Int,
        pkg: String,
    ) = ActiveFrame(taskId = id, packageName = pkg, className = null, userId = 0, running = true)

    @Test
    fun framesAreTheEightMostRecentTasksWithoutStartOrRecents() {
        val tasks =
            listOf(frame(1, "org.sableos.launcher"), frame(2, "a"), frame(2, "a"), frame(3, "com.android.launcher3")) +
                (4..12).map { frame(it, "app$it") }
        val frames = ActiveFrames.select(tasks, setOf("org.sableos.launcher", "com.android.launcher3"))
        assertEquals(ActiveFrames.MAX, frames.size)
        assertEquals(listOf(2, 4, 5, 6, 7, 8, 9, 10), frames.map { it.taskId })
    }

    @Test
    fun partialNativeTaskListDoesNotSuppressLauncherLaunches() {
        val display =
            ActiveFrames.mergeForDisplay(
                native = listOf(frame(10, "mail")),
                recentPackages = listOf("chat", "mail"),
                personalPackages = setOf("mail", "chat"),
                personalUserId = 0,
            )
        assertEquals(listOf("chat", "mail"), display.frames.map { it.packageName })
        assertEquals(1, display.syntheticTaskIds.size)
        assertTrue(display.frames.first().taskId in display.syntheticTaskIds)
        assertEquals(10, display.frames.last().taskId)
    }

    @Test
    fun fullNativeListStillIncludesMoreRecentlyLaunchedApp() {
        val native = (1..ActiveFrames.MAX).map { frame(it, "native$it") }
        val display =
            ActiveFrames.mergeForDisplay(
                native, listOf("openedFromTenseven"), setOf("openedFromTenseven"), personalUserId = 0,
            )
        assertEquals(ActiveFrames.MAX, display.frames.size)
        assertEquals("openedFromTenseven", display.frames.first().packageName)
        assertEquals("native1", display.frames[1].packageName)
    }

    @Test
    fun matchingRealTaskIsReusedNotMadeSynthetic() {
        val native = listOf(frame(10, "mail"), frame(11, "browser"))
        val display =
            ActiveFrames.mergeForDisplay(
                native, listOf("browser", "browser", "mail"), setOf("browser", "mail"), personalUserId = 0,
            )
        assertEquals(listOf(11, 10), display.frames.map { it.taskId })
        assertTrue(display.syntheticTaskIds.isEmpty())
    }

    @Test
    fun hiddenAndOtherProfileRecentAppsAreNotSynthetic() {
        val native = listOf(frame(10, "workchat").copy(userId = 11))
        val display =
            ActiveFrames.mergeForDisplay(
                native, listOf("privatechat", "workchat", "personal", "hidden", "personal"),
                setOf("workchat", "personal"),
                personalUserId = 0,
                dismissed = setOf("personal"),
            )
        // Work-profile task remains a true task; personal copy is synthesized separately.
        assertEquals(listOf("workchat", "workchat"), display.frames.map { it.packageName })
        assertEquals(setOf(11, 0), display.frames.map { it.userId }.toSet())
        assertEquals(1, display.syntheticTaskIds.size)
    }

    @Test
    fun privateSpaceTasksNeverBecomeFrames() {
        val tasks = listOf(frame(1, "a"), frame(2, "b").copy(userId = 11), frame(3, "c"))
        assertEquals(listOf(1, 3), ActiveFrames.select(tasks, emptySet(), setOf(11)).map { it.taskId })
        assertEquals(listOf(1, 2, 3), ActiveFrames.select(tasks, emptySet()).map { it.taskId })
    }

    @Test
    fun xOrBackspaceCloseAFrame() {
        assertTrue(ActiveFrames.isCloseKey(52, 0))
        assertTrue(ActiveFrames.isCloseKey(67, 0))
        assertTrue(ActiveFrames.isCloseKey(52, 0x1))
        assertFalse(ActiveFrames.isCloseKey(52, 0x2))
        assertFalse(ActiveFrames.isCloseKey(66, 0))
    }

    @Test
    fun focusMovesToTheNextFrameAfterClose() {
        assertEquals(0, ActiveFrames.focusAfterClose(0, 3))
        assertEquals(1, ActiveFrames.focusAfterClose(2, 3))
        assertNull(ActiveFrames.focusAfterClose(0, 1))
    }

    @Test
    fun gridKeepsTheSavedOrderAndAppendsNewApps() {
        val installed = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), AppGrid.resolve(null, listOf("c", "x"), installed))
        assertEquals(listOf("d", "b", "a", "c"), AppGrid.resolve(listOf("d", "gone", "b"), listOf("c"), installed))
    }

    @Test
    fun gridPagesHoldFiveColumns() {
        assertEquals(5, AppGrid.COLUMNS)
        // Q25 at density 280: about 387dp under the status bar, less dots, bar and padding.
        assertEquals(4, AppGrid.rowsFor(387 - 72, 74))
        assertEquals(AppGrid.MIN_ROWS, AppGrid.rowsFor(100, 74))
        assertEquals(AppGrid.MAX_ROWS, AppGrid.rowsFor(2000, 74))
        assertEquals(20, AppGrid.perPage(4))
        assertEquals(1, AppGrid.pageCount(0, 20))
        assertEquals(2, AppGrid.pageCount(21, 20))
        assertEquals(listOf(20, 21), AppGrid.page((0..21).toList(), 1, 20))
    }

    @Test
    fun gridIconsFollowTheCell() {
        // 411dp less 8dp padding over five columns, four rows of ~79dp, a 31dp label block.
        assertEquals(44, AppGrid.iconSizeFor(80, 78, 31))
        assertEquals(40, AppGrid.iconSizeFor(80, 71, 31))
        assertEquals(AppGrid.MIN_ICON_DP, AppGrid.iconSizeFor(40, 40, 31))
        assertEquals(AppGrid.MAX_ICON_DP, AppGrid.iconSizeFor(200, 200, 31))
    }

    @Test
    fun gridLabelsDropTheSablePrefixUnlessTheNameIsTaken() {
        assertEquals("Calculator", AppGrid.gridLabel("Sable Calculator"))
        assertEquals("2048", AppGrid.gridLabel(" Sable 2048 "))
        assertEquals("Files", AppGrid.gridLabel("Files"))
        assertEquals("Sable", AppGrid.gridLabel("Sable"))
        assertEquals("Sablefish", AppGrid.gridLabel("Sablefish"))
        assertEquals("Sable Camera", AppGrid.gridLabel("Sable Camera", setOf("Sable Camera", "camera")))
        assertEquals("Hub", AppGrid.gridLabel("Sable Hub", setOf("Sable Hub", "Camera")))
    }

    @Test
    fun gridMovesShiftLikeADraggedIcon() {
        val keys = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(listOf("b", "a", "c", "d", "e", "f"), AppGrid.move(keys, 0, 1))
        assertEquals(listOf("b", "c", "d", "e", "f", "a"), AppGrid.move(keys, 0, AppGrid.COLUMNS))
        assertEquals(listOf("f", "a", "b", "c", "d", "e"), AppGrid.move(keys, 5, -10))
        assertEquals(keys, AppGrid.move(keys, 0, -1))
        assertEquals(-5, AppGrid.arrowDelta(19))
        assertEquals(1, AppGrid.arrowDelta(22))
        assertNull(AppGrid.arrowDelta(66))
    }

    @Test
    fun shiftEnterFindsTheDualCopy() {
        data class App(
            val component: String,
            val profile: String?,
        )
        val chat = App("chat/.Main", null)
        val chatDual = App("chat/.Main", AppProfiles.DUAL)
        val work = App("chat/.Main", AppProfiles.WORK)
        val apps = listOf(chat, work, chatDual, App("mail/.Main", null))
        assertEquals(chatDual, AppProfiles.secondInstance(chat, apps, { it.component }, { it.profile }))
        assertEquals(chat, AppProfiles.secondInstance(chatDual, apps, { it.component }, { it.profile }))
        assertNull(AppProfiles.secondInstance(work, apps, { it.component }, { it.profile }))
        assertNull(AppProfiles.secondInstance(apps[3], apps, { it.component }, { it.profile }))
        assertEquals("Dual", AppProfiles.badge(AppProfiles.DUAL))
        assertEquals("Work", AppProfiles.badge(AppProfiles.WORK))
    }

    @Test
    fun appGridColumnsFollowTheSettingAndTheScreenWidth() {
        // Auto keeps the five columns on the Q25's 411 dp and uses fewer, larger cells on a narrow screen.
        assertEquals(5, AppGrid.columnsFor(GridColumns.AUTO, 403))
        assertEquals(4, AppGrid.columnsFor(GridColumns.AUTO, 300))
        assertEquals(3, AppGrid.columnsFor(GridColumns.AUTO, 200))
        // A chosen number is kept while the cells stay wide enough.
        assertEquals(3, AppGrid.columnsFor(3, 403))
        assertEquals(6, AppGrid.columnsFor(6, 403))
        assertEquals(2, AppGrid.columnsFor(6, 100))
        assertEquals(15, AppGrid.perPage(3, 5))
        assertEquals(12, AppGrid.perPage(4, 3))
    }

    @Test
    fun appGridIconSizeScalesWithTheChosenSize() {
        assertEquals(1f, AppGrid.iconScale(Bb7IconSize.Medium), 0f)
        assertEquals(44, AppGrid.iconSizeFor(80, 78, 31))
        assertEquals(44, AppGrid.iconSizeFor(80, 78, 31, AppGrid.iconScale(Bb7IconSize.Medium)))
        assertEquals(35, AppGrid.iconSizeFor(80, 78, 31, AppGrid.iconScale(Bb7IconSize.Small)))
        assertEquals(47, AppGrid.iconSizeFor(80, 78, 31, AppGrid.iconScale(Bb7IconSize.Large)))
        // Never smaller than the minimum, and never taller than the cell leaves room for.
        assertEquals(AppGrid.MIN_ICON_DP, AppGrid.iconSizeFor(20, 78, 31, AppGrid.iconScale(Bb7IconSize.Small)))
        assertEquals(40, AppGrid.iconSizeFor(120, 71, 31, AppGrid.iconScale(Bb7IconSize.Large)))
    }
}
