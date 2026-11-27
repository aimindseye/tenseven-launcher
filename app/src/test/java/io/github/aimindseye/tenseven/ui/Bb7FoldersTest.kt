package io.github.aimindseye.tenseven.ui

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Bb7FoldersTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        Bb7Folders.names(context).forEach { Bb7Folders.delete(context, it) }
    }

    @Test
    fun keysRoundTrip() {
        assertEquals("folder:Games", Bb7Folders.key("Games"))
        assertTrue(Bb7Folders.isFolderKey("folder:Games"))
        assertFalse(Bb7Folders.isFolderKey("com.example/App"))
        val entry = Bb7Folders.entry("Games")
        assertTrue(Bb7Folders.isFolder(entry))
        assertEquals("Games", Bb7Folders.nameOf(entry))
    }

    @Test
    fun createPicksFirstFreeName() {
        assertEquals("Folder", Bb7Folders.create(context))
        assertEquals("Folder 2", Bb7Folders.create(context))
        Bb7Folders.delete(context, "Folder")
        assertEquals("Folder", Bb7Folders.create(context))
    }

    @Test
    fun addKeepsOrderWithoutDuplicates() {
        val name = Bb7Folders.create(context)
        Bb7Folders.add(context, name, "a")
        Bb7Folders.add(context, name, "b")
        Bb7Folders.add(context, name, "a")
        assertEquals(listOf("a", "b"), Bb7Folders.contents(context, name))
        Bb7Folders.remove(context, name, "a")
        assertEquals(listOf("b"), Bb7Folders.contents(context, name))
    }

    @Test
    fun renameKeepsAppsAndMakesNameUnique() {
        val first = Bb7Folders.create(context)
        val second = Bb7Folders.create(context)
        Bb7Folders.add(context, first, "a")
        assertEquals("Work", Bb7Folders.rename(context, first, "  Work "))
        assertEquals(listOf("a"), Bb7Folders.contents(context, "Work"))
        assertEquals("Work 2", Bb7Folders.rename(context, second, "Work"))
        assertEquals(listOf("Work", "Work 2"), Bb7Folders.names(context))
    }

    @Test
    fun renameRejectsBlankOrUnchanged() {
        val name = Bb7Folders.create(context)
        assertNull(Bb7Folders.rename(context, name, "   "))
        assertNull(Bb7Folders.rename(context, name, name))
    }

    @Test
    fun changesBumpVersion() {
        val before = Bb7Folders.version
        Bb7Folders.create(context)
        assertTrue(Bb7Folders.version > before)
    }
}
