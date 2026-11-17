package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class StartNavigationActionsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun homeEntryPointsReturnHomeWithoutLaunchingOrRecordingTheLauncher() {
        val fixture = StartActionFixture(context)
        fixture.session.navigation.destination = SableStartDestination.Search
        fixture.session.launches.recentKeys = listOf("existing")
        val entry = fixture.app("Home").copy(component = ComponentName(context, SableStartActivity::class.java))
        fixture.applications.launchApp(entry)
        fixture.applications.launchApp(
            entry.copy(component = ComponentName(context.packageName, "io.github.aimindseye.tenseven.LauncherEntry")),
        )
        assertEquals(listOf(StartPages.HOME, StartPages.HOME), fixture.pages)
        assertEquals(SableStartDestination.Start, fixture.session.navigation.destination)
        assertTrue(fixture.launched.isEmpty())
        assertEquals(listOf("existing"), fixture.session.launches.recentKeys)
    }

    @Test
    fun successfulLaunchesAreDeduplicatedNewestFirstAndBounded() {
        val fixture = StartActionFixture(context)
        val entries = (0..MAX_SESSION_RECENTS).map { fixture.app("App" + it) }
        entries.forEach(fixture.applications::launchApp)
        fixture.applications.launchApp(entries[2])
        fixture.applications.launchApp(entries[2])
        val recent = fixture.session.launches.recentKeys
        assertEquals(MAX_SESSION_RECENTS, recent.size)
        assertEquals(entries[2].stableKey(), recent.first())
        assertEquals(recent.size, recent.distinct().size)
        assertFalse(entries.first().stableKey() in recent)
    }

    @Test
    fun failedLaunchesDoNotChangeSessionHistory() {
        val fixture = StartActionFixture(context)
        fixture.session.launches.recentKeys = listOf("previous")
        fixture.launchAccepted = false
        fixture.applications.launchApp(fixture.app("Rejected"))
        assertEquals(listOf("previous"), fixture.session.launches.recentKeys)
        assertEquals(1, fixture.launched.size)
    }

    @Test
    fun notificationLaunchesSelectThePersonalInstalledEntryOnly() {
        val component = ComponentName("fixture.mail", "fixture.mail.Main")
        val work = AppEntry("Work", component, Process.myUserHandle(), profileLabel = AppProfiles.WORK)
        val personal = work.copy(label = "Personal", profileLabel = null)
        val fixture = StartActionFixture(context, listOf(work, personal))
        fixture.applications.openNotificationApp("fixture.missing")
        fixture.applications.openNotificationApp("fixture.mail")
        assertEquals(listOf(personal), fixture.launched)
    }

    @Test
    fun secondInstanceSelectionUsesDualProfileAndFallsBackToTheOriginal() {
        val component = ComponentName("fixture.app", "fixture.app.Main")
        val personal = AppEntry("Personal", component, Process.myUserHandle())
        val dual = personal.copy(label = "Dual", profileLabel = AppProfiles.DUAL)
        val fixture = StartActionFixture(context, listOf(personal, dual))
        assertSame(dual, fixture.applications.secondInstance(personal))
        assertSame(personal, fixture.applications.secondInstance(dual))
        fixture.applications.launchSecond(personal)
        fixture.applications.launchSecond(fixture.app("Single"))
        assertEquals(dual, fixture.launched.first())
        assertEquals("Single", fixture.launched.last().label)
    }

    @Test
    fun navigationPreservesReturnDestinationAndHubFilterWithoutAcquiringRoles() {
        val fixture = StartActionFixture(context)
        val app = fixture.app("Selected")
        fixture.navigation.openSearch(SableStartDestination.Apps, "typed")
        assertEquals("typed", fixture.session.navigation.searchSeed)
        fixture.navigation.navigateBack()
        assertEquals(SableStartDestination.Apps, fixture.session.navigation.destination)
        fixture.navigation.openPeek(app, SableStartDestination.Start)
        assertSame(app, fixture.session.navigation.selectedApp)
        fixture.navigation.navigateBack()
        assertEquals(SableStartDestination.Start, fixture.session.navigation.destination)
        fixture.navigation.showHub(HubFilter.Contacts)
        assertEquals(HubFilter.Contacts, fixture.session.navigation.hubFilter)
        assertEquals(listOf(StartPages.HUB), fixture.pages)
        fixture.navigation.dial("+123")
        assertEquals(Intent.ACTION_DIAL, fixture.intents.single().action)
        assertNull(fixture.intents.single().component)
        assertTrue(fixture.launched.isEmpty())
    }

    @Test
    fun pagerPolicyKeepsWidgetsLeadAndClampsAbsoluteMoves() {
        assertEquals(StartPages.HOME, StartPagerPolicy.initialPage("widgets", 0))
        assertEquals(0, StartPagerPolicy.initialPage("widgets", 1))
        assertEquals(StartPages.HOME + 1, StartPagerPolicy.initialPage("start", 1))
        assertEquals(StartPages.HUB + 1, StartPagerPolicy.initialPage("hub", 1))
        assertEquals(0, StartPagerPolicy.moveTarget(0, -1, 4))
        assertEquals(3, StartPagerPolicy.moveTarget(3, 1, 4))
        assertEquals(2, StartPagerPolicy.moveTarget(1, 1, 4))
    }
}
