package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.platform.HubSourceRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubAutomaticSourcesTest {
    @Test fun defaultAndUpgradeFollowAndroidSourcesWithOptionalFilters() {
        val context = isolatedContext()
        val prefs = context.getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            // A saved selection from the previous version must not block new Android-delivered sources.
            prefs.edit().putStringSet(StartStateRepository.KEY_HUB_ENABLED, setOf("previous.mail")).commit()
            val repository = HubSourceRepository(context)
            assertTrue(repository.includes("new.app"))
            val settings = HubSourceSettings(context)
            settings.toggle("new.app")
            assertFalse(HubSourceRepository(context).includes("new.app"))
            assertTrue(repository.includes("another.app"))
            settings.setAutomatic(false)
            assertTrue(repository.includes("previous.mail"))
            assertFalse(repository.includes("another.app"))
            settings.setAutomatic(true)
            assertFalse(repository.includes("new.app"))
            assertTrue(repository.includes("another.app"))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private fun isolatedContext(): Context =
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ): SharedPreferences = super.getSharedPreferences("hub-automatic-test-$name", mode)
        }
}
