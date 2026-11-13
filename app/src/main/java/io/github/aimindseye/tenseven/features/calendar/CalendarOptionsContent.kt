package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.provider.CalendarContract
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import io.github.aimindseye.tenseven.D5BbDialogs
import java.time.format.DateTimeFormatter

/** Activity-bound CalendarOptionsContent renderer; lifecycle and mutable state remain with the activity. */
internal class CalendarOptionsContent(
    private val activity: TenSevenCalendarActivity,
    private val actions: CalendarContentActions,
) {
    fun renderCalendarSelection(body: LinearLayout) {
        if (!activity.views.permission()) {
            activity.views.label(
                body,
                "Allow Calendar access to select your calendars.",
                CALENDAR_CALENDAR_SELECTION_SP_15_F,
            )
            val actions = activity.views.row()
            body.addView(actions)
            activity.views.button(actions, "Allow Calendars") {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_CALENDAR),
                    CALENDAR_CALENDAR_SELECTION_REQUEST_221,
                )
            }
            return
        }
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = activity.views.column()
        scroll.addView(list)
        val picked = activity.selectedIds().toMutableSet()
        renderCalendarAccountGroups(list, picked)
        if (activity.calendars.isEmpty()) {
            activity.views.label(
                list,
                "No calendars available. Check account sync.",
                CALENDAR_CALENDAR_SELECTION_SP_14_F,
                color = activity.views.muted,
            )
        }
        val manage =
            activity.views.label(
                list,
                "Manage synced accounts  ›",
                CALENDAR_CALENDAR_SELECTION_SP_15_F,
                color = activity.views.accent,
            )
        manage.setOnClickListener {
            try {
                activity.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
            } catch (
                _: Exception,
            ) {
                D5BbDialogs.message(activity, "Accounts", "Open Android Settings > Accounts to manage calendar sync")
            }
        }
        // Done is anchored outside the scrolling calendar-account list.
        val done = activity.views.label(body, "Done", CALENDAR_CALENDAR_SELECTION_SP_16_F, true, activity.views.accent)
        done.gravity = Gravity.CENTER
        done.setOnClickListener {
            actions.load()
            actions.returnToMain()
        }
    }

    private fun renderCalendarAccountGroups(
        list: LinearLayout,
        picked: MutableSet<Long>,
    ) {
        val grouped = activity.calendars.groupBy { it.account.ifBlank { "On this device" } }
        grouped.forEach { (account, items) ->
            activity.views.label(list, account, CALENDAR_CALENDAR_SELECTION_SP_14_F, true, activity.views.muted)
            items.forEach { source ->
                val item =
                    activity.views.row().apply {
                        setPadding(
                            activity.views.dp(CALENDAR_LIST_ITEM_GUTTER_DP),
                            0,
                            activity.views.dp(CALENDAR_LIST_ITEM_GUTTER_DP),
                            0,
                        )
                    }
                list.addView(item, LinearLayout.LayoutParams(-1, activity.views.dp(CALENDAR_CALENDAR_ROW_HEIGHT_DP)))
                val toggle =
                    CheckBox(activity).apply {
                        isChecked = source.id in picked
                        buttonTintList = ColorStateList.valueOf(activity.views.accent)
                        contentDescription = "Show ${source.name} from $account"
                        setOnCheckedChangeListener { _, enabled ->
                            if (enabled) picked.add(source.id) else picked.remove(source.id)
                            activity.storeIds(picked)
                        }
                    }
                item.addView(
                    toggle,
                    LinearLayout.LayoutParams(activity.views.dp(CALENDAR_CALENDAR_TOGGLE_WIDTH_DP), -1),
                )
                item.addView(
                    View(activity).apply {
                        background =
                            GradientDrawable().apply {
                                cornerRadius = activity.views.dp(CALENDAR_CONTROL_GAP_DP).toFloat()
                                setColor(source.color)
                            }
                    },
                    LinearLayout.LayoutParams(
                        activity.views.dp(CALENDAR_SELECTION_MARK_DP),
                        activity.views.dp(CALENDAR_SELECTION_MARK_DP),
                    ),
                )
                item.addView(
                    TextView(activity).apply {
                        text = source.name
                        textSize = CALENDAR_CALENDAR_SELECTION_SP_15_F
                        setTextColor(activity.views.fg)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP), 0, 0, 0)
                        setOnClickListener { toggle.isChecked = !toggle.isChecked }
                    },
                    LinearLayout.LayoutParams(0, -1, 1f),
                )
                activity.views.divider(list)
            }
        }
    }

    fun renderSettings(body: LinearLayout) {
        val scroll = ScrollView(activity)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = activity.views.column()
        scroll.addView(list)
        val prefs = activity.getSharedPreferences("tenseven_calendar_view", android.content.Context.MODE_PRIVATE)
        val defaultView = prefs.getString("default_view", "Today") ?: "Today"
        val default =
            activity.views.label(
                list,
                "Default view                         $defaultView   ›",
                CALENDAR_SETTINGS_SP_15_F,
            )
        default.setOnClickListener {
            D5BbDialogs.choices(activity, "Default view", listOf("Today", "Week", "Month", "Day")) { index ->
                prefs.edit { putString("default_view", listOf("Today", "Week", "Month", "Day")[index]) }
                actions.render()
            }
        }
        activity.views.divider(list)
        val week =
            activity.views.label(
                list,
                "Week starts                  ${if (activity.mondayStart()) "Monday" else "Sunday"}   ›",
                CALENDAR_SETTINGS_SP_15_F,
            )
        week.setOnClickListener {
            prefs.edit { putBoolean("monday_start", !activity.mondayStart()) }
            actions.render()
        }
        activity.views.divider(list)
        val manage = activity.views.label(list, "Calendars & accounts   ›", CALENDAR_SETTINGS_SP_15_F)
        manage.setOnClickListener { actions.selectMode("Calendars") }
        activity.views.divider(list)
        activity.views.label(list, "Theme                               BlackBerry dark", CALENDAR_SETTINGS_SP_15_F)
        activity.views.divider(list)
        activity.views.label(
            list,
            "Calendar colors indicate the selected source; navigation and controls stay in BB10 blue, white and gray.",
            CALENDAR_SETTINGS_RATIO_12_F,
            color = activity.views.muted,
        )
    }

    fun renderEventComposer(body: LinearLayout) {
        val scroll = ScrollView(activity)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = activity.views.column()
        scroll.addView(form)
        val title = createEventTitleField(form)
        activity.views.divider(form)
        activity.views.label(
            form,
            "${activity.selected.format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy"))} · 10:00 AM",
            CALENDAR_EDITOR_SP_15_F,
        )
        val source = activity.calendars.firstOrNull { it.id in activity.selectedIds() }
        activity.views.label(form, "Calendar: ${source?.name ?: "Choose in Android Calendar"}", CALENDAR_EDITOR_SP_15_F)
        val (location, notes) = createEventAdditionalFields(form)
        // The Save handoff is a fixed action, never below a long event form.
        val controls = activity.views.row()
        body.addView(controls, LinearLayout.LayoutParams(-1, activity.views.dp(CALENDAR_FORM_ACTION_HEIGHT_DP)))
        activity.views.button(controls, "Continue to Android Calendar") {
            val intent =
                Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, title.text.toString().trim())
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, location.text.toString().trim())
                    .putExtra(CalendarContract.Events.DESCRIPTION, notes.text.toString().trim())
                    .putExtra(
                        CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                        activity.dateMs(activity.selected) + CALENDAR_EDITOR_VALUE_10 * CALENDAR_EDITOR_TIME_3600_000_L,
                    )
            source?.let { intent.putExtra(CalendarContract.Events.CALENDAR_ID, it.id) }
            try {
                activity.startActivity(intent)
            } catch (
                _: Exception,
            ) {
                D5BbDialogs.message(
                    activity,
                    "Calendar editor",
                    "No Android Calendar editor is available to save this event",
                )
            }
        }
        activity.views.label(
            form,
            "Android Calendar confirms and saves the event; TenSeven currently has read-only calendar access.",
            CALENDAR_EDITOR_RATIO_12_F,
            color = activity.views.muted,
        )
    }

    private fun createEventTitleField(form: LinearLayout): EditText {
        val title =
            EditText(activity).apply {
                hint = "Event title"
                setSingleLine(true)
                textSize = CALENDAR_EDITOR_SP_17_F
                setTextColor(activity.views.fg)
                setHintTextColor(activity.views.muted)
                setPadding(
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                )
            }
        form.addView(title, LinearLayout.LayoutParams(-1, activity.views.dp(CALENDAR_FORM_SINGLE_LINE_HEIGHT_DP)))
        return title
    }

    private fun createEventAdditionalFields(form: LinearLayout): Pair<EditText, EditText> {
        val location =
            EditText(activity).apply {
                hint = "Location"
                setSingleLine(true)
                setTextColor(activity.views.fg)
                setHintTextColor(activity.views.muted)
                setPadding(
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                )
            }
        form.addView(location, LinearLayout.LayoutParams(-1, activity.views.dp(CALENDAR_FORM_SINGLE_LINE_HEIGHT_DP)))
        val notes =
            EditText(activity).apply {
                hint = "Notes (optional)"
                setTextColor(activity.views.fg)
                setHintTextColor(activity.views.muted)
                minLines = 2
                maxLines = CALENDAR_EDITOR_VALUE_3
                setPadding(
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                )
            }
        form.addView(notes, LinearLayout.LayoutParams(-1, activity.views.dp(CALENDAR_FORM_NOTES_HEIGHT_DP)))
        return location to notes
    }
}
