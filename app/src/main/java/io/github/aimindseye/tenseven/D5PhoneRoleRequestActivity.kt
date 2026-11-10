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
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/** Explicit, user-initiated system Phone-role request, separate from Setup's Compose navigation. */
class D5PhoneRoleRequestActivity : Activity() {
    private var requestStarted = false
    private val roleRequest = 451

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

    private fun render() {
        val bg = Color.rgb(7, 12, 19)
        val fg = Color.WHITE
        val muted = Color.rgb(178, 196, 210)
        val blue = Color.rgb(0, 167, 240)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(bg)
                setPadding(dp(20), dp(22), dp(20), dp(18))
            }

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
                    setPadding(0, dp(8), 0, dp(8))
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
                    textSize = 16f
                    setTextColor(blue)
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(14), dp(8), dp(14))
                    setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(-1, dp(57)),
            )
        }
        text("Default Phone app", 22f, fg, true)
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
                text("TenSeven Phone is the current default Phone app.", 16f, fg)
            }

            !enabled -> {
                text("Enable TenSeven Phone in Apps & Features before selecting it as default.", 16f, muted)
            }

            else -> {
                text(
                    "Android lists the bundled Phone component as 'TenSeven Launcher'. Selecting that package lets TenSeven Phone handle calls.",
                    16f,
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
