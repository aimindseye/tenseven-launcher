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

/** An opt-in dialog-sized floating keypad; no SYSTEM_ALERT_WINDOW privilege or system overlay claimed. */
class TenSevenFloatingDialpadActivity : Activity() {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(9), dp(18), dp(11))
            setBackgroundColor(Color.rgb(18, 31, 45))
        }
        val number = EditText(this).apply {
            hint = "Dial number"; inputType = android.text.InputType.TYPE_CLASS_PHONE
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        number.showSoftInputOnFocus = false
        root.addView(number, LinearLayout.LayoutParams(-1, dp(48)))
        listOf("123", "456", "789", "*0#").forEach { symbols ->
            val line = LinearLayout(this)
            root.addView(line)
            symbols.forEach { digit ->
                val key = TextView(this).apply {
                    text = digit.toString()
                    textSize = 25f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setOnClickListener { number.append(digit.toString()) }
                }
                line.addView(key, LinearLayout.LayoutParams(0, dp(47), 1f))
            }
        }
        val place = TextView(this).apply {
            text = "Open dialer"
            textSize = 17f
            setTextColor(Color.rgb(0, 173, 239))
            gravity = Gravity.CENTER
            setOnClickListener {
                val target = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.text.toString(), null))
                    .setClass(this@TenSevenFloatingDialpadActivity, TenSevenPhoneActivity::class.java)
                startActivity(target)
                finish()
            }
        }
        root.addView(place, LinearLayout.LayoutParams(-1, dp(46)))
        setContentView(root)
    }
}
