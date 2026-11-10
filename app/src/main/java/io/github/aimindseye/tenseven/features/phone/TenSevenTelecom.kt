package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallScreeningService
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.aimindseye.tenseven.D5BbDialogs
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/** Local-only explicit number matching. Never infer that an unknown number is spam. */
object PhoneBlocklist {
    private const val PREFS = "tenseven_phone_blocklist"
    private const val KEY = "blocked_numbers"
    private const val ALLOW = "allowed_numbers"
    private const val SILENCE_UNKNOWN = "silence_unknown"

    fun allowed(context: Context): Set<String> =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(ALLOW, emptySet())
            ?.toSet()
            .orEmpty()

    fun allow(
        context: Context,
        value: String,
    ) {
        val number = normalize(value)
        if (number.isNotEmpty()) {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(
                    ALLOW,
                    allowed(context) + number,
                ).apply()
        }
    }

    fun unallow(
        context: Context,
        value: String,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(ALLOW, allowed(context) - normalize(value))
            .apply()
    }

    fun silenceUnknown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SILENCE_UNKNOWN, false)

    fun setSilenceUnknown(
        context: Context,
        enabled: Boolean,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SILENCE_UNKNOWN, enabled)
            .apply()
    }

    fun normalize(value: String): String {
        val text = PhoneNumberUtils.normalizeNumber(value.trim())
        return if (text.length >= 7) text else ""
    }

    fun blocked(context: Context): Set<String> =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.toSet()
            .orEmpty()

    fun contains(
        context: Context,
        value: String,
    ): Boolean = normalize(value).let { it.isNotEmpty() && it in blocked(context) }

    fun block(
        context: Context,
        value: String,
    ) {
        val number = normalize(value)
        if (number.isEmpty()) return
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, blocked(context) + number)
            .apply()
    }

    fun unblock(
        context: Context,
        value: String,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, blocked(context) - normalize(value))
            .apply()
    }
}

/** Applied only after Android grants ROLE_CALL_SCREENING. Always answers promptly. */
class TenSevenCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val normalized = PhoneBlocklist.normalize(number)
        val explicitlyAllowed = normalized in PhoneBlocklist.allowed(this)
        val shouldBlock = !explicitlyAllowed && PhoneBlocklist.contains(this, number)
        // Unknown-number filtering is explicit opt-in, never a default or a speculative spam verdict.
        val silenceUnknown =
            !shouldBlock && !explicitlyAllowed && PhoneBlocklist.silenceUnknown(this) &&
                checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                !isInContacts(number)
        val result =
            CallResponse
                .Builder()
                .setSilenceCall(silenceUnknown)
                .setDisallowCall(shouldBlock)
                .setRejectCall(shouldBlock)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        respondToCall(callDetails, result)
    }

    private fun isInContacts(number: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver
                .query(
                    uri,
                    arrayOf(android.provider.ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null,
                )?.use { it.moveToFirst() } ==
                true
        } catch (_: Exception) {
            true
        } // Fail open if the provider is inaccessible.
    }
}

/** The system gives call objects only to the current default Phone role holder. */
internal object PhoneCallState {
    @Volatile var call: Call? = null

    @Volatile var service: TenSevenInCallService? = null

    // In-memory only: retain identity for the brief Call ended screen after Telecom removes its Call.
    // Reset for every new Call, including one with a restricted/empty handle, to avoid stale identity.
    @Volatile private var lastNumber: String? = null

    @Volatile private var lastNetworkName: String? = null

    fun remember(
        call: Call,
        newCall: Boolean = false,
    ) {
        val snapshot =
            D6CallIdentityPolicy.reconcile(
                D6CallIdentityPolicy.Snapshot(lastNumber, lastNetworkName),
                call.details.handle?.schemeSpecificPart,
                call.details.callerDisplayName,
                newCall = newCall,
            )
        lastNumber = snapshot.number
        lastNetworkName = snapshot.networkName
    }

    fun number(): String =
        D6CallIdentityPolicy.displayNumber(D6CallIdentityPolicy.publicNumber(call?.details?.handle?.schemeSpecificPart) ?: lastNumber)

    fun isPrivateCaller(): Boolean =
        D6CallIdentityPolicy.publicNumber(call?.details?.handle?.schemeSpecificPart) == null && lastNumber == null

    fun caller(context: Context): String {
        val number = D6CallIdentityPolicy.publicNumber(call?.details?.handle?.schemeSpecificPart) ?: lastNumber
        if (number == null) return D6CallIdentityPolicy.PRIVATE_LABEL
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return D6CallIdentityPolicy.callerName(null, lastNetworkName, number)
        }
        val contactName =
            try {
                val uri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            } catch (_: Exception) {
                null
            }
        return D6CallIdentityPolicy.callerName(contactName, lastNetworkName, number)
    }
}

class TenSevenInCallService : InCallService() {
    private val channel = "tenseven_incoming_call"
    private val notificationId = 7017

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        PhoneCallState.remember(call, newCall = true)
        PhoneCallState.call = call
        PhoneCallState.service = this
        publishCallNotification()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (PhoneCallState.call === call) {
            PhoneCallState.remember(call) // Save caller before Android clears the Call object.
            PhoneCallState.call = null
        }
        getSystemService(NotificationManager::class.java)?.cancel(notificationId)
    }

    override fun onDestroy() {
        if (PhoneCallState.service === this) PhoneCallState.service = null
        super.onDestroy()
    }

    private fun publishCallNotification() {
        val notifications = getSystemService(NotificationManager::class.java) ?: return
        notifications.createNotificationChannel(NotificationChannel(channel, "Phone calls", NotificationManager.IMPORTANCE_HIGH))
        val intent =
            Intent(this, TenSevenInCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder =
            Notification
                .Builder(this, channel)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("TenSeven Phone")
                .setContentText(PhoneCallState.number())
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .setOngoing(true)
        // Android may require notification/full-screen special access. Always keep the in-call service active.
        try {
            notifications.notify(notificationId, builder.build())
        } catch (
            _: SecurityException,
        ) {
            // User may have disabled notifications; role fallback remains Android-managed.
        }
    }
}

/** Full-screen incoming and ongoing call controls. Only Telecom owns the connection. */

/** Approved BB10-style call surface; only Android Telecom owns the actual call. */
class TenSevenInCallActivity : Activity() {
    private val refresh = Handler(Looper.getMainLooper())
    private val ui by lazy { PhonePalette(this) }
    private var lastState = -100
    private val tick =
        object : Runnable {
            override fun run() {
                val state = PhoneCallState.call?.state ?: Call.STATE_DISCONNECTED
                if (lastState != state) {
                    lastState = state
                    render()
                }
                refresh.postDelayed(this, 350L)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    override fun onResume() {
        super.onResume()
        refresh.removeCallbacks(tick)
        refresh.post(tick)
    }

    override fun onPause() {
        refresh.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        refresh.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun shape(
        color: Int,
        outline: Int? = null,
    ) = GradientDrawable().apply {
        cornerRadius = dp(30).toFloat()
        setColor(color)
        if (outline != null) setStroke(dp(1), outline)
    }

    private fun text(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int = ui.fg,
        bold: Boolean = false,
        height: Int = -2,
    ) {
        val line =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        parent.addView(line, LinearLayout.LayoutParams(-1, height).apply { setMargins(dp(7), dp(3), dp(7), dp(3)) })
    }

    private fun button(
        row: LinearLayout,
        title: String,
        color: Int = ui.panel,
        onTap: () -> Unit,
    ) {
        val item =
            Button(this).apply {
                text = title
                isAllCaps = false
                textSize = 14f
                setTextColor(if (color == ui.panel) ui.fg else Color.WHITE)
                background = shape(color, if (color == ui.panel) ui.line else null)
                minimumHeight = 0
                setOnClickListener { onTap() }
            }
        row.addView(
            item,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            },
        )
    }

    private fun row(root: LinearLayout): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(9), 0, dp(9), 0)
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

    private fun statusName(state: Int) =
        when (state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING -> "Dialing"
            Call.STATE_CONNECTING -> "Connecting"
            Call.STATE_ACTIVE -> "Call in progress"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> "Phone call"
        }

    // The default-dialer role is an Android-authorized path to silenceRinger. This API
    // cannot be granted through a third-party MODIFY_PHONE_STATE permission request.
    // Keep the role check and SecurityException handling; suppress only the static warning.
    @SuppressLint("MissingPermission")
    private fun silenceIncomingRinger() {
        val role = getSystemService(RoleManager::class.java)
        if (role?.isRoleHeld(RoleManager.ROLE_DIALER) != true) {
            Toast.makeText(this, "Default Phone role required", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            getSystemService(android.telecom.TelecomManager::class.java)?.silenceRinger()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Default Phone role required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render() {
        TenSevenSafeViewport.darkSystemBars(this)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(ui.bg)
            }
        // No redundant app-name header: give caller image and primary actions the usable viewport.
        val call = PhoneCallState.call
        val identity =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(4))
            }
        root.addView(identity, LinearLayout.LayoutParams(-1, 0, 1f))
        val name = PhoneCallState.caller(this)
        val number = PhoneCallState.number()
        val privateCaller = PhoneCallState.isPrivateCaller()
        val iconRow = row(identity)
        val avatar =
            if (privateCaller) {
                // No invented "PN" initials for an actually withheld number.
                TextView(this).apply {
                    text = "☎"
                    textSize = 36f
                    setTextColor(ui.fg)
                    gravity = Gravity.CENTER
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(ui.panel)
                            setStroke(dp(1), ui.line)
                        }
                    contentDescription = "Private caller"
                }
            } else {
                val contactId = TenSevenContactPhotos.lookupContactId(this, number)
                TenSevenContactPhotos.avatar(this, contactId, name, 92, ui)
            }
        iconRow.addView(avatar, LinearLayout.LayoutParams(dp(92), dp(92)))
        text(identity, name, 29f, ui.fg, true)
        if (!privateCaller && name != number) text(identity, number, 16f, ui.muted)
        text(identity, statusName(call?.state ?: Call.STATE_DISCONNECTED), 17f, ui.muted)
        if (call?.state == Call.STATE_RINGING) text(identity, "Incoming · Mobile", 13f, ui.muted)
        when (call?.state) {
            Call.STATE_RINGING -> {
                val primary = row(root)
                button(primary, "Answer", ui.success) {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    render()
                }
                button(primary, "Decline", ui.danger) {
                    call.reject(false, null)
                    finish()
                }
                val secondary = row(root)
                button(secondary, "Silence") { silenceIncomingRinger() }
                button(secondary, "Reply with text") {
                    if (call.details.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT)) {
                        showReply(call)
                    } else {
                        Toast.makeText(this, "Text reply is unavailable for this call", Toast.LENGTH_SHORT).show()
                    }
                }
                button(secondary, "More") { showIncomingMore(call) }
            }

            Call.STATE_ACTIVE, Call.STATE_HOLDING -> {
                val controls = row(root)
                button(controls, "Keypad") { showTonePad(call) }
                button(controls, "Mute") { PhoneCallState.service?.setMuted(true) }
                button(controls, "Speaker") { PhoneCallState.service?.setAudioRoute(CallAudioState.ROUTE_SPEAKER) }
                val more = row(root)
                button(more, "Unmute") { PhoneCallState.service?.setMuted(false) }
                button(more, "Earpiece") { PhoneCallState.service?.setAudioRoute(CallAudioState.ROUTE_EARPIECE) }
                if (call.state == Call.STATE_HOLDING || call.details.can(Call.Details.CAPABILITY_HOLD)) {
                    button(more, if (call.state == Call.STATE_HOLDING) "Resume" else "Hold") {
                        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
                        render()
                    }
                }
                val end = row(root)
                button(end, "End call", ui.danger) {
                    call.disconnect()
                    finish()
                }
            }

            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                val stop = row(root)
                button(stop, "Cancel call", ui.danger) {
                    call.disconnect()
                    finish()
                }
            }

            else -> {
                val close = row(root)
                button(close, "Close") { finish() }
            }
        }
        root.addView(android.view.View(this), LinearLayout.LayoutParams(-1, dp(12)))
        TenSevenSafeViewport.show(this, root)
    }

    private fun showReply(call: Call) {
        if (!call.details.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT)) {
            Toast.makeText(this, "Text reply unavailable for this call", Toast.LENGTH_SHORT).show()
            return
        }
        val replies = listOf("Can't talk now", "I'll call you back", "In a meeting", "Custom reply…")
        D5BbDialogs.choices(this, "Reply with text", replies) { which ->
            if (which < 3) {
                call.reject(true, replies[which])
                finish()
            } else {
                D5BbDialogs.prompt(this, "Custom text reply", "Send and decline") { reply ->
                    call.reject(true, reply)
                    finish()
                }
            }
        }
    }

    private fun showIncomingMore(call: Call) {
        val actions =
            listOf(
                "Reply with text",
                "Decline (voicemail if available)",
                "Block number & decline",
                "Report spam (local blocklist)",
                "Cancel",
            )
        D5BbDialogs.choices(this, "Incoming call options", actions) { which ->
            when (which) {
                0 -> {
                    showReply(call)
                }

                1 -> {
                    call.reject(false, null)
                    finish()
                }

                2, 3 -> {
                    PhoneBlocklist.block(this, PhoneCallState.number())
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        call.reject(Call.REJECT_REASON_UNWANTED)
                    } else {
                        call.reject(false, null)
                    }
                    Toast.makeText(this, "Saved locally; enable call screening for future calls", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun showTonePad(call: Call) {
        D5BbDialogs.custom(this, "Call keypad") { container, dialog ->
            listOf("123", "456", "789", "*0#").forEach { group ->
                val row = LinearLayout(this)
                container.addView(row, LinearLayout.LayoutParams(-1, dp(50)))
                group.forEach { digit ->
                    val key =
                        Button(this).apply {
                            text = digit.toString()
                            isAllCaps = false
                            setTextColor(Color.WHITE)
                            setBackgroundColor(Color.rgb(17, 29, 43))
                            setOnClickListener {
                                call.playDtmfTone(digit)
                                call.stopDtmfTone()
                            }
                        }
                    row.addView(key, LinearLayout.LayoutParams(0, -1, 1f))
                }
            }
            val done =
                TextView(this).apply {
                    text = "Done"
                    textSize = 16f
                    setTextColor(Color.rgb(0, 166, 237))
                    gravity = Gravity.CENTER
                    setOnClickListener { dialog.dismiss() }
                }
            container.addView(done, LinearLayout.LayoutParams(-1, dp(44)))
        }
    }
}
