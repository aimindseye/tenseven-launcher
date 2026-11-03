package io.github.aimindseye.tenseven.widgets

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.util.SizeF
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.model.WidgetBinding
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.platform.WidgetHostController
import io.github.aimindseye.tenseven.platform.WidgetHostStore
import io.github.aimindseye.tenseven.platform.WidgetTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHostIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun realProviderBindSizeReorderRecreateAndRemove() =
        withHost { scenario ->
            var ids = emptyList<Int>()
            var rendered: android.appwidget.AppWidgetHostView? = null
            scenario.onActivity { activity ->
                val state = StartStateRepository(activity)
                val host = controller(activity)
                host.startListening()
                val provider = provider(host, false)
                repeat(2) { host.begin(provider) }
                ids = state.loadWidgetIds()
                assertEquals(2, ids.size)
                host.move(ids.last(), -1)
                assertEquals(ids.reversed(), state.loadWidgetIds())
                val view = host.createView(ids.first())
                assertNotNull(view)
                rendered = view
                view!!.updateAppWidgetSize(android.os.Bundle(), listOf(SizeF(240f, 160f)))
                assertTrue(
                    AppWidgetManager
                        .getInstance(activity)
                        .getAppWidgetOptions(ids.first())
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0,
                )
                val resized = WidgetHostStore(activity).resize(ids.first(), 110, 1)
                assertTrue(resized > 110)
            }
            await {
                var visible = false
                instrumentation.runOnMainSync {
                    visible = rendered
                        ?.findViewById<android.widget.TextView>(android.R.id.text1)
                        ?.text
                        ?.contains("P3 widget") == true
                }
                visible
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val host = controller(activity)
                assertEquals(ids.reversed(), host.load())
                assertTrue(WidgetHostStore(activity).height(ids.first(), 110) > 110)
                // Android may delete an allocated id independently of the saved launcher list.
                AppWidgetHost(activity, HOST_ID).deleteAppWidgetId(ids.first())
                assertFalse(ids.first() in host.load())
                host.remove(ids.last())
                assertTrue(StartStateRepository(activity).loadWidgetIds().isEmpty())
            }
        }

    @Test fun configureCancelAndSaveUsePersistedTransactionAndExactId() =
        withHost { scenario ->
            var pendingId = -1
            scenario.onActivity { activity ->
                val host = controller(activity)
                host.begin(provider(host, true), WidgetTarget.HOME)
                pendingId = WidgetHostStore(activity).pending()!!.id
                // A new controller recovers the journal; a stale result must not commit this widget.
                controller(activity).onActivityResult(
                    WidgetHostController.REQUEST_CONFIGURE,
                    Activity.RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId + 1),
                )
                assertTrue(StartStateRepository(activity).loadHomeWidgetIds().isEmpty())
            }
            clickConfiguration("P3 cancel widget")
            await { WidgetHostStore(context).pending() == null }
            assertNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(pendingId))
            scenario.onActivity { activity ->
                val host = controller(activity)
                host.begin(provider(host, true), WidgetTarget.HOME)
                pendingId = WidgetHostStore(activity).pending()!!.id
            }
            clickConfiguration("P3 save widget")
            await { WidgetHostStore(context).pending() == null }
            assertEquals(listOf(pendingId), StartStateRepository(context).loadHomeWidgetIds())
        }

    @Test fun setupFromAnEarlierBootIsDiscardedBeforeOrphanReconciliation() {
        val store = WidgetHostStore(context)
        store.save(WidgetBinding(123, "fixture/Widget", 0, "PAGE", WidgetBinding.Stage.Bind))
        context
            .getSharedPreferences("widget_host_state", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("pending_boot", -2)
            .commit()
        assertNull(WidgetHostStore(context).pending())
    }

    private fun withHost(test: (ActivityScenario<SableStartActivity>) -> Unit) {
        val state = StartStateRepository(context)
        state.saveOnboardingDone()
        val oldPage = state.loadWidgetIds()
        val oldHome = state.loadHomeWidgetIds()
        state.saveWidgetIds(emptyList())
        state.saveHomeWidgetIds(emptyList())
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        try {
            ActivityScenario.launch(SableStartActivity::class.java).use { scenario ->
                try {
                    test(scenario)
                } finally {
                    scenario.onActivity { activity ->
                        val host = controller(activity)
                        (state.loadWidgetIds() + state.loadHomeWidgetIds()).forEach(host::remove)
                        WidgetHostStore(activity).pending()?.let { pending ->
                            host.onActivityResult(WidgetHostController.REQUEST_CONFIGURE, Activity.RESULT_CANCELED)
                            AppWidgetHost(activity, HOST_ID).deleteAppWidgetId(pending.id)
                        }
                    }
                }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            state.saveWidgetIds(oldPage)
            state.saveHomeWidgetIds(oldHome)
        }
    }

    private fun controller(activity: Activity): WidgetHostController =
        WidgetHostController(activity, StartStateRepository(activity)) { _, _ -> }

    private fun provider(
        host: WidgetHostController,
        configured: Boolean,
    ): AppWidgetProviderInfo {
        val name = if (configured) P3ConfiguredWidgetProvider::class.java.name else P3WidgetProvider::class.java.name
        return host.providers().first { it.provider.className == name }
    }

    private fun clickConfiguration(text: String) {
        await {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull()
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        instrumentation.waitForIdleSync()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + TIMEOUT_MS
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            android.os.SystemClock.sleep(POLL_MS)
        }
        assertTrue("Widget lifecycle condition timed out", condition())
    }

    private companion object {
        const val HOST_ID = 0x7E57
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L
    }
}
