package io.github.aimindseye.tenseven.conversations

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.github.aimindseye.tenseven.model.HubMessageKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A stand-in for Sable Mail's feed: two inbox rows, and it remembers which message was marked read. */
class FakeMailFeed : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(COLUMNS).apply {
            addRow(arrayOf<Any?>(7L, "me@example.org", "Ada", "Lunch", "See you\n at  noon", 1000L, 0, "ref-7"))
            addRow(arrayOf<Any?>(8L, "me@example.org", "", "Old news", null, 500L, 1, null))
        }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        markedRead = uri.lastPathSegment
        latch.countDown()
        return 1
    }

    companion object {
        val COLUMNS = arrayOf("id", "account", "sender", "subject", "preview", "date", "read", "reference")

        @Volatile
        var markedRead: String? = null
        var latch = CountDownLatch(1)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SableMailFeedProviderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun install() {
        Robolectric.setupContentProvider(FakeMailFeed::class.java, "org.sableos.mail.hub")
    }

    @Test
    fun readIsNullWhenSableMailIsNotInstalled() {
        assertNull(SableMailFeed.read(context))
    }

    @Test
    fun readTurnsFeedRowsIntoEmailNotifications() {
        install()
        val rows = SableMailFeed.read(context)!!
        assertEquals(listOf("sablemail|7", "sablemail|8"), rows.map { it.key })
        val first = rows[0]
        assertEquals("Ada", first.conversationName)
        assertEquals(HubMessageKind.Email, first.kind)
        assertEquals(SableMailFeed.MAIL_PACKAGE, first.packageName)
        assertEquals(1000L, first.postedAt)
        assertEquals("Lunch · See you at noon", first.messages.single().text)
        assertFalse(first.messages.single().isHistoric)
        assertNotNull(first.contentIntent)
    }

    @Test
    fun readMailIsHistoricAndBlankSenderFallsBackToAccount() {
        install()
        val second = SableMailFeed.read(context)!![1]
        assertEquals("me@example.org", second.conversationName)
        assertTrue(second.messages.single().isHistoric)
        assertEquals("Old news", second.messages.single().text)
    }

    @Test
    fun markReadReachesSableMail() {
        install()
        FakeMailFeed.latch = CountDownLatch(1)
        val mail = SableMailFeed.read(context)!!.first()
        SableMailFeed.markRead(context, mail)
        assertTrue(FakeMailFeed.latch.await(5, TimeUnit.SECONDS))
        assertEquals("7", FakeMailFeed.markedRead)
    }

    @Test
    fun markReadIgnoresOtherApps() {
        install()
        FakeMailFeed.markedRead = null
        FakeMailFeed.latch = CountDownLatch(1)
        val other =
            SableMailFeed.read(context)!!.first().copy(key = "other|9")
        SableMailFeed.markRead(context, other)
        assertFalse(FakeMailFeed.latch.await(300, TimeUnit.MILLISECONDS))
        assertNull(FakeMailFeed.markedRead)
    }
}
