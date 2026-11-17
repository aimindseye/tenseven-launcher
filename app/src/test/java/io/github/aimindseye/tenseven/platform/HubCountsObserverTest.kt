package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import io.github.aimindseye.tenseven.conversations.ConversationHub
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubCountsObserverTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: HubCountsProvider

    @Before
    fun setUp() {
        ConversationHub.clearNow()
        LockLayerState.update(emptyMap())
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            context.packageName + "/io.github.aimindseye.tenseven.conversations.ConversationListenerService",
        )
        provider = Robolectric.buildContentProvider(HubCountsProvider::class.java).create().get()
    }

    @After
    fun tearDown() {
        provider.shutdown()
        LockLayerState.update(emptyMap())
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
    }

    @Test
    fun countChangesNotifyOnceAndQueriesNeverLoop() {
        val updates = observe()
        try {
            LockLayerState.update(mapOf("fixture.app" to 1))
            assertEquals(1, updates.calls)
            LockLayerState.update(mapOf("fixture.app" to 1))
            ConversationHub.clearNow()
            provider.query(updates.uri, null, null, null, null)?.close()
            assertEquals(1, updates.calls)
            LockLayerState.update(mapOf("fixture.app" to 2))
            assertEquals(2, updates.calls)
            assertEquals(updates.uri, updates.lastUri)
        } finally {
            context.contentResolver.unregisterContentObserver(updates)
        }
    }

    @Test
    fun lossOfAccessNotifiesReadersToFallBack() {
        val updates = observe()
        try {
            Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
            ConversationHub.clearNow()
            assertEquals(1, updates.calls)
            ConversationHub.clearNow()
            assertEquals(1, updates.calls)
        } finally {
            context.contentResolver.unregisterContentObserver(updates)
        }
    }

    @Test
    fun repeatedCreationAndShutdownDoNotRetainDuplicateSubscriptions() {
        val updates = observe()
        try {
            provider.onCreate()
            LockLayerState.update(mapOf("fixture.app" to 1))
            assertEquals(1, updates.calls)
            provider.shutdown()
            LockLayerState.update(mapOf("fixture.app" to 2))
            ConversationHub.clearNow()
            assertEquals(1, updates.calls)
        } finally {
            context.contentResolver.unregisterContentObserver(updates)
        }
    }

    private fun observe(): Updates =
        Updates(HubCountsProvider.appsUri(context.packageName)).also {
            context.contentResolver.registerContentObserver(it.uri, false, it)
        }

    private class Updates(
        val uri: Uri,
    ) : ContentObserver(null) {
        var calls = 0
        var lastUri: Uri? = null

        override fun onChange(
            selfChange: Boolean,
            uri: Uri?,
        ) {
            calls++
            lastUri = uri
        }
    }
}
