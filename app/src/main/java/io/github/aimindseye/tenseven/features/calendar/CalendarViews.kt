package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/** Shared Calendar view primitives; retains the approved Activity context and geometry. */
internal class CalendarViews(
    private val activity: TenSevenCalendarActivity,
) {
    val bg get() = Color.rgb(3, 5, 8)
    val fg get() = Color.rgb(243, 247, 250)
    val muted get() = Color.rgb(160, 173, 186)
    val accent get() = Color.rgb(0, 151, 235)
    private val panel get() = Color.rgb(18, 22, 29)
    private val border get() = Color.rgb(49, 56, 64)

    fun permission() =
        activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager
                .PERMISSION_GRANTED

    fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()

    fun column() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

    fun row() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun label(
        parent: LinearLayout,
        value: String,
        size: Float = 16f,
        bold: Boolean = false,
        color: Int = fg,
    ): TextView {
        val text =
            TextView(activity).apply {
                this.text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(
                    dp(CALENDAR_FORM_AND_LABEL_INSET_DP),
                    dp(CALENDAR_LABEL_VERTICAL_INSET_DP),
                    dp(CALENDAR_LABEL_END_INSET_DP),
                    dp(CALENDAR_LABEL_VERTICAL_INSET_DP),
                )
            }
        parent.addView(text)
        return text
    }

    fun pill(
        value: String,
        active: Boolean = false,
        size: Float = 14f,
        onClick: () -> Unit,
    ): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(if (active) Color.WHITE else fg)
            gravity = Gravity.CENTER
            setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            background =
                GradientDrawable().apply {
                    cornerRadius = dp(CALENDAR_PILL_RADIUS_DP).toFloat()
                    setColor(if (active) accent else panel)
                    setStroke(dp(CALENDAR_HAIRLINE_DP), if (active) accent else border)
                }
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }

    fun button(
        parent: LinearLayout,
        name: String,
        action: () -> Unit,
    ) {
        val view = pill(name, onClick = action)
        parent.addView(
            view,
            LinearLayout.LayoutParams(0, dp(CALENDAR_CONTROL_HEIGHT_DP), 1f).apply {
                setMargins(
                    dp(CALENDAR_CONTROL_GAP_DP),
                    dp(CALENDAR_CONTROL_GAP_DP),
                    dp(CALENDAR_CONTROL_GAP_DP),
                    dp(CALENDAR_CONTROL_GAP_DP),
                )
            },
        )
    }

    fun divider(parent: LinearLayout) {
        parent.addView(
            View(activity).apply { setBackgroundColor(border) },
            LinearLayout.LayoutParams(
                -1,
                dp(CALENDAR_HAIRLINE_DP),
            ),
        )
    }

    fun fit() = TenSevenSafeViewport.fit(activity)
}
