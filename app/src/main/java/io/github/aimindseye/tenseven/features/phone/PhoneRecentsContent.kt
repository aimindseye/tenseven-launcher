package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.graphics.Color
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.aimindseye.tenseven.features.phone.TenSevenPhoneActivity.Screen

/** Activity-bound PhoneRecentsContent renderer; lifecycle and mutable state remain with the activity. */
internal class PhoneRecentsContent(
    private val activity: TenSevenPhoneActivity,
    private val onOpenContact: (Long) -> Unit,
    private val onVoicemail: () -> Unit,
    private val onDefaultPhoneSetup: () -> Unit,
) {
    fun recents(frame: FrameLayout) {
        val root = activity.views.column()
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        renderRecentFilters(root)
        val listArea = FrameLayout(activity)
        root.addView(listArea, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        listArea.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val list =
            activity.views.column().apply {
                setPadding(
                    0,
                    activity.views.px(PHONE_EXTRA_TIGHT_INSET_DP),
                    0,
                    activity.views.px(PHONE_RECENTS_BOTTOM_INSET_DP),
                )
            }
        scroll.addView(list)
        renderRecentHistory(list)
        renderVoicemailRow(list)
        activity.views.floating(
            listArea,
            "⠿",
            "Open dialpad",
            FloatingPosition(Gravity.END or Gravity.BOTTOM, PHONE_RECENTS_VALUE_15, PHONE_RECENTS_VALUE_16),
        ) {
            activity.screen = Screen.DIAL
            activity.draw()
        }
    }

    private fun renderRecentFilters(root: LinearLayout) {
        val filters = activity.views.row()
        root.addView(filters, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)))
        listOf("All", "Missed", "Incoming", "Outgoing").forEach { name ->
            val selected = activity.callFilter == name
            val control =
                activity.views
                    .clickLabel(name, "Show $name calls", PHONE_CAPTION_TEXT_SP) {
                        activity.callFilter = name
                        activity.draw()
                    }.apply { setTextColor(if (selected) Color.WHITE else activity.views.colors.muted) }
            val group = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            group.addView(control, LinearLayout.LayoutParams(-1, 0, 1f))
            group.addView(
                View(activity).apply {
                    setBackgroundColor(if (selected) activity.views.colors.accent else activity.views.colors.bg)
                },
                LinearLayout.LayoutParams(-1, activity.views.px(PHONE_MICRO_INSET_DP)),
            )
            filters.addView(group, LinearLayout.LayoutParams(0, -1, 1f))
        }
        activity.views.rule(root)
    }

    private fun renderRecentHistory(list: LinearLayout) {
        if (!activity.isGranted(Manifest.permission.READ_CALL_LOG)) {
            activity.views.notice(
                list,
                "Call history needs Android call-log access. Selecting TenSeven as default Phone may be required.",
                "Open default Phone setup",
            ) { onDefaultPhoneSetup() }
            activity.views.notice(list, "Allow call history", "Request call-log permission") {
                activity.requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), PHONE_REQUEST_CALL_LOG)
            }
        } else {
            val people =
                if (activity.isGranted(
                        Manifest.permission.READ_CONTACTS,
                    )
                ) {
                    activity.data.people()
                } else {
                    emptyList()
                }
            val byNumber = people.associateBy { PhoneBlocklist.normalize(it.number) }
            val favorites = people.filter { it.favorite }.map { PhoneBlocklist.normalize(it.number) }.toSet()
            val entries =
                activity.data.calls().filter {
                    when (activity.callFilter) {
                        "Missed" -> it.kind == CallLog.Calls.MISSED_TYPE
                        "Incoming" -> it.kind == CallLog.Calls.INCOMING_TYPE
                        "Outgoing" -> it.kind == CallLog.Calls.OUTGOING_TYPE
                        "Favorites" -> PhoneBlocklist.normalize(it.number) in favorites
                        else -> true
                    }
                }
            if (entries.isEmpty()) activity.views.notice(list, "No ${activity.callFilter.lowercase()} calls")
            entries.forEach { entry ->
                val person = byNumber[PhoneBlocklist.normalize(entry.number)]
                renderRecentCallRow(list, entry, person)
            }
        }
    }

    private fun renderRecentCallRow(
        list: LinearLayout,
        entry: TenSevenPhoneData.Call,
        person: TenSevenPhoneData.Person?,
    ) {
        val kind = recentCallKind(entry.kind)
        val item =
            activity.views.row().apply {
                setPadding(
                    activity.views.px(PHONE_LIST_CONTROL_INSET_DP),
                    activity.views.px(PHONE_SMALL_INSET_DP),
                    activity.views.px(PHONE_SECTION_INSET_DP),
                    activity.views.px(PHONE_SMALL_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "${person?.name ?: entry.number}, $kind, ${entry.whenText}"
                setOnClickListener {
                    if (person != null) {
                        onOpenContact(person.id)
                    } else {
                        activity.dialNumber = entry.number
                        activity.screen = Screen.DIAL
                        activity.draw()
                    }
                }
            }
        list.addView(item, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_RECENT_CALL_ROW_HEIGHT_DP)))
        val glyph = recentCallKindGlyph(kind)
        item.addView(glyph, LinearLayout.LayoutParams(activity.views.px(PHONE_CALL_TYPE_ICON_WIDTH_DP), -1))
        val summary = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        item.addView(summary, LinearLayout.LayoutParams(0, -PHONE_RECENTS_DP_2, 1f))
        summary.addView(
            activity.views.label(
                person?.name ?: entry.number.ifBlank { "Private number" },
                PHONE_BODY_TEXT_SP,
                if (kind == "Missed") activity.views.colors.danger else activity.views.colors.fg,
                bold = true,
            ),
        )
        summary.addView(activity.views.label(kind, PHONE_CAPTION_TEXT_SP, activity.views.colors.muted))
        item.addView(
            activity.views.label(entry.whenText, PHONE_RECENTS_SP_11_F, activity.views.colors.muted).apply {
                gravity =
                    Gravity.CENTER
            },
        )
        item.addView(
            activity.views.label("›", PHONE_RECENTS_SP_21_F, activity.views.colors.muted).apply {
                gravity =
                    Gravity.CENTER
            },
            LinearLayout.LayoutParams(activity.views.px(PHONE_CALLOUT_TOP_INSET_DP), -1),
        )
        activity.views.rule(list)
    }

    private fun recentCallKind(kind: Int): String =
        when (kind) {
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            else -> "Call"
        }

    private fun recentCallKindGlyph(kind: String): TextView {
        val glyph =
            activity.views
                .label(
                    when (kind) {
                        "Missed" -> "✕"
                        "Outgoing" -> "↗"
                        "Blocked" -> "⊘"
                        else -> "↙"
                    },
                    PHONE_RECENTS_RATIO_25_F,
                    if (kind == "Missed") activity.views.colors.danger else activity.views.colors.accent,
                ).apply { gravity = Gravity.CENTER }
        return glyph
    }

    private fun renderVoicemailRow(list: LinearLayout) {
        // Voicemail must be visible in Recents even if call history is empty.
        val vm =
            activity.views.row().apply {
                setPadding(
                    activity.views.px(PHONE_DETAIL_CONTENT_INSET_DP),
                    activity.views.px(PHONE_LIST_CONTROL_INSET_DP),
                    activity.views.px(PHONE_AUXILIARY_INSET_DP),
                    activity.views.px(PHONE_LIST_CONTROL_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "Open voicemail"
                setOnClickListener { onVoicemail() }
            }
        list.addView(vm, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_VOICEMAIL_ACTION_HEIGHT_DP)))
        vm.addView(
            activity.views.label("☏", PHONE_RECENTS_SP_24_F).apply { gravity = Gravity.CENTER },
            LinearLayout
                .LayoutParams(activity.views.px(PHONE_CONTACT_AVATAR_EDGE_DP), -PHONE_RECENTS_DP_1),
        )
        val vtext = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        vm.addView(vtext, LinearLayout.LayoutParams(0, -PHONE_RECENTS_DP_2, 1f))
        vtext.addView(activity.views.label("Voicemail", PHONE_BODY_TEXT_SP, bold = true))
        vtext.addView(
            activity.views.label("Open carrier voicemail", PHONE_CAPTION_TEXT_SP, activity.views.colors.muted),
        )
        vm.addView(activity.views.label("›", PHONE_SYMBOL_TEXT_SP, activity.views.colors.muted))
        activity.views.rule(list)
    }
}
