package io.github.aimindseye.tenseven.platform

import android.content.ComponentName
import android.content.Context
import android.os.Process
import io.github.aimindseye.tenseven.model.AppEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaFavouritesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun absentWhenSableMediaIsNotInstalled() {
        assertFalse(MediaFavourites.available(context))
        assertNull(MediaFavourites.tile(context))
    }

    @Test
    fun opensWithoutCrashingWhenNothingAnswers() {
        MediaFavourites.open(context)
    }

    @Test
    fun recognisesOnlyItsOwnTile() {
        val tileComponent = ComponentName(MediaFavourites.TILE_PACKAGE, "Favourites")
        val tile = AppEntry("Favourites", tileComponent, Process.myUserHandle())
        val other = AppEntry("Clock", ComponentName("com.example.clock", "Main"), Process.myUserHandle())
        assertTrue(MediaFavourites.isTile(tile))
        assertFalse(MediaFavourites.isTile(other))
    }
}
