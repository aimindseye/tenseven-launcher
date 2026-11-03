package io.github.aimindseye.tenseven.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RemoteViews

/** A real provider in the separate test APK; never shipped in the Full APK. */
open class P3WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "P3 widget $id")
            })
        }
    }
}

class P3ConfiguredWidgetProvider : P3WidgetProvider()

class P3WidgetConfigureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(Button(this).apply {
            text = "P3 save widget"
            setOnClickListener {
                P3WidgetProvider().onUpdate(this@P3WidgetConfigureActivity,
                    AppWidgetManager.getInstance(this@P3WidgetConfigureActivity), intArrayOf(id))
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
            }
        })
        layout.addView(Button(this).apply {
            text = "P3 cancel widget"
            setOnClickListener { finish() }
        })
        setContentView(layout)
    }
}
