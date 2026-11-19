package io.github.aimindseye.tenseven.model

import android.content.ComponentName
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class Bb7RecentAppsTest {
    private fun app(
        label: String,
        packageName: String,
        profile: String? = null,
    ): AppEntry {
        val component = ComponentName(packageName, "$packageName.Main")
        return AppEntry(label, component, Process.myUserHandle(), profileLabel = profile)
    }

    @Test
    fun newestLaunchWinsWithoutAlphabeticalOrFrequencySorting() {
        val old = app("Alpha", "fixture.old")
        val new = app("Zulu", "fixture.new")
        val events = listOf(ForegroundEvent("fixture.old", OLD_TIME), ForegroundEvent("fixture.new", NEW_TIME))
        val usage = AppUsage.EMPTY.record(old.stableKey()).record(old.stableKey())
        assertEquals(listOf(new, old), Bb7RecentApps.select(listOf(old, new), events))
        assertEquals(listOf(old), Bb7Home.panelApps(Bb7Panel.Frequent, listOf(old, new), null, usage))
    }

    @Test
    fun repeatedLaunchesAndMultipleActivitiesDoNotDuplicateAnApp() {
        val first = app("One", "fixture.one")
        val otherActivity = first.copy(component = ComponentName("fixture.one", "fixture.one.Second"))
        val events = listOf(ForegroundEvent("fixture.one", OLD_TIME), ForegroundEvent("fixture.one", NEW_TIME))
        assertEquals(listOf(first), Bb7RecentApps.select(listOf(first, otherActivity), events))
    }

    @Test
    fun unavailableAndOtherProfileAppsAreExcluded() {
        val personal = app("Personal", "fixture.personal")
        val work = app("Work", "fixture.work", "work")
        val events =
            listOf(
                ForegroundEvent("fixture.gone", NEW_TIME),
                ForegroundEvent("fixture.work", NEW_TIME),
                ForegroundEvent("fixture.personal", OLD_TIME),
            )
        assertEquals(listOf(personal), Bb7RecentApps.select(listOf(personal, work), events))
        assertTrue(Bb7RecentApps.select(emptyList(), events).isEmpty())
    }

    @Test
    fun noHistoryNeverFallsBackToAllOrFavorites() {
        val entry = app("Installed", "fixture.installed")
        assertTrue(Bb7Home.panelApps(Bb7Panel.Recent, listOf(entry), listOf(entry.stableKey())).isEmpty())
        assertTrue(Bb7RecentApps.select(listOf(entry), listOf(ForegroundEvent("fixture.installed", 0))).isEmpty())
    }

    private companion object {
        const val OLD_TIME = 10L
        const val NEW_TIME = 20L
    }
}
