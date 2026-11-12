package io.github.aimindseye.tenseven

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/** Explicit, user-initiated system Phone-role request, separate from Setup's Compose navigation. */
class D5PhoneRoleRequestActivity : Activity() {
    // Stable system role request code and approved default-Phone setup metrics.
    private companion object {
        const val PHONE_ROLE_REQUEST_CODE = 451
        const val TEXT_VERTICAL_PADDING_DP = 8
        const val ACTION_VERTICAL_PADDING_DP = 14
        const val CONTENT_BOTTOM_PADDING_DP = 18
        const val CONTENT_HORIZONTAL_PADDING_DP = 20
        const val CONTENT_TOP_PADDING_DP = 22
        const val ACTION_ROW_HEIGHT_DP = 57
        const val ACTION_TEXT_SIZE_SP = 16f
        const val TITLE_TEXT_SIZE_SP = 22f
        const val BODY_TEXT_SIZE_SP = 16f
    }

    private var requestStarted = false
    private val roleRequest = PHONE_ROLE_REQUEST_CODE

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun roleManager() = getSystemService(RoleManager::class.java)

    private fun holdsRole() = roleManager()?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStarted = savedInstanceState?.getBoolean("requested") ?: false
        render()
        if (!requestStarted && !holdsRole()) {
            window.decorView.post { requestPhoneRole() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested", requestStarted)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Deprecated("Legacy Activity result used for system ROLE_DIALER confirmation")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == roleRequest) render()
    }

    private fun requestPhoneRole() {
        val roles = roleManager()
        val component =
            ComponentName(
                packageName,
                "io.github.aimindseye.tenseven.features.phone.TenSevenPhoneActivity",
            )
        val enabled =
            packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (!enabled || roles?.isRoleAvailable(RoleManager.ROLE_DIALER) != true) {
            render()
            return
        }
        if (holdsRole()) {
            render()
            return
        }
        requestStarted = true
        try {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER), roleRequest)
        } catch (_: Exception) {
            render() // Fall back to Android Default Apps rather than silently closing Setup.
        }
    }

    private fun createPhoneRoleRoot(background: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(
                dp(CONTENT_HORIZONTAL_PADDING_DP),
                dp(CONTENT_TOP_PADDING_DP),
                dp(CONTENT_HORIZONTAL_PADDING_DP),
                dp(CONTENT_BOTTOM_PADDING_DP),
            )
        }

    private fun render() {
        val bg = "#070C13".toColorInt()
        val fg = Color.WHITE
        val muted = "#B2C4D2".toColorInt()
        val blue = "#00A7F0".toColorInt()
        val root = createPhoneRoleRoot(bg)

        fun text(
            value: String,
            size: Float,
            color: Int,
            bold: Boolean = false,
        ) {
            root.addView(
                TextView(this).apply {
                    this.text = value
                    textSize = size
                    setTextColor(color)
                    if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(TEXT_VERTICAL_PADDING_DP), 0, dp(TEXT_VERTICAL_PADDING_DP))
                },
            )
        }

        fun action(
            value: String,
            onClick: () -> Unit,
        ) {
            root.addView(
                TextView(this).apply {
                    text = value
                    textSize = ACTION_TEXT_SIZE_SP
                    setTextColor(blue)
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(TEXT_VERTICAL_PADDING_DP),
                        dp(ACTION_VERTICAL_PADDING_DP),
                        dp(TEXT_VERTICAL_PADDING_DP),
                        dp(ACTION_VERTICAL_PADDING_DP),
                    )
                    setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(-1, dp(ACTION_ROW_HEIGHT_DP)),
            )
        }
        text("Default Phone app", TITLE_TEXT_SIZE_SP, fg, true)
        val component =
            ComponentName(
                packageName,
                "io.github.aimindseye.tenseven.features.phone.TenSevenPhoneActivity",
            )
        val enabled =
            packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        when {
            holdsRole() -> {
                text("TenSeven Phone is the current default Phone app.", BODY_TEXT_SIZE_SP, fg)
            }

            !enabled -> {
                text(
                    "Enable TenSeven Phone in Apps & Features before selecting it as default.",
                    BODY_TEXT_SIZE_SP,
                    muted,
                )
            }

            else -> {
                text(
                    "Android lists the bundled Phone component as 'TenSeven Launcher'. Selecting that " +
                        "package lets TenSeven Phone handle calls.",
                    BODY_TEXT_SIZE_SP,
                    muted,
                )
            }
        }
        if (enabled && !holdsRole()) {
            action("Choose TenSeven as default Phone") { requestPhoneRole() }
            action("Open Android Default Apps") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (
                    _: Exception,
                ) {
                    D5BbDialogs.message(this, "Default apps", "Open Android Settings > Apps > Default apps > Phone app")
                }
            }
        }
        action("Back to TenSeven Settings") { finish() }
        TenSevenSafeViewport.show(this, root)
    }
}
