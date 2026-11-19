package io.github.aimindseye.tenseven.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.model.ExtraApps
import io.github.aimindseye.tenseven.model.HomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class L0CoreBehaviorTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun bb7StartsWithoutAnyCompanion() = startHome(HomeStyle.Bb7)

    @Test
    fun bb10StartsWithoutAnyCompanion() = startHome(HomeStyle.Bb10)

    @Test
    fun companionPresenceAndRemovalDoNotRequireLauncherReinstallation() {
        val manager = shadowOf(context.packageManager)
        assertNull(CompanionLaunches.intent(context, "phone"))
        val component = ComponentName(PHONE, "fixture.PhoneActivity")
        manager.addActivityIfNotPresent(component).apply {
            enabled = true
            exported = true
        }
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
        assertEquals(component, CompanionLaunches.intent(context, "phone")?.component)
        manager.removeActivity(component)
        manager.removePackage(PHONE)
        assertNull(CompanionLaunches.intent(context, "phone"))
    }

    @Test
    fun optionalChoicesDoNotInstallAppsOrAcquirePhoneRole() {
        ExtraAppsControl.apply(context, ExtraApps.ALL.map { it.id }.toSet())
        assertEquals(ExtraApps.ALL.map { it.id }.toSet(), ExtraAppsControl.enabledIds(context))
        assertNull(CompanionLaunches.intent(context, "phone"))
        assertNull(CompanionLaunches.intent(context, "calendar"))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun phoneAndCalendarHandoffsUsePublicAndroidActions() {
        val call = PeopleRepository.callIntent("123")
        assertEquals(Intent.ACTION_DIAL, call.action)
        assertEquals("tel", call.data?.scheme)
        assertNull(call.component)
        DefaultApps.launch(context, DefaultApps.CALENDAR)
        val calendar = requireNotNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertEquals(setOf(Intent.CATEGORY_APP_CALENDAR), calendar.selector?.categories)
        assertNull(calendar.component)
    }

    private fun startHome(style: HomeStyle) {
        assertNull(CompanionLaunches.intent(context, "phone"))
        assertNull(CompanionLaunches.intent(context, "calendar"))
        val state = StartStateRepository(context)
        state.saveOnboardingDone()
        state.saveHomeStyle(style)
        Robolectric.buildActivity(SableStartActivity::class.java).setup().use { controller ->
            assertNotNull(controller.get().window.decorView)
            assertEquals(style, state.loadHomeStyle())
            assertFalse(controller.get().isFinishing)
        }
    }

    private companion object {
        const val PHONE = "io.github.aimindseye.tenseven.phone.debug"
    }
}
