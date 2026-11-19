package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import io.github.aimindseye.tenseven.features.AndroidViewFixtures as Views

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneDialBehaviorTest {
    @Test
    fun keypadEditsNumberAndLongPressesWithoutOpeningKeyboardOrPlacingCall() {
        Views.permission(Manifest.permission.READ_CONTACTS, false)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = activity.window.decorView
            val input = Views.input(root, "Enter number")
            assertFalse(input.showSoftInputOnFocus)
            Views.described(root, "1 key").performClick()
            Views.described(root, "2 ABC").performClick()
            assertEquals("12", activity.dialNumber)
            Views.described(root, "Delete last digit").performClick()
            assertEquals("1", input.text.toString())
            Views.described(root, "0 +").performLongClick()
            assertEquals("1+", activity.dialNumber)
            Views.described(root, "Delete last digit").performLongClick()
            assertEquals("", activity.dialNumber)
            Views.described(root, "Delete last digit").performClick()
            Views.described(root, "Place call").performClick()
            assertEquals("Enter a number first", ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun numberMatchSelectionUpdatesNumberAndCursorAndRecreationKeepsDialText() {
        PhoneFixtureRecords.contacts()
        Views.permission(Manifest.permission.READ_CONTACTS, true)
        Robolectric.buildActivity(TenSevenPhoneActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = activity.window.decorView
            val input = Views.input(root, "Enter number")
            input.setText("0001")
            Views.described(root, "Dial Alex Fixture ${PhoneFixtureRecords.FIRST_NUMBER}").performClick()
            assertEquals(PhoneFixtureRecords.FIRST_NUMBER, activity.dialNumber)
            assertEquals(input.length(), input.selectionStart)
            controller.recreate()
            assertEquals(PhoneFixtureRecords.FIRST_NUMBER, controller.get().dialNumber)
            assertTrue(Views.input(controller.get().window.decorView, "Enter number").isFocusable)
        }
    }
}
