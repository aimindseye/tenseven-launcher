package io.github.aimindseye.tenseven.features.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val DIALPAD_ON_CREATE_RGB_173 = 173
private const val DIALPAD_ON_CREATE_RGB_18 = 18
private const val DIALPAD_ON_CREATE_RGB_239 = 239
private const val DIALPAD_ON_CREATE_RGB_31 = 31
private const val DIALPAD_ON_CREATE_RGB_45 = 45

private const val DIALPAD_SIDE_DP = 18
private const val DIALPAD_TOP_DP = 9
private const val DIALPAD_BOTTOM_DP = 11
private const val DIALPAD_NUMBER_HEIGHT_DP = 48
private const val DIALPAD_KEY_TEXT_SP = 25f
private const val DIALPAD_KEY_HEIGHT_DP = 47
private const val DIALPAD_ACTION_TEXT_SP = 17f
private const val DIALPAD_ACTION_HEIGHT_DP = 46

/** An opt-in dialog-sized floating keypad; no SYSTEM_ALERT_WINDOW privilege or system overlay claimed. */
class TenSevenFloatingDialpadActivity : Activity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    /** Reuse the same dialpad key layout without inflating Activity.onCreate. */
    private fun addDigitKeys(
        root: LinearLayout,
        number: EditText,
    ) {
        listOf("123", "456", "789", "*0#").forEach { symbols ->
            val line = LinearLayout(this)
            root.addView(line)
            symbols.forEach { digit ->
                val key =
                    TextView(this).apply {
                        text = digit.toString()
                        textSize = DIALPAD_KEY_TEXT_SP
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setOnClickListener { number.append(digit.toString()) }
                    }
                line.addView(key, LinearLayout.LayoutParams(0, dp(DIALPAD_KEY_HEIGHT_DP), 1f))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(DIALPAD_SIDE_DP),
                    dp(DIALPAD_TOP_DP),
                    dp(DIALPAD_SIDE_DP),
                    dp(DIALPAD_BOTTOM_DP),
                )
                setBackgroundColor(
                    Color.rgb(
                        DIALPAD_ON_CREATE_RGB_18,
                        DIALPAD_ON_CREATE_RGB_31,
                        DIALPAD_ON_CREATE_RGB_45,
                    ),
                )
            }
        val number =
            EditText(this).apply {
                hint = "Dial number"
                inputType = android.text.InputType.TYPE_CLASS_PHONE
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
            }
        number.showSoftInputOnFocus = false
        root.addView(number, LinearLayout.LayoutParams(-1, dp(DIALPAD_NUMBER_HEIGHT_DP)))
        addDigitKeys(root, number)
        val place =
            TextView(this).apply {
                text =
                    this@TenSevenFloatingDialpadActivity.getString(
                        io.github.aimindseye.tenseven.phone.R.string.tenseven_float_open_dialer,
                    )
                textSize = DIALPAD_ACTION_TEXT_SP
                setTextColor(Color.rgb(0, DIALPAD_ON_CREATE_RGB_173, DIALPAD_ON_CREATE_RGB_239))
                gravity = Gravity.CENTER
                setOnClickListener {
                    val target =
                        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.text.toString(), null))
                            .setClass(this@TenSevenFloatingDialpadActivity, TenSevenPhoneActivity::class.java)
                    startActivity(target)
                    finish()
                }
            }
        root.addView(place, LinearLayout.LayoutParams(-1, dp(DIALPAD_ACTION_HEIGHT_DP)))
        setContentView(root)
    }
}
