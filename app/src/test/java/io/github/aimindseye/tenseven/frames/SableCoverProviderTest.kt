package io.github.aimindseye.tenseven.frames

import android.net.Uri
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SableCoverProviderTest {
    private val provider = SableCoverProvider()

    private fun ok(result: Bundle) = result.getBoolean(SableCoverProvider.RESULT_OK)

    @Test
    fun unknownMethodsAreRefused() {
        assertFalse(ok(provider.call("somethingElse", null, null)))
    }

    @Test
    fun aCallWithoutAVerifiedCallerCannotClear() {
        assertFalse(ok(provider.call(SableCoverProvider.METHOD_CLEAR, null, null)))
    }

    @Test
    fun aCallWithoutAVerifiedCallerCannotSet() {
        val extras = Bundle().apply { putString(SableCoverProvider.EXTRA_TITLE, "Now") }
        assertFalse(ok(provider.call(SableCoverProvider.METHOD_SET, null, extras)))
    }

    @Test
    fun nothingElseIsOffered() {
        val uri = Uri.parse("content://org.sableos.launcher.covers")
        assertEquals(true, provider.onCreate())
        assertNull(provider.query(uri, null, null, null, null))
        assertNull(provider.getType(uri))
        assertNull(provider.insert(uri, null))
        assertEquals(0, provider.delete(uri, null, null))
        assertEquals(0, provider.update(uri, null, null, null))
    }
}
