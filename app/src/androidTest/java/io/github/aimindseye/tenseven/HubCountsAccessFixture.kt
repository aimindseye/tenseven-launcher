package io.github.aimindseye.tenseven

import android.database.ContentObserver
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Changes notification access only for the isolated L0 debug test target and restores it. */
internal class HubCountsAccessFixture : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val original = ConversationAccess.isGranted(context)

    init {
        check(context.packageName == "io.github.aimindseye.tenseven.debug.l0")
    }

    fun setGranted(granted: Boolean) {
        val signal = CountDownLatch(1)
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    signal.countDown()
                }
            }
        val operation = if (granted) "allow_listener" else "disallow_listener"
        val component = context.packageName + "/io.github.aimindseye.tenseven.conversations.ConversationListenerService"
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor("enabled_notification_listeners"), false, observer)
        try {
            val descriptor = instrumentation.uiAutomation.executeShellCommand("cmd notification $operation $component")
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(descriptor)
                .use { it.readBytes() }
            if (ConversationAccess.isGranted(context) != granted) {
                check(signal.await(SETTINGS_SECONDS, TimeUnit.SECONDS))
            }
            check(ConversationAccess.isGranted(context) == granted)
        } finally {
            resolver.unregisterContentObserver(observer)
        }
    }

    override fun close() {
        setGranted(original)
    }

    private companion object {
        const val SETTINGS_SECONDS = 3L
    }
}
