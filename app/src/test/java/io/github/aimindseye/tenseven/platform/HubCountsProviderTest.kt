package io.github.aimindseye.tenseven.platform

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.Settings
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.ConversationMessage
import io.github.aimindseye.tenseven.conversations.HubNotification
import io.github.aimindseye.tenseven.live.AppUnreadCount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Schema/metadata tests do not claim that Robolectric enforces Android signatures across UIDs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubCountsProviderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: HubCountsProvider
    private val uri get() = HubCountsProvider.appsUri(context.packageName)

    @Before
    fun setUp() {
        ConversationHub.clearNow()
        LockLayerState.update(emptyMap())
        access(true)
        provider = Robolectric.buildContentProvider(HubCountsProvider::class.java).create().get()
    }

    @After
    fun tearDown() {
        provider.shutdown()
        ConversationHub.clearNow()
        LockLayerState.update(emptyMap())
        access(false)
    }

    @Test
    fun deniedNotificationAccessReturnsNoCursorEvenWithWaitingCounts() {
        LockLayerState.update(mapOf("fixture.app" to COUNT))
        access(false)
        assertNull(provider.query(uri, null, null, null, null))
    }

    @Test
    fun emptyCountsReturnAnEmptyTwoColumnCursor() {
        provider.query(uri, null, null, null, null).use { cursor ->
            assertNotNull(cursor)
            assertEquals(0, cursor?.count)
            assertArrayEquals(arrayOf("package", "count"), cursor?.columnNames)
            assertEquals(uri, cursor?.notificationUri)
        }
    }

    @Test
    fun providerReturnsCountsInHubOrderAndNeverMessageColumns() {
        LockLayerState.update(mapOf("b.app" to COUNT, "a.app" to COUNT, "ignored.app" to 0))
        provider.query(uri, arrayOf("body", "sender", "title"), "body", arrayOf("secret"), "title").use { cursor ->
            val rows = requireNotNull(cursor)
            assertArrayEquals(arrayOf("package", "count"), rows.columnNames)
            assertEquals(2, rows.count)
            assertEquals(-1, rows.getColumnIndex("body"))
            assertEquals(-1, rows.getColumnIndex("sender"))
            assertEquals(-1, rows.getColumnIndex("title"))
            rows.moveToFirst()
            assertEquals("a.app", rows.getString(0))
            assertEquals(COUNT, rows.getInt(1))
            rows.moveToNext()
            assertEquals("b.app", rows.getString(0))
        }
    }

    @Test
    fun wrongAuthorityOrPathCannotBeQueriedDirectly() {
        val other = HubCountsProvider.appsUri("other.application")
        assertNull(provider.query(other, null, null, null, null))
        assertNull(provider.query(uri.buildUpon().appendPath("messages").build(), null, null, null, null))
        assertNull(provider.query(Uri.parse("https://example.com/apps"), null, null, null, null))
        assertNull(provider.getType(other))
    }

    @Test
    fun writesAreRejectedEvenForTheOwningProcess() {
        val values = ContentValues()
        assertThrows(UnsupportedOperationException::class.java) { provider.insert(uri, values) }
        assertThrows(UnsupportedOperationException::class.java) { provider.update(uri, values, null, null) }
        assertThrows(UnsupportedOperationException::class.java) { provider.delete(uri, null, null) }
    }

    @Test
    fun productionAndDebugAuthorityDerivationCannotCollide() {
        assertEquals(
            "content://io.github.aimindseye.tenseven.hubcounts/apps",
            HubCountsProvider.appsUri(PRODUCTION).toString(),
        )
        assertEquals("content://io.github.aimindseye.tenseven.debug.l0.hubcounts/apps", uri.toString())
        assertFalse(uri == HubCountsProvider.appsUri(PRODUCTION))
    }

    @Test
    fun manifestHasDistinctSignatureReadAndWritePermissions() {
        val manager = context.packageManager
        val info = manager.getProviderInfo(ComponentName(context, HubCountsProvider::class.java), 0)
        assertEquals(context.packageName + ".hubcounts", info.authority)
        assertEquals(context.packageName + ".permission.READ_HUB_COUNTS", info.readPermission)
        assertEquals(context.packageName + ".permission.WRITE_HUB_COUNTS", info.writePermission)
        assertFalse(info.grantUriPermissions)
        for (permission in listOf(info.readPermission, info.writePermission)) {
            val declaration = manager.getPermissionInfo(permission, 0)
            assertEquals(
                PermissionInfo.PROTECTION_SIGNATURE,
                declaration.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE,
            )
        }
    }

    @Test
    fun homeBarAndProviderPickTheSameWaitingCounts() =
        runBlocking {
            LockLayerState.update(mapOf("b.app" to COUNT, "a.app" to COUNT))
            val home = LiveSurfaceRepository(context).snapshot().hubApps
            assertEquals(home, providerRows())
        }

    @Test
    fun fallbackCountsStillExposeNoConversationContent() =
        runBlocking {
            ConversationHub.replace(
                listOf(
                    HubNotification(
                        key = "n1",
                        conversationId = "pr143-fixture-conversation",
                        conversationName = "Private sender",
                        packageName = "fixture.app",
                        appName = "Private notification title",
                        postedAt = System.currentTimeMillis(),
                        isOngoing = false,
                        messages =
                            listOf(
                                ConversationMessage(
                                    id = "private-fixture-message",
                                    conversationId = "pr143-fixture-conversation",
                                    notificationKey = "n1",
                                    packageName = "fixture.app",
                                    appName = "Private notification title",
                                    text = "Private message body",
                                    timestamp = System.currentTimeMillis(),
                                    senderName = "Private sender",
                                    isOutgoing = false,
                                ),
                            ),
                        contentIntent = null,
                        replyAction = null,
                    ),
                ),
            )
            val expected = listOf(AppUnreadCount("fixture.app", 1))
            assertEquals(expected, providerRows())
            assertEquals(expected, LiveSurfaceRepository(context).snapshot().hubApps)
        }

    private fun providerRows(): List<AppUnreadCount> =
        requireNotNull(provider.query(uri, null, null, null, null)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(AppUnreadCount(cursor.getString(0), cursor.getInt(1)))
            }
        }

    private fun access(granted: Boolean) {
        val value =
            if (granted) {
                context.packageName + "/io.github.aimindseye.tenseven.conversations.ConversationListenerService"
            } else {
                ""
            }
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", value)
    }

    private companion object {
        const val COUNT = 7
        const val PRODUCTION = "io.github.aimindseye.tenseven"
    }
}
