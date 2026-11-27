package io.github.aimindseye.tenseven

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val BB_DIALOGS_CHOICES_DP_1 = 1
private const val BB_DIALOGS_CHOICES_DP_2 = 2
private const val BB_DIALOGS_MULTI_CHOICE_DP_1 = 1
private const val BB_DIALOGS_MULTI_CHOICE_DP_2 = 2

/** BB10-inspired local dialogs: no platform-white alert sheets on light-mode devices. */
object D5BbDialogs {
    // Approved BB10 dialog spacing, font sizes and sizing semantics.
    private const val DIVIDER_THICKNESS_DP = 1
    private const val CHECKBOX_PADDING_VERTICAL_DP = 6
    private const val COMPACT_PADDING_DP = 8
    private const val STANDARD_PADDING_VERTICAL_DP = 9
    private const val INPUT_PADDING_HORIZONTAL_DP = 12
    private const val LABEL_PADDING_HORIZONTAL_DP = 13
    private const val CORNER_RADIUS_DP = 14
    private const val CHOICE_ROW_MIN_HEIGHT_DP = 46
    private const val ACTION_ROW_HEIGHT_DP = 48
    private const val REPLY_INPUT_HEIGHT_DP = 85
    private const val MULTICHOICE_LIST_HEIGHT_DP = 270
    private const val TITLE_TEXT_SIZE_SP = 19f
    private const val BODY_TEXT_SIZE_SP = 15f
    private const val REPLY_TEXT_SIZE_SP = 16f
    private const val CHOICE_TEXT_SIZE_SP = 14f
    private const val REPLY_MAX_LINES = 3
    private const val DIALOG_WIDTH_FRACTION = 0.92f
    private const val ALTERNATING_ROW_PERIOD = 2
    private val background = "#070C13".toColorInt()
    private val panel = "#131E2B".toColorInt()
    private val textColor = Color.WHITE
    private val secondary = "#B8C9D7".toColorInt()
    private val accent = "#00A6ED".toColorInt()
    private val edge = "#354B5C".toColorInt()

    private fun dp(
        activity: Activity,
        n: Int,
    ) = (activity.resources.displayMetrics.density * n).toInt()

    private fun shape(activity: Activity) =
        GradientDrawable().apply {
            setColor(background)
            cornerRadius = dp(activity, CORNER_RADIUS_DP).toFloat()
            setStroke(dp(activity, DIVIDER_THICKNESS_DP), edge)
        }

    private fun label(
        activity: Activity,
        text: String,
        title: Boolean = false,
    ): TextView =
        TextView(activity).apply {
            this.text = text
            textSize = if (title) TITLE_TEXT_SIZE_SP else BODY_TEXT_SIZE_SP
            setTextColor(if (title) textColor else secondary)
            if (title) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(activity, LABEL_PADDING_HORIZONTAL_DP),
                dp(activity, STANDARD_PADDING_VERTICAL_DP),
                dp(activity, LABEL_PADDING_HORIZONTAL_DP),
                dp(activity, STANDARD_PADDING_VERTICAL_DP),
            )
        }

    private fun divider(
        activity: Activity,
        root: LinearLayout,
    ) {
        root.addView(
            View(activity).apply { setBackgroundColor(edge) },
            LinearLayout.LayoutParams(
                -1,
                dp(
                    activity,
                    DIVIDER_THICKNESS_DP,
                ),
            ),
        )
    }

    fun custom(
        activity: Activity,
        title: String,
        contents: (LinearLayout, Dialog) -> Unit,
    ): Dialog {
        val dialog = Dialog(activity)
        val shell =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(activity)
                setPadding(
                    dp(activity, COMPACT_PADDING_DP),
                    dp(activity, COMPACT_PADDING_DP),
                    dp(activity, COMPACT_PADDING_DP),
                    dp(activity, STANDARD_PADDING_VERTICAL_DP),
                )
            }
        shell.addView(label(activity, title, true))
        divider(activity, shell)
        contents(shell, dialog)
        dialog.setContentView(shell)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.show()
        val width = (activity.resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    fun choices(
        activity: Activity,
        title: String,
        choices: List<String>,
        onChoice: (Int) -> Unit,
    ) {
        custom(activity, title) { body, dialog ->
            choices.forEachIndexed { index, name ->
                val choice =
                    label(activity, name).apply {
                        setTextColor(textColor)
                        minHeight = dp(activity, CHOICE_ROW_MIN_HEIGHT_DP)
                        setBackgroundColor(if (index % ALTERNATING_ROW_PERIOD == 0) panel else D5BbDialogs.background)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            onChoice(index)
                        }
                    }
                body.addView(choice, LinearLayout.LayoutParams(-BB_DIALOGS_CHOICES_DP_1, -BB_DIALOGS_CHOICES_DP_2))
                divider(activity, body)
            }
        }
    }

    fun confirm(
        activity: Activity,
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit,
    ) {
        custom(activity, title) { body, dialog ->
            body.addView(label(activity, message))
            val row = LinearLayout(activity).apply { gravity = Gravity.END }
            body.addView(row)
            listOf("Cancel", confirmText).forEachIndexed { index, text ->
                val b =
                    label(activity, text).apply {
                        setTextColor(if (index == 1) accent else secondary)
                        gravity = Gravity.CENTER
                        isClickable = true
                        setOnClickListener {
                            dialog.dismiss()
                            if (index == 1) onConfirm()
                        }
                    }
                row.addView(b, LinearLayout.LayoutParams(0, dp(activity, ACTION_ROW_HEIGHT_DP), 1f))
            }
        }
    }

    fun message(
        activity: Activity,
        title: String,
        message: String,
    ) {
        confirm(activity, title, message, "OK") { }
    }

    fun prompt(
        activity: Activity,
        title: String,
        positive: String,
        onSubmit: (String) -> Unit,
    ) {
        custom(activity, title) { body, dialog ->
            val input =
                EditText(activity).apply {
                    hint = "Type a reply"
                    textSize = REPLY_TEXT_SIZE_SP
                    setTextColor(textColor)
                    setHintTextColor(secondary)
                    setSingleLine(false)
                    maxLines = REPLY_MAX_LINES
                    background = shape(activity)
                    setPadding(
                        dp(activity, INPUT_PADDING_HORIZONTAL_DP),
                        dp(activity, STANDARD_PADDING_VERTICAL_DP),
                        dp(activity, INPUT_PADDING_HORIZONTAL_DP),
                        dp(activity, STANDARD_PADDING_VERTICAL_DP),
                    )
                }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(activity, REPLY_INPUT_HEIGHT_DP)))
            val action =
                label(activity, positive).apply {
                    gravity = Gravity.CENTER
                    setTextColor(accent)
                    setOnClickListener {
                        val value = input.text.toString().trim()
                        if (value.isNotEmpty()) {
                            dialog.dismiss()
                            onSubmit(value)
                        }
                    }
                }
            body.addView(action, LinearLayout.LayoutParams(-1, dp(activity, ACTION_ROW_HEIGHT_DP)))
        }
    }

    fun multiChoice(
        activity: Activity,
        title: String,
        labels: List<String>,
        checked: BooleanArray,
        onSave: () -> Unit,
    ) {
        custom(activity, title) { body, dialog ->
            val choices = android.widget.ScrollView(activity)
            val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            choices.addView(rows)
            body.addView(choices, LinearLayout.LayoutParams(-1, dp(activity, MULTICHOICE_LIST_HEIGHT_DP)))
            labels.forEachIndexed { index, value ->
                val checkbox =
                    CheckBox(activity).apply {
                        text = value
                        textSize = CHOICE_TEXT_SIZE_SP
                        setTextColor(textColor)
                        buttonTintList = ColorStateList.valueOf(accent)
                        isChecked = checked[index]
                        setPadding(
                            dp(activity, COMPACT_PADDING_DP),
                            dp(activity, CHECKBOX_PADDING_VERTICAL_DP),
                            dp(activity, COMPACT_PADDING_DP),
                            dp(activity, CHECKBOX_PADDING_VERTICAL_DP),
                        )
                        setOnCheckedChangeListener { _, state -> checked[index] = state }
                    }
                rows.addView(
                    checkbox,
                    LinearLayout.LayoutParams(
                        -BB_DIALOGS_MULTI_CHOICE_DP_1,
                        -BB_DIALOGS_MULTI_CHOICE_DP_2,
                    ),
                )
            }
            val actions = LinearLayout(activity)
            body.addView(actions)
            listOf("Cancel", "Save").forEachIndexed { index, name ->
                actions.addView(
                    label(activity, name).apply {
                        gravity = Gravity.CENTER
                        setTextColor(if (index == 1) accent else secondary)
                        setOnClickListener {
                            dialog.dismiss()
                            if (index == 1) onSave()
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(activity, ACTION_ROW_HEIGHT_DP), 1f),
                )
            }
        }
    }
}
