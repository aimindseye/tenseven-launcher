package io.github.aimindseye.tenseven.frames

import android.net.Uri
import android.os.Bundle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SableCoverProviderTest {
    private val uri = Uri.parse("content://org.sableos.launcher.covers")

    private fun call(
        method: String,
        extras: Bundle? = null,
    ): Bundle? {
        Robolectric.setupContentProvider(SableCoverProvider::class.java, "org.sableos.launcher.covers")
        return RuntimeEnvironment.getApplication().contentResolver.call(uri, method, null, extras)
    }

    @After
    fun clean() = SableFrameCovers.clearAll()

    @Test
    fun unknownMethodsAreRefused() {
        val result = call("somethingElse")
        assertNotNull(result)
        assertFalse(result!!.getBoolean(SableCoverProvider.RESULT_OK))
    }

    @Test
    fun setAndClearAnswerWithAResult() {
        val extras = Bundle().apply { putString(SableCoverProvider.EXTRA_DETAIL, "Now playing") }
        assertNotNull(call(SableCoverProvider.METHOD_SET, extras))
        assertNotNull(call(SableCoverProvider.METHOD_CLEAR))
    }

    @Test
    fun nothingElseIsOffered() {
        val provider = SableCoverProvider()
        assertEquals(true, provider.onCreate())
        assertNull(provider.query(uri, null, null, null, null))
        assertNull(provider.getType(uri))
        assertNull(provider.insert(uri, null))
        assertEquals(0, provider.delete(uri, null, null))
        assertEquals(0, provider.update(uri, null, null, null))
    }
}
