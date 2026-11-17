package io.github.aimindseye.tenseven

import android.database.ContentObserver
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.platform.HubCountsProvider
import io.github.aimindseye.tenseven.platform.LockLayerState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses real resolver delivery while restoring the original notification-access state. */
class HubCountsObserverInstrumentationTest {
    @Test
    fun realResolverDeliversCountOnlyObserverChanges() {
        HubCountsAccessFixture().use { access ->
            access.setGranted(true)
            verifyObserver()
        }
    }

    private fun verifyObserver() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val uri = HubCountsProvider.appsUri(context.packageName)
        val prior = LockLayerState.counts
        val signal = CountDownLatch(1)
        val observer =
            object : ContentObserver(null) {
                override fun onChange(
                    selfChange: Boolean,
                    changed: Uri?,
                ) {
                    if (changed == uri) signal.countDown()
                }
            }
        context.contentResolver.query(uri, null, null, null, null)?.close()
        context.contentResolver.registerContentObserver(uri, false, observer)
        try {
            instrumentation.runOnMainSync {
                LockLayerState.update(mapOf("fixture.application" to UNREAD))
                context.contentResolver.query(uri, null, null, null, null).use { value ->
                    val cursor = requireNotNull(value)
                    assertArrayEquals(arrayOf("package", "count"), cursor.columnNames)
                    assertTrue(cursor.moveToFirst())
                    assertEquals("fixture.application", cursor.getString(0))
                    assertEquals(UNREAD, cursor.getInt(1))
                    assertEquals(-1, cursor.getColumnIndex("body"))
                }
            }
            assertTrue(signal.await(OBSERVER_SECONDS, TimeUnit.SECONDS))
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
            instrumentation.runOnMainSync { LockLayerState.update(prior) }
        }
    }

    private companion object {
        const val UNREAD = 7
        const val OBSERVER_SECONDS = 3L
    }
}
