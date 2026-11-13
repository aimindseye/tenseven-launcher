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

private object DialpadMetrics {
    const val SIDE_DP = 18
    const val TOP_DP = 9
    const val BOTTOM_DP = 11
    const val NUMBER_HEIGHT_DP = 48
    const val KEY_TEXT_SP = 25f
    const val KEY_HEIGHT_DP = 47
    const val ACTION_TEXT_SP = 17f
    const val ACTION_HEIGHT_DP = 46
}

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
                        textSize = DialpadMetrics.KEY_TEXT_SP
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setOnClickListener { number.append(digit.toString()) }
                    }
                line.addView(key, LinearLayout.LayoutParams(0, dp(DialpadMetrics.KEY_HEIGHT_DP), 1f))
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
                    dp(DialpadMetrics.SIDE_DP),
                    dp(DialpadMetrics.TOP_DP),
                    dp(DialpadMetrics.SIDE_DP),
                    dp(DialpadMetrics.BOTTOM_DP),
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
        root.addView(number, LinearLayout.LayoutParams(-1, dp(DialpadMetrics.NUMBER_HEIGHT_DP)))
        addDigitKeys(root, number)
        val place =
            TextView(this).apply {
                text =
                    this@TenSevenFloatingDialpadActivity.getString(
                        io.github.aimindseye.tenseven.R.string.tenseven_float_open_dialer,
                    )
                textSize = DialpadMetrics.ACTION_TEXT_SP
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
        root.addView(place, LinearLayout.LayoutParams(-1, dp(DialpadMetrics.ACTION_HEIGHT_DP)))
        setContentView(root)
    }
}
