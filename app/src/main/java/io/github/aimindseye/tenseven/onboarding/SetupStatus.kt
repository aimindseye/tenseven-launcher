package io.github.aimindseye.tenseven.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.conversations.ConversationAccess

/** Reads what has been granted, from Android. Cheap; call it again when the user comes back from Settings. */
object SetupStatus {
    fun read(context: Context): SetupState =
        SetupState(
            homeRoleHeld = isHomeRoleHeld(context),
            contactsGranted = granted(context, Manifest.permission.READ_CONTACTS),
            photosGranted = granted(context, photosPermission()),
            notificationAccess = ConversationAccess.isGranted(context),
            usageAccess = hasUsageAccess(context),
        )

    /** READ_MEDIA_IMAGES from Android 13; READ_EXTERNAL_STORAGE before. */
    fun photosPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isHomeRoleHeld(context: Context): Boolean =
        runCatching {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        }.getOrDefault(false)

    fun hasUsageAccess(context: Context): Boolean =
        runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

    /** The package that installed Tenseven (the Play Store, a file manager, ...), or null when Android does not say. */
    fun installer(context: Context): String? =
        runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()

    private fun granted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
