package org.sableos.start.platform

import android.content.pm.PackageManager
import java.lang.reflect.Proxy

/**
 * Listens for runtime-permission grants and revokes anywhere on the device (the signal carries a uid).
 *
 * PackageManager.OnPermissionsChangedListener is a system API: it cannot be named in an app built against the
 * public SDK, and Android only lets platform-signed apps holding OBSERVE_GRANT_REVOKE_PERMISSIONS register it.
 * It is reached by reflection so the same source builds everywhere: [register] returns false when Android
 * refuses (an ordinary install), and the caller keeps working without the signal.
 */
internal class PermissionChangeSignal(
    private val packageManager: PackageManager,
    private val onChanged: (uid: Int) -> Unit,
) {
    private var listener: Any? = null

    fun register(): Boolean {
        if (listener != null) return true
        return runCatching {
            val type = Class.forName("android.content.pm.PackageManager\$OnPermissionsChangedListener")
            val proxy =
                Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                    if (method.name == "onPermissionsChanged") (args?.firstOrNull() as? Int)?.let(onChanged)
                    null
                }
            PackageManager::class.java
                .getMethod("addOnPermissionsChangeListener", type)
                .invoke(packageManager, proxy)
            listener = proxy
            true
        }.getOrDefault(false)
    }

    fun unregister() {
        val registered = listener ?: return
        listener = null
        runCatching {
            val type = Class.forName("android.content.pm.PackageManager\$OnPermissionsChangedListener")
            PackageManager::class.java
                .getMethod("removeOnPermissionsChangeListener", type)
                .invoke(packageManager, registered)
        }
    }
}
