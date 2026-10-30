package io.github.aimindseye.tenseven.platform

import android.os.UserHandle
import android.os.UserManager

/**
 * `UserHandle.getIdentifier()` and `UserManager.isManagedProfile(int)` are hidden APIs: SableOS builds against
 * the system SDK, where they exist, but an ordinary app cannot name them. They are reached by reflection so the
 * same source builds against the public SDK.
 */
private val identifierMethod by lazy { runCatching { UserHandle::class.java.getMethod("getIdentifier") }.getOrNull() }

private val managedProfileMethod by lazy {
    runCatching { UserManager::class.java.getMethod("isManagedProfile", Int::class.javaPrimitiveType) }.getOrNull()
}

/** The Android user id. UserHandle.hashCode() is the same number, which is the fallback if reflection is refused. */
internal val UserHandle.identifier: Int
    get() = runCatching { identifierMethod?.invoke(this) as? Int }.getOrNull() ?: hashCode()

/** True when [user] is a work (managed) profile; false when Android does not say. */
internal fun UserManager.isManagedProfile(user: UserHandle): Boolean =
    runCatching { managedProfileMethod?.invoke(this, user.identifier) as? Boolean }.getOrNull() ?: false
