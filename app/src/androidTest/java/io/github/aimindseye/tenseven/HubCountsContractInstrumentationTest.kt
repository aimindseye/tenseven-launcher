package io.github.aimindseye.tenseven

import android.content.ComponentName
import android.content.ContentValues
import android.content.pm.PermissionInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.platform.HubCountsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Cross-UID denial is additionally tested from the isolated emulator shell. */
class HubCountsContractInstrumentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val uri get() = HubCountsProvider.appsUri(context.packageName)

    @Test
    fun installedProviderIsIsolatedAndSignatureProtected() {
        val manager = context.packageManager
        val provider = manager.getProviderInfo(ComponentName(context, HubCountsProvider::class.java), 0)
        assertEquals(context.packageName + ".hubcounts", provider.authority)
        assertEquals(context.packageName + ".permission.READ_HUB_COUNTS", provider.readPermission)
        assertEquals(context.packageName + ".permission.WRITE_HUB_COUNTS", provider.writePermission)
        assertFalse(provider.grantUriPermissions)
        for (permission in listOf(provider.readPermission, provider.writePermission)) {
            val info = manager.getPermissionInfo(permission, 0)
            val protection = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            assertEquals(PermissionInfo.PROTECTION_SIGNATURE, protection)
        }
    }

    @Test
    fun notificationAccessDenialProducesNoCursor() {
        HubCountsAccessFixture().use { access ->
            access.setGranted(false)
            assertFalse(ConversationAccess.isGranted(context))
            assertNull(context.contentResolver.query(uri, null, null, null, null))
        }
    }

    @Test
    fun evenTheOwnerCannotWriteCounts() {
        val resolver = context.contentResolver
        val values = ContentValues()
        assertThrows(UnsupportedOperationException::class.java) { resolver.insert(uri, values) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.update(uri, values, null, null) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.delete(uri, null, null) }
    }
}
