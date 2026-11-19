package io.github.aimindseye.tenseven.ui

import android.content.Context
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.stableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class StartHomeActionsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun editPersistsInOriginalOrderBeforeReturningAndScrollingHome() {
        val fixture = StartActionFixture(context)
        fixture.session.navigation.destination = SableStartDestination.EditHome
        val edit = HomeEdit(listOf("chosen"), QuickBar.DEFAULT, GlanceWidgets.DEFAULT, setOf(HomeLayout.WEATHER))
        fixture.home.saveEdit(edit)
        assertEquals(listOf("grid", "bar", "widgets", "hidden", "scroll"), fixture.writes)
        assertEquals(
            List(4) { SableStartDestination.EditHome } + SableStartDestination.Start,
            fixture.writeDestinations,
        )
        assertEquals(listOf("chosen"), fixture.session.home.savedGridOrder)
        assertEquals(QuickBar.DEFAULT, fixture.session.home.quickBarTokens)
        assertEquals(setOf(HomeLayout.WEATHER), fixture.session.home.hiddenSections)
        assertEquals(1, fixture.barWrites.size)
        assertNull(fixture.barWrites.single())
    }

    @Test
    fun replacingAnIdenticalSlotDoesNotRepeatPersistence() {
        val fixture = StartActionFixture(context)
        val app = fixture.app("Pinned")
        fixture.home.replaceSlot(app, 0)
        fixture.home.replaceSlot(app, 0)
        assertEquals(1, fixture.barWrites.size)
        assertEquals(
            QuickBar.appToken(app.stableKey()),
            fixture.session.home.quickBarTokens
                .first(),
        )
        assertEquals(SableStartDestination.Peek, fixture.session.navigation.destination)
    }

    @Test
    fun fullQuickBarRequestsSlotPickerWithoutChangingPreferences() {
        val fixture = StartActionFixture(context)
        fixture.home.toggleBaseBar(fixture.app("Unpinned"), -1)
        assertEquals(SableStartDestination.SlotPicker, fixture.session.navigation.destination)
        assertEquals(QuickBar.DEFAULT, fixture.session.home.quickBarTokens)
        assertTrue(fixture.barWrites.isEmpty())
    }

    @Test
    fun letterShortcutWritesRefreshTheVersionAndRemovalDeletesOnlyTheSelection() {
        val fixture = StartActionFixture(context)
        val app = fixture.app("Shortcut")
        fixture.home.setLetterShortcut(app, 'q')
        assertEquals(app.component.flattenToString(), fixture.letters.load().componentFor('q'))
        assertEquals(1, fixture.session.launches.shortcutVersion)
        fixture.home.setLetterShortcut(app, null)
        assertNull(fixture.letters.load().letterOf(app.component.flattenToString()))
        assertEquals(2, fixture.session.launches.shortcutVersion)
    }
}
