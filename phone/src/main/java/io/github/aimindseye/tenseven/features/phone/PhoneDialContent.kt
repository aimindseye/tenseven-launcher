package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Activity-bound PhoneDialContent renderer; lifecycle and mutable state remain with the activity. */
internal class PhoneDialContent(
    private val activity: TenSevenPhoneActivity,
) {
    /** Dial content has no ScrollView; the keypad stretches only inside the inset-safe window. */
    fun dial(frame: FrameLayout) {
        val metrics = activity.windowManager.currentWindowMetrics
        val safe =
            metrics.windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val density = activity.resources.displayMetrics.density
        val widthDp = (metrics.bounds.width() - safe.left - safe.right) / density
        val heightDp = (metrics.bounds.height() - safe.top - safe.bottom) / density
        val size = PhoneUiRules.bounds(widthDp, heightDp)
        val panel =
            activity.views.column().apply {
                setPadding(
                    activity.views.px(PHONE_SECTION_INSET_DP),
                    activity.views.px(PHONE_EXTRA_TIGHT_INSET_DP),
                    activity.views.px(PHONE_SECTION_INSET_DP),
                    activity.views.px(PHONE_COMPACT_INSET_DP),
                )
            }
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))

        val input = renderDialInput(panel, size)
        val matches = activity.views.column()
        panel.addView(matches, LinearLayout.LayoutParams(-1, 0))
        val people = if (activity.isGranted(Manifest.permission.READ_CONTACTS)) activity.data.people() else emptyList()

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    updateDialMatches(s?.toString().orEmpty(), people, size, matches, input)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        updateDialMatches(activity.dialNumber, people, size, matches, input)

        val keypad = activity.views.column()
        panel.addView(keypad, LinearLayout.LayoutParams(-1, 0, 1f))
        renderDialKeypad(keypad, input, size)
        renderDialCallAction(panel, input, size)
    }

    private fun renderDialInput(
        panel: LinearLayout,
        size: PhoneUiRules.Bounds,
    ): EditText {
        val numberRow = activity.views.row()
        panel.addView(
            numberRow,
            LinearLayout.LayoutParams(
                -PHONE_DIAL_DP_1,
                activity.views.px(
                    if (size
                            .compact
                    ) {
                        PHONE_DIAL_DP_43
                    } else {
                        PHONE_DIAL_DP_52
                    },
                ),
            ),
        )
        val input =
            EditText(activity).apply {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_PHONE
                showSoftInputOnFocus = false // Hardware QWERTY and touch keys both remain available.
                textSize = if (size.compact) PHONE_DIAL_SP_24_F else PHONE_DIAL_SP_28_F
                gravity = Gravity.CENTER
                setTextColor(activity.views.colors.fg)
                setHintTextColor(activity.views.colors.muted)
                hint = "Enter number"
                background = null
                setText(activity.dialNumber)
                setSelection(text.length)
            }
        numberRow.addView(input, LinearLayout.LayoutParams(0, -1, 1f))
        numberRow.addView(
            activity.views
                .clickLabel("⌫", "Delete last digit", PHONE_DIAL_RATIO_25_F) {
                    if (input.text.isNotEmpty()) input.text.delete(input.length() - 1, input.length())
                }.apply {
                    setOnLongClickListener {
                        input.text.clear()
                        true
                    }
                },
            LinearLayout.LayoutParams(activity.views.px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP), -1),
        )
        activity.views.rule(panel)

        return input
    }

    private fun updateDialMatches(
        text: String,
        people: List<TenSevenPhoneData.Person>,
        size: PhoneUiRules.Bounds,
        matches: LinearLayout,
        input: EditText,
    ) {
        activity.dialNumber = text
        matches.removeAllViews()
        val results =
            PhoneUiRules.dialMatches(
                text,
                people.map {
                    PhoneUiRules.Match(it.id, it.name, it.number)
                },
                size.maxMatches,
            )
        // No blank reserved match panel when the number matches nobody.
        matches.layoutParams =
            LinearLayout.LayoutParams(
                -PHONE_MATCHES_DP_1,
                activity.views.px(results.size * (if (size.compact) PHONE_MATCHES_DP_37 else PHONE_MATCHES_DP_44)),
            )
        results.forEach { match ->
            addDialMatchRow(matches, input, size, match)
        }
    }

    private fun addDialMatchRow(
        matches: LinearLayout,
        input: EditText,
        size: PhoneUiRules.Bounds,
        match: PhoneUiRules.Match,
    ) {
        val cell =
            activity.views.row().apply {
                setPadding(
                    activity.views.px(PHONE_SMALL_INSET_DP),
                    activity.views.px(PHONE_MICRO_INSET_DP),
                    activity.views.px(PHONE_NARROW_GUTTER_DP),
                    activity.views.px(PHONE_MICRO_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "Dial ${match.name} ${match.number}"
                setOnClickListener {
                    input.setText(match.number)
                    input.setSelection(input.length())
                }
            }
        matches.addView(
            cell,
            LinearLayout.LayoutParams(
                -PHONE_MATCHES_DP_1,
                activity.views.px(if (size.compact) PHONE_MATCHES_DP_36 else PHONE_MATCHES_DP_43),
            ),
        )
        cell.addView(
            TenSevenContactPhotos.avatar(
                activity,
                match.contactId,
                match.name,
                PHONE_MATCHES_VALUE_32,
                activity.views.colors,
            ),
            LinearLayout.LayoutParams(
                activity.views.px(PHONE_COMPACT_AVATAR_EDGE_DP),
                activity.views.px(PHONE_COMPACT_AVATAR_EDGE_DP),
            ),
        )
        val names =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.views.px(PHONE_ROW_CONTENT_GUTTER_DP), 0, 0, 0)
            }
        cell.addView(names, LinearLayout.LayoutParams(0, -PHONE_MATCHES_DP_2, 1f))
        names.addView(activity.views.label(match.name, PHONE_COMPACT_TEXT_SP, bold = true))
        names.addView(activity.views.label(match.number, PHONE_MATCHES_SP_11_F, activity.views.colors.muted))
        cell.addView(activity.views.label("›", PHONE_SYMBOL_TEXT_SP, activity.views.colors.muted))
        activity.views.rule(matches)
    }

    private fun renderDialKeypad(
        keypad: LinearLayout,
        input: EditText,
        size: PhoneUiRules.Bounds,
    ) {
        val digits =
            listOf(
                "1" to "",
                "2" to "ABC",
                "3" to "DEF",
                "4" to "GHI",
                "5" to "JKL",
                "6" to "MNO",
                "7" to "PQRS",
                "8" to "TUV",
                "9" to "WXYZ",
                "*" to "",
                "0" to "+",
                "#" to "",
            )
        val keyVisual = DialKeyVisual(input, size)
        digits.chunked(PHONE_KEYPAD_VALUE_3).forEach { trio ->
            val keyRow = activity.views.row()
            keypad.addView(keyRow, LinearLayout.LayoutParams(-1, 0, 1f))
            trio.forEachIndexed { index, (digit, letters) ->
                addDialKey(keyRow, digit, letters, index, keyVisual)
            }
            activity.views.rule(keypad)
        }
    }

    private data class DialKeyVisual(
        val input: EditText,
        val size: PhoneUiRules.Bounds,
    )

    private fun addDialKey(
        keyRow: LinearLayout,
        digit: String,
        letters: String,
        index: Int,
        visual: DialKeyVisual,
    ) {
        val input = visual.input
        val size = visual.size
        val key =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "$digit ${letters.ifEmpty { "key" }}"
                setOnClickListener { input.append(digit) }
                if (digit == "0") {
                    setOnLongClickListener {
                        input.append("+")
                        true
                    }
                }
            }
        keyRow.addView(key, LinearLayout.LayoutParams(0, -1, 1f))
        key.addView(
            activity.views
                .label(digit, if (size.compact) PHONE_KEYPAD_SP_23_F else PHONE_LARGE_SYMBOL_TEXT_SP)
                .apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, 0, 2f),
        )
        key.addView(
            activity.views
                .label(
                    letters,
                    if (size.compact) PHONE_KEYPAD_SP_9_F else PHONE_KEYPAD_SP_10_F,
                    activity.views.colors.muted,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        if (index < 2) {
            keyRow.addView(
                View(activity).apply { setBackgroundColor(activity.views.colors.line) },
                LinearLayout.LayoutParams(activity.views.px(PHONE_HAIRLINE_DP), -1),
            )
        }
    }

    private fun renderDialCallAction(
        panel: LinearLayout,
        input: EditText,
        size: PhoneUiRules.Bounds,
    ) {
        val callRow = activity.views.row().apply { gravity = Gravity.CENTER }
        panel.addView(callRow, LinearLayout.LayoutParams(-1, activity.views.px(size.primaryCallHeightDp)))
        val call =
            activity.views
                .clickLabel("✆", "Place call", PHONE_LARGE_SYMBOL_TEXT_SP) {
                    activity.dialNumber = input.text.toString()
                    activity.placeCall(activity.dialNumber)
                }.apply {
                    background = activity.views.round(activity.views.colors.success, PHONE_KEYPAD_VALUE_24)
                    setTextColor(Color.WHITE)
                }
        callRow.addView(
            call,
            LinearLayout.LayoutParams(
                activity.views.px(if (size.compact) PHONE_KEYPAD_DP_126 else PHONE_KEYPAD_DP_145),
                activity.views.px(size.primaryCallHeightDp - PHONE_KEYPAD_DP_8),
            ),
        )
    }
}
