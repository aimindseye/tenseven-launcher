package io.github.aimindseye.tenseven.features.calendar

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import io.github.aimindseye.tenseven.calendar.R

/** Transient views belonging to one Calendar account group; no persisted or process-global disclosure state. */
internal class CalendarSelectionName(
    val view: View,
    private val publicName: TextView,
    private val privateName: TextView,
    private val selection: CheckBox,
    private val hidesAccount: Boolean,
) {
    fun reveal(show: Boolean) {
        if (!hidesAccount) return
        publicName.visibility = if (show) View.GONE else View.VISIBLE
        privateName.visibility = if (show) View.VISIBLE else View.GONE
        selection.contentDescription = "Show ${if (show) privateName.text else publicName.text}"
    }
}

internal fun calendarSelectionName(
    activity: TenSevenCalendarActivity,
    source: D5R2Calendar,
    selection: CheckBox,
    parent: ViewGroup,
): CalendarSelectionName {
    val root = activity.layoutInflater.inflate(R.layout.calendar_selection_name, parent, false)
    val publicName = root.findViewById<TextView>(R.id.calendar_name_public)
    val privateName = root.findViewById<TextView>(R.id.calendar_name_private)
    val hidesAccount = source.account.isNotBlank() && source.name.contains(source.account, ignoreCase = true)
    publicName.text = if (hidesAccount) activity.getString(R.string.calendar_private_name) else source.name
    // This XML-backed field is GONE before binding and is never independently saved/restored.
    privateName.text = source.name
    listOf(publicName, privateName).forEach { name ->
        name.textSize = CALENDAR_CALENDAR_SELECTION_SP_15_F
        name.setTextColor(activity.views.fg)
        name.gravity = Gravity.CENTER_VERTICAL
        name.setPadding(activity.views.dp(CALENDAR_FORM_AND_LABEL_INSET_DP), 0, 0, 0)
        name.setOnClickListener { selection.isChecked = !selection.isChecked }
    }
    selection.contentDescription = "Show ${publicName.text}"
    return CalendarSelectionName(root, publicName, privateName, selection, hidesAccount)
}
