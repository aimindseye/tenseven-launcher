package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.os.UserHandle

/**
 * A Context for another Android user (a work or private profile).
 *
 * Context.createContextAsUser is a system API and needs INTERACT_ACROSS_USERS, which only a platform-signed
 * launcher holds. It is reached by reflection so the same source builds against the public SDK; where Android
 * refuses it returns null and the caller reports that profile's data as unavailable instead of guessing.
 */
internal object UserContexts {
    fun forUser(
        context: Context,
        user: UserHandle,
    ): Context? =
        runCatching {
            Context::class.java
                .getMethod("createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType)
                .invoke(context, user, 0) as? Context
        }.getOrNull()
}
