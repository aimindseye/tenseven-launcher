package io.github.aimindseye.tenseven

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * The Tenseven lock layer: a BlackBerry-style screen shown over the system lock screen when the screen wakes. It is
 * only a cover. The phone's own lock stays underneath, so swiping up, pressing Enter or Space, or tapping an app asks
 * Android to dismiss the real lock first (PIN, pattern or fingerprint) and only then opens anything. It shows the
 * time, the date, an icon and count for each app with notifications waiting, and a camera shortcut. Message text
 * is revealed only after Android authentication and the user’s message-preview opt-in.
 */
class LockLayerActivity : ComponentActivity() {
    private var selectedPackage by mutableStateOf<String?>(null)
    private var revealAfterUnlock by mutableStateOf(false)
    private val closer =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF || !revealAfterUnlock) finish()
            }
        }

    /** Whether this is a preview from a settings screen (it closes itself rather than asking for the PIN). */
    private val isPreview get() = intent?.hasExtra(EXTRA_RETURN_TO) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        ContextCompat.registerReceiver(this, closer, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val state = StartStateRepository(this)
        val sources =
            io.github.aimindseye.tenseven.platform
                .HubSourceRepository(this)
        // Preview opt-in never overrides Android authentication on this cover.
        val showText = state.loadHubPeekText()
        setContent {
            val enabled = sources.policy().resolve(io.github.aimindseye.tenseven.platform.LockLayerState.counts.keys)
            LauncherTheme(window = window) {
                io.github.aimindseye.tenseven.ui.LockNotificationScreen(
                    enabled = enabled,
                    showText = showText,
                    selectedPackage = selectedPackage,
                    actions =
                        io.github.aimindseye.tenseven.ui.LockScreenActions(
                            select = { selectedPackage = it },
                            unlock = { unlock(null) },
                            reveal = ::revealDetails,
                            openApp = { pkg -> unlock(packageManager.getLaunchIntentForPackage(pkg)) },
                            camera = ::openCamera,
                        ),
                )
            }
        }
    }

    /** A preview started from Setup or Launcher Settings goes back to that screen, not to Home. */
    override fun finish() {
        val back = intent?.getStringExtra(EXTRA_RETURN_TO)
        if (back != null) {
            intent?.removeExtra(EXTRA_RETURN_TO)
            runCatching {
                startActivity(Intent().setClassName(this, back).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
        super.finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closer) }
        super.onDestroy()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && selectedPackage != null) {
            selectedPackage = null
            true
        } else {
            super.onKeyDown(keyCode, event)
        }

    /** Explicit authentication keeps this cover open so the selected source can be read. */
    private fun revealDetails() {
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return
        revealAfterUnlock = true
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissCancelled() {
                    revealAfterUnlock = false
                }

                override fun onDismissError() {
                    revealAfterUnlock = false
                }
            },
        )
    }

    /** Asks Android to dismiss the real lock screen, then opens [then] (if any) and closes this cover. */
    private fun unlock(then: Intent?) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (isPreview) {
            finish()
            return
        }
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            then?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    then?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    finish()
                }
            },
        )
    }

    /** The secure camera opens over the lock screen without unlocking; if none answers, unlock and open the normal one. */
    private fun openCamera() {
        val secure = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(secure) }.isFailure) {
            unlock(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
    }
}

/** Set on a preview: the class name of the screen to return to when the preview closes. */
const val EXTRA_RETURN_TO = "io.github.aimindseye.tenseven.extra.LOCK_PREVIEW_RETURN_TO"
