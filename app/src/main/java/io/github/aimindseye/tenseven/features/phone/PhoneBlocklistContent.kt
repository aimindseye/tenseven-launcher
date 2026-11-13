package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.role.RoleManager
import android.provider.CallLog
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.aimindseye.tenseven.D5BbDialogs

/** Activity-bound PhoneBlocklistContent renderer; lifecycle and mutable state remain with the activity. */
internal class PhoneBlocklistContent(
    private val activity: TenSevenPhoneActivity,
) {
    fun blocklist(frame: FrameLayout) {
        val scroll = ScrollView(activity)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val body =
            activity.views.column().apply {
                setPadding(
                    activity.views.px(PHONE_LIST_CONTENT_INSET_DP),
                    activity.views.px(PHONE_SECTION_INSET_DP),
                    activity.views.px(PHONE_LIST_CONTENT_INSET_DP),
                    activity.views.px(PHONE_CALLOUT_CONTENT_INSET_DP),
                )
            }
        scroll.addView(body)
        activity.views.notice(
            body,
            "Numbers you explicitly block are screened when TenSeven holds " +
                "the optional Android screening role.",
        )
        blockNumberEntry(body)
        renderBlockedNumberRows(body)
        renderCallScreeningSetting(body)
        allowNumberEntry(body)
        renderAllowedNumberRows(body)
        renderUnknownCallerSilencer(body)
        renderBlockedCallHistory(body)
        activity.views.notice(
            body,
            "Unknown calls are only silenced when screening is enabled and Contacts access is allowed.",
        )
    }

    private fun blockNumberEntry(body: LinearLayout) {
        val input =
            EditText(activity).apply {
                hint = "Number with country code"
                inputType = InputType.TYPE_CLASS_PHONE
                setTextColor(activity.views.colors.fg)
                setHintTextColor(activity.views.colors.muted)
            }
        body.addView(input)
        body.addView(
            activity.views
                .clickLabel("Block number", "Block number") {
                    val value = PhoneBlocklist.normalize(input.text.toString())
                    if (value.length < PHONE_BLOCKLIST_VALUE_7) {
                        activity.toast("Enter a full number")
                    } else {
                        PhoneBlocklist.block(activity, value)
                        activity.draw()
                    }
                }.apply { setTextColor(activity.views.colors.accent) },
            LinearLayout.LayoutParams(-1, activity.views.px(PHONE_MENU_ROW_HEIGHT_DP)),
        )
    }

    private fun renderBlockedNumberRows(body: LinearLayout) {
        PhoneBlocklist.blocked(activity).sorted().forEach { number ->
            body.addView(
                activity.views.clickLabel("$number     Unblock", "Unblock $number") {
                    D5BbDialogs.confirm(activity, "Unblock number", "Allow calls from $number?", "Unblock") {
                        PhoneBlocklist.unblock(activity, number)
                        activity.draw()
                    }
                },
                LinearLayout.LayoutParams(-1, activity.views.px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP)),
            )
            activity.views.rule(body)
        }
    }

    private fun renderCallScreeningSetting(body: LinearLayout) {
        body.addView(
            activity.views
                .clickLabel("Allowed numbers / screening settings", "Screening settings") {
                    val roles = activity.getSystemService(RoleManager::class.java)
                    if (roles?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true &&
                        !roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                    ) {
                        activity.safeStart(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    } else {
                        D5BbDialogs.message(
                            activity,
                            "Call screening",
                            "Review Android Default Apps to change call screening.",
                        )
                    }
                }.apply { setTextColor(activity.views.colors.accent) },
            LinearLayout.LayoutParams(-1, activity.views.px(PHONE_CONTACT_OR_MENU_ROW_DP)),
        )
    }

    private fun allowNumberEntry(body: LinearLayout) {
        // Preserve existing explicit allowlist without auto-blocking unknown callers.
        val allow =
            EditText(activity).apply {
                hint = "Number to allow"
                inputType = InputType.TYPE_CLASS_PHONE
                setTextColor(activity.views.colors.fg)
                setHintTextColor(activity.views.colors.muted)
            }
        body.addView(allow)
        body.addView(
            activity.views.clickLabel("Allow number", "Allow number") {
                val number = PhoneBlocklist.normalize(allow.text.toString())
                if (number.length < PHONE_BLOCKLIST_VALUE_7) {
                    activity.toast("Enter a full number")
                } else {
                    PhoneBlocklist.allow(activity, number)
                    activity.draw()
                }
            },
            LinearLayout.LayoutParams(-1, activity.views.px(PHONE_MENU_ACTION_HEIGHT_DP)),
        )
    }

    private fun renderAllowedNumberRows(body: LinearLayout) {
        PhoneBlocklist.allowed(activity).sorted().forEach { number ->
            body.addView(
                activity.views.clickLabel("$number     Remove", "Remove allowed $number") {
                    D5BbDialogs.confirm(activity, "Remove exception", "Remove allowed number $number?", "Remove") {
                        PhoneBlocklist.unallow(activity, number)
                        activity.draw()
                    }
                },
                LinearLayout.LayoutParams(-1, activity.views.px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP)),
            )
        }
    }

    private fun renderUnknownCallerSilencer(body: LinearLayout) {
        body.addView(
            activity.views.clickLabel(
                "Silence unknown: ${if (PhoneBlocklist.silenceUnknown(activity)) "ON" else "OFF"}",
            ) {
                PhoneBlocklist.setSilenceUnknown(activity, !PhoneBlocklist.silenceUnknown(activity))
                activity.draw()
            },
            LinearLayout.LayoutParams(-1, activity.views.px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)),
        )
    }

    private fun renderBlockedCallHistory(body: LinearLayout) {
        body.addView(
            activity.views.clickLabel("Blocked call history", "View blocked calls") {
                if (!activity.isGranted(Manifest.permission.READ_CALL_LOG)) {
                    activity.requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), PHONE_REQUEST_CALL_LOG)
                } else {
                    val recent =
                        activity.data
                            .calls()
                            .filter { it.kind == CallLog.Calls.BLOCKED_TYPE }
                            .take(PHONE_BLOCKLIST_MAX_25)
                    D5BbDialogs.message(
                        activity,
                        "Blocked calls",
                        if (recent.isEmpty()) {
                            "No blocked calls in Android call history"
                        } else {
                            recent.joinToString("\n") { "${it.number} · ${it.whenText}" }
                        },
                    )
                }
            },
            LinearLayout.LayoutParams(-1, activity.views.px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)),
        )
    }
}
