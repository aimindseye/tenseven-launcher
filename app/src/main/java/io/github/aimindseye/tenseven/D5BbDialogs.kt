package io.github.aimindseye.tenseven

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** BB10-inspired local dialogs: no platform-white alert sheets on light-mode devices. */
object D5BbDialogs {
    private val background = Color.rgb(7, 12, 19)
    private val panel = Color.rgb(19, 30, 43)
    private val textColor = Color.WHITE
    private val secondary = Color.rgb(184, 201, 215)
    private val accent = Color.rgb(0, 166, 237)
    private val edge = Color.rgb(53, 75, 92)
    private fun dp(activity: Activity, n: Int) = (activity.resources.displayMetrics.density * n).toInt()
    private fun shape(activity: Activity) = GradientDrawable().apply {
        setColor(background)
        cornerRadius = dp(activity, 14).toFloat()
        setStroke(dp(activity, 1), edge)
    }
    private fun label(activity: Activity, text: String, title: Boolean = false): TextView = TextView(activity).apply {
        this.text = text
        textSize = if (title) 19f else 15f
        setTextColor(if (title) textColor else secondary)
        if (title) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(activity, 13), dp(activity, 9), dp(activity, 13), dp(activity, 9))
    }
    private fun divider(activity: Activity, root: LinearLayout) {
        root.addView(View(activity).apply { setBackgroundColor(edge) }, LinearLayout.LayoutParams(-1, dp(activity, 1)))
    }
    fun custom(activity: Activity, title: String, contents: (LinearLayout, Dialog) -> Unit): Dialog {
        val dialog = Dialog(activity)
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(activity)
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 9))
        }
        shell.addView(label(activity, title, true))
        divider(activity, shell)
        contents(shell, dialog)
        dialog.setContentView(shell)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.show()
        val width = (activity.resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }
    fun choices(activity: Activity, title: String, choices: List<String>, onChoice: (Int) -> Unit) {
        custom(activity, title) { body, dialog ->
            choices.forEachIndexed { index, name ->
                val choice = label(activity, name).apply {
                    setTextColor(textColor)
                    minHeight = dp(activity, 46)
                    setBackgroundColor(if (index % 2 == 0) panel else D5BbDialogs.background)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { dialog.dismiss(); onChoice(index) }
                }
                body.addView(choice, LinearLayout.LayoutParams(-1, -2))
                divider(activity, body)
            }
        }
    }
    fun confirm(activity: Activity, title: String, message: String, confirmText: String, onConfirm: () -> Unit) {
        custom(activity, title) { body, dialog ->
            body.addView(label(activity, message))
            val row = LinearLayout(activity).apply { gravity = Gravity.END }
            body.addView(row)
            listOf("Cancel", confirmText).forEachIndexed { index, text ->
                val b = label(activity, text).apply {
                    setTextColor(if (index == 1) accent else secondary)
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener { dialog.dismiss(); if (index == 1) onConfirm() }
                }
                row.addView(b, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
            }
        }
    }
    fun message(activity: Activity, title: String, message: String) {
        confirm(activity, title, message, "OK") { }
    }
    fun prompt(activity: Activity, title: String, positive: String, onSubmit: (String) -> Unit) {
        custom(activity, title) { body, dialog ->
            val input = EditText(activity).apply {
                hint = "Type a reply"
                textSize = 16f
                setTextColor(textColor)
                setHintTextColor(secondary)
                setSingleLine(false)
                maxLines = 3
                background = shape(activity)
                setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9))
            }
            body.addView(input, LinearLayout.LayoutParams(-1, dp(activity, 85)))
            val action = label(activity, positive).apply {
                gravity = Gravity.CENTER
                setTextColor(accent)
                setOnClickListener {
                    val value = input.text.toString().trim()
                    if (value.isNotEmpty()) { dialog.dismiss(); onSubmit(value) }
                }
            }
            body.addView(action, LinearLayout.LayoutParams(-1, dp(activity, 48)))
        }
    }
    fun multiChoice(activity: Activity, title: String, labels: List<String>, checked: BooleanArray, onSave: () -> Unit) {
        custom(activity, title) { body, dialog ->
            val choices = android.widget.ScrollView(activity)
            val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            choices.addView(rows)
            body.addView(choices, LinearLayout.LayoutParams(-1, dp(activity, 270)))
            labels.forEachIndexed { index, value ->
                val checkbox = CheckBox(activity).apply {
                    text = value
                    textSize = 14f
                    setTextColor(textColor)
                    buttonTintList = ColorStateList.valueOf(accent)
                    isChecked = checked[index]
                    setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6))
                    setOnCheckedChangeListener { _, state -> checked[index] = state }
                }
                rows.addView(checkbox, LinearLayout.LayoutParams(-1, -2))
            }
            val actions = LinearLayout(activity)
            body.addView(actions)
            listOf("Cancel", "Save").forEachIndexed { index, name ->
                actions.addView(label(activity, name).apply {
                    gravity = Gravity.CENTER
                    setTextColor(if (index == 1) accent else secondary)
                    setOnClickListener { dialog.dismiss(); if (index == 1) onSave() }
                }, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
            }
        }
    }
}
