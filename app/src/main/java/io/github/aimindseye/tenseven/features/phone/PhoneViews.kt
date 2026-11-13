package io.github.aimindseye.tenseven.features.phone

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

internal data class FloatingPosition(
    val alignment: Int,
    val right: Int,
    val bottom: Int,
)

/** Shared Phone view construction with the original Activity context and palette. */
internal class PhoneViews(
    private val activity: TenSevenPhoneActivity,
) {
    val colors by lazy { PhonePalette(activity) }

    fun px(dp: Int) = (dp * activity.resources.displayMetrics.density).toInt()

    fun column() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.bg)
        }

    fun row() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun label(
        value: String,
        size: Float = PHONE_BODY_TEXT_SP,
        color: Int = colors.fg,
        bold: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            maxLines = 2
        }

    fun rule(parent: LinearLayout) =
        parent.addView(
            View(activity).apply { setBackgroundColor(colors.line) },
            LinearLayout.LayoutParams(
                -1,
                px(PHONE_HAIRLINE_DP),
            ),
        )

    fun round(
        color: Int,
        radius: Int = 12,
        stroke: Boolean = false,
    ) = GradientDrawable().apply {
        cornerRadius = px(radius).toFloat()
        setColor(color)
        if (stroke) setStroke(px(PHONE_HAIRLINE_DP), this@PhoneViews.colors.line)
    }

    fun circular(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    fun clickLabel(
        text: String,
        description: String = text,
        size: Float = PHONE_BODY_TEXT_SP,
        action: () -> Unit,
    ): TextView =
        label(text, size).apply {
            gravity = Gravity.CENTER
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    fun notice(
        body: LinearLayout,
        message: String,
        action: String? = null,
        onClick: (() -> Unit)? = null,
    ) {
        val note = label(message, PHONE_SECONDARY_TEXT_SP, colors.muted)
        note.setPadding(
            px(PHONE_CALLOUT_CONTENT_INSET_DP),
            px(PHONE_CALLOUT_TOP_INSET_DP),
            px(PHONE_CALLOUT_CONTENT_INSET_DP),
            px(PHONE_DETAIL_CONTENT_INSET_DP),
        )
        body.addView(note)
        if (action != null && onClick != null) {
            val go =
                clickLabel(action, action, PHONE_BODY_TEXT_SP, onClick).apply {
                    setTextColor(colors.accent)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        px(PHONE_CALLOUT_CONTENT_INSET_DP),
                        px(PHONE_SMALL_INSET_DP),
                        px(PHONE_CALLOUT_CONTENT_INSET_DP),
                        px(PHONE_PAGE_CONTENT_INSET_DP),
                    )
                }
            body.addView(go)
        }
    }

    fun floating(
        parent: FrameLayout,
        glyph: String,
        description: String,
        position: FloatingPosition,
        action: () -> Unit,
    ) {
        val control =
            clickLabel(glyph, description, PHONE_FLOATING_RATIO_28_F, action).apply {
                background = circular(colors.accent)
                setTextColor(Color.WHITE)
                elevation = px(PHONE_TIGHT_INSET_DP).toFloat()
            }
        parent.addView(
            control,
            FrameLayout
                .LayoutParams(
                    px(PHONE_FLOATING_ACTION_EDGE_DP),
                    px(PHONE_FLOATING_ACTION_EDGE_DP),
                    position.alignment,
                ).apply {
                    rightMargin = px(position.right)
                    bottomMargin = px(position.bottom)
                },
        )
    }
}
