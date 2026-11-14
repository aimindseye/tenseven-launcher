package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import io.github.aimindseye.tenseven.D5BbDialogs
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CALENDAR_DAYS_PER_WEEK = 7

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
internal const val CALENDAR_AGENDA_SP_13_F = 13f
internal const val CALENDAR_AGENDA_SP_15_F = 15f
internal const val CALENDAR_CALENDAR_SELECTION_REQUEST_221 = 221
internal const val CALENDAR_CALENDAR_SELECTION_SP_14_F = 14f
internal const val CALENDAR_CALENDAR_SELECTION_SP_15_F = 15f
internal const val CALENDAR_CALENDAR_SELECTION_SP_16_F = 16f
internal const val CALENDAR_DAY_DP_1 = 1
internal const val CALENDAR_DAY_DP_22 = 22
internal const val CALENDAR_DAY_DP_24 = 24
internal const val CALENDAR_DAY_DP_50 = 50
internal const val CALENDAR_DAY_SP_12_F = 12f
internal const val CALENDAR_DAY_VALUE_8 = 8
private const val CALENDAR_DRAW_MAX_14 = 14
private const val CALENDAR_DRAW_RATIO_12_F = 12f
private const val CALENDAR_DRAW_RATIO_14_F = 14f
private const val CALENDAR_DRAW_RATIO_26_F = 26f
private const val CALENDAR_DRAW_RATIO_30_F = 30f
private const val CALENDAR_DRAW_RATIO_3_F = 3f
private const val CALENDAR_DRAW_RATIO_4_F = 4f
private const val CALENDAR_DRAW_RATIO_6_F = 6f
private const val CALENDAR_DRAW_RATIO_8_F = 8f
private const val CALENDAR_DRAW_RGB_151 = 151
private const val CALENDAR_DRAW_RGB_169 = 169
private const val CALENDAR_DRAW_RGB_180 = 180
private const val CALENDAR_DRAW_RGB_192 = 192
private const val CALENDAR_DRAW_RGB_205 = 205
private const val CALENDAR_DRAW_RGB_235 = 235
private const val CALENDAR_DRAW_RGB_238 = 238
private const val CALENDAR_DRAW_RGB_24 = 24
private const val CALENDAR_DRAW_RGB_31 = 31
private const val CALENDAR_DRAW_RGB_40 = 40
private const val CALENDAR_DRAW_RGB_50 = 50
private const val CALENDAR_DRAW_RGB_57 = 57
private const val CALENDAR_DRAW_RGB_66 = 66
private const val CALENDAR_DRAW_SP_11_F = 11f
private const val CALENDAR_DRAW_VALUE_7 = 7
internal const val CALENDAR_EDITOR_RATIO_12_F = 12f
internal const val CALENDAR_EDITOR_SP_15_F = 15f
internal const val CALENDAR_EDITOR_SP_17_F = 17f
internal const val CALENDAR_EDITOR_TIME_3600_000_L = 3600_000L
internal const val CALENDAR_EDITOR_VALUE_10 = 10
internal const val CALENDAR_EDITOR_VALUE_3 = 3
internal const val CALENDAR_EVENT_ROW_DP_1 = 1
internal const val CALENDAR_EVENT_ROW_DP_43 = 43
internal const val CALENDAR_EVENT_ROW_DP_51 = 51
internal const val CALENDAR_EVENT_ROW_SP_11_F = 11f
internal const val CALENDAR_EVENT_ROW_SP_14_F = 14f
internal const val CALENDAR_EVENT_ROW_SP_23_F = 23f
private const val CALENDAR_GLYPH_SP_23_F = 23f
private const val CALENDAR_HOME_DP_1 = 1
private const val CALENDAR_HOME_DP_42 = 42
private const val CALENDAR_HOME_DP_47 = 47
private const val CALENDAR_HOME_RATIO_11_F = 11f
private const val CALENDAR_HOME_RATIO_13_F = 13f
private const val CALENDAR_HOME_RATIO_22_F = 22f
private const val CALENDAR_HOME_REQUEST_221 = 221
private const val CALENDAR_HOME_SP_12_F = 12f
private const val CALENDAR_HOME_SP_15_F = 15f
private const val CALENDAR_HOME_SP_18_F = 18f
private const val CALENDAR_HOME_VALUE_6 = 6
private const val CALENDAR_LOAD_COL_3 = 3
private const val CALENDAR_LOAD_COL_4 = 4
private const val CALENDAR_LOAD_COL_5 = 5
private const val CALENDAR_LOAD_MAX_3000 = 3000
internal const val CALENDAR_MONTH_DATE_6 = 6
internal const val CALENDAR_MONTH_DATE_7 = 7
internal const val CALENDAR_MONTH_VALUE_27 = 27
internal const val CALENDAR_MONTH_VALUE_7 = 7
private const val CALENDAR_MOVE_DATE_TIME_7_L = 7L
private const val CALENDAR_SETTINGS_MENU_VALUE_3 = 3
private const val CALENDAR_SETTINGS_MENU_VALUE_4 = 4
private const val CALENDAR_SETTINGS_MENU_VALUE_5 = 5
private const val CALENDAR_SETTINGS_MENU_VALUE_6 = 6
internal const val CALENDAR_SETTINGS_RATIO_12_F = 12f
internal const val CALENDAR_SETTINGS_SP_15_F = 15f
internal const val CALENDAR_WEEK_BAR_COUNT_7 = 7
internal const val CALENDAR_WEEK_BAR_MAX_3 = 3
internal const val CALENDAR_WEEK_BAR_SP_10_F = 10f
internal const val CALENDAR_WEEK_BAR_SP_15_F = 15f
internal const val CALENDAR_WEEK_BAR_VALUE_7 = 7
internal const val CALENDAR_WEEK_COUNT_7 = 7
internal const val CALENDAR_WEEK_MAX_5 = 5
internal const val CALENDAR_WEEK_RATIO_15_F = 15f

internal data class D5R2Calendar(
    val id: Long,
    val name: String,
    val color: Int,
    val account: String,
)

internal data class D5R2Event(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
)

internal const val CALENDAR_HAIRLINE_DP = 1
internal const val CALENDAR_MICRO_GUTTER_DP = 2
internal const val CALENDAR_CONTROL_GAP_DP = 3
internal const val CALENDAR_SMALL_GUTTER_DP = 4
internal const val CALENDAR_COMPACT_GUTTER_DP = 5
internal const val CALENDAR_WEEK_PILL_RADIUS_DP = 6
internal const val CALENDAR_LABEL_VERTICAL_INSET_DP = 7
private const val CALENDAR_WEEK_NAV_RADIUS_DP = 8
internal const val CALENDAR_LIST_ITEM_GUTTER_DP = 9
internal const val CALENDAR_LABEL_END_INSET_DP = 10
internal const val CALENDAR_FORM_AND_LABEL_INSET_DP = 12
internal const val CALENDAR_SELECTION_MARK_DP = 13
internal const val CALENDAR_EVENT_DISCLOSURE_WIDTH_DP = 22
internal const val CALENDAR_PILL_RADIUS_DP = 25
private const val CALENDAR_HEADER_ACTION_NARROW_DP = 33
private const val CALENDAR_HEADER_ACTION_COMPACT_DP = 38
private const val CALENDAR_HEADER_ACTION_STANDARD_DP = 39
private const val CALENDAR_HEADER_ACTION_WIDE_DP = 41
internal const val CALENDAR_CALENDAR_TOGGLE_WIDTH_DP = 42
internal const val CALENDAR_CONTROL_HEIGHT_DP = 43
internal const val CALENDAR_CALENDAR_ROW_HEIGHT_DP = 49
internal const val CALENDAR_FORM_ACTION_HEIGHT_DP = 53
internal const val CALENDAR_FORM_SINGLE_LINE_HEIGHT_DP = 54
internal const val CALENDAR_FORM_NOTES_HEIGHT_DP = 95

/** Read-only multi-calendar month + busy arcs + week schedule; creation delegated to Android provider UI. */
class TenSevenCalendarActivity : Activity() {
    internal val views by lazy { CalendarViews(this) }
    private val contentActions =
        CalendarContentActions(::setDay, ::moveDate, ::selectMode, ::load, ::returnToMain, ::render)
    private val scheduleContent by lazy { CalendarScheduleContent(this, contentActions) }
    private val optionsContent by lazy { CalendarOptionsContent(this, contentActions) }
    internal var selected = LocalDate.now()
    internal var month = YearMonth.from(selected)

    // One view model for all approved screens; no stacked Month+Schedule page scrolling.
    private var mode = "Today"
    private var previousMode = "Today"
    internal var calendars = listOf<D5R2Calendar>()
    private var events = listOf<D5R2Event>()
    private var loading = false
    private var loadGeneration = 0
    internal val zone = ZoneId.systemDefault()

    // BB10 dark takes precedence over Android light/dark UI mode.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("TenSevenD5R7", "Calendar approved BB10 UI active")
        mode = getSharedPreferences("tenseven_calendar_view", MODE_PRIVATE)
            .getString("default_view", "Today")
            ?.takeIf { it in listOf("Today", "Week", "Month", "Day") } ?: "Today"
        render()
    }

    override fun onResume() {
        super.onResume()
        load()
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        load()
        render()
    }

    internal fun dateMs(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    internal fun mondayStart() =
        getSharedPreferences("tenseven_calendar_view", MODE_PRIVATE)
            .getBoolean("monday_start", false)

    internal fun weekStart(day: LocalDate = selected): LocalDate =
        day.minusDays(
            if (mondayStart()) {
                (day.dayOfWeek.value - 1).toLong()
            } else {
                (day.dayOfWeek.value % CALENDAR_DAYS_PER_WEEK).toLong()
            },
        )

    internal fun selectedIds(): Set<Long> {
        val prefs = getSharedPreferences("tenseven_calendars", MODE_PRIVATE)
        val saved = prefs.getStringSet("selected_ids", null)
        return if (saved == null) calendars.map { it.id }.toSet() else saved.mapNotNull { it.toLongOrNull() }.toSet()
    }

    internal fun storeIds(ids: Set<Long>) {
        getSharedPreferences(
            "tenseven_calendars",
            MODE_PRIVATE,
        ).edit {
            putStringSet("selected_ids", ids.map { it.toString() }.toSet())
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        if (!views.permission()) {
            calendars = emptyList()
            events = emptyList()
            loading = false
            return
        }
        val targetMonth = month
        loading = true
        // CalendarContract.Instances.query expands recurrence and can block; never run it on the UI thread.
        Thread {
            val found = queryCalendarSources()
            val loaded = queryCalendarEvents(targetMonth)
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != loadGeneration) return@runOnUiThread
                calendars = found
                events = loaded
                loading = false
                render()
            }
        }.start()
    }

    private fun queryCalendarSources(): List<D5R2Calendar> {
        val found = mutableListOf<D5R2Calendar>()
        try {
            val cols =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                )
            contentResolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    cols,
                    null,
                    null,
                    "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE LOCALIZED ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        found.add(
                            D5R2Calendar(
                                cursor.getLong(0),
                                cursor.getString(1) ?: "Calendar",
                                cursor.getInt(2),
                                cursor.getString(CALENDAR_LOAD_COL_3).orEmpty(),
                            ),
                        )
                    }
                }
        } catch (_: Exception) {
            // Permission or provider change.
        }
        return found
    }

    private fun queryCalendarEvents(targetMonth: YearMonth): List<D5R2Event> {
        val from = targetMonth.atDay(1).minusWeeks(1)
        val to = targetMonth.atEndOfMonth().plusWeeks(2)
        val loaded = mutableListOf<D5R2Event>()
        try {
            val columns =
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                )
            CalendarContract.Instances
                .query(
                    contentResolver,
                    columns,
                    dateMs(from),
                    dateMs(
                        to
                            .plusDays(1),
                    ),
                )?.use { cursor ->
                    while (cursor.moveToNext() && loaded.size < CALENDAR_LOAD_MAX_3000) {
                        loaded.add(
                            D5R2Event(
                                cursor.getLong(0),
                                cursor.getLong(1),
                                cursor.getString(2) ?: "(untitled)",
                                cursor.getLong(CALENDAR_LOAD_COL_3),
                                cursor.getLong(CALENDAR_LOAD_COL_4),
                                cursor.getInt(CALENDAR_LOAD_COL_5) != 0,
                            ),
                        )
                    }
                }
        } catch (_: Exception) {
            // Do not invent events when the provider fails.
        }
        return loaded
    }

    internal fun visibleEvents() = events.filter { it.calendarId in selectedIds() }

    internal fun eventsOn(day: LocalDate): List<D5R2Event> = calendarEventsOn(visibleEvents(), day, zone)

    private fun setDay(day: LocalDate) {
        val changedMonth = YearMonth.from(day) != month
        selected = day
        month = YearMonth.from(day)
        if (changedMonth) load()
        render()
    }

    private fun moveDate(direction: Int) {
        if (mode == "Month") {
            val nextMonth = month.plusMonths(direction.toLong())
            selected = nextMonth.atDay(selected.dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth()))
        } else {
            selected =
                selected.plusDays(
                    if (mode == "Week") CALENDAR_MOVE_DATE_TIME_7_L * direction else direction.toLong(),
                )
        }
        month = YearMonth.from(selected)
        load()
        render()
    }

    private fun selectMode(next: String) {
        if (next == "Today") {
            // Today is an action, not just a tab label: discard the previously viewed date.
            val target = D6TodayNavigationPolicy.reset(LocalDate.now(zone), month)
            selected = target.day
            month = target.month
            mode = "Today"
            if (target.reloadEvents) load()
            render()
            return
        }
        if (mode in listOf("Today", "Week", "Month", "Day") &&
            next in listOf("Calendars", "Settings", "New")
        ) {
            previousMode = mode
        }
        mode = next
        render()
    }

    private fun openNewEvent() {
        previousMode = mode
        mode = "New"
        render()
    }

    private fun returnToMain() {
        mode = if (previousMode in listOf("Today", "Week", "Month", "Day")) previousMode else "Today"
        render()
    }

    private fun glyph(
        value: String,
        content: String,
        action: () -> Unit,
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = CALENDAR_GLYPH_SP_23_F
            setTextColor(views.fg)
            gravity = Gravity.CENTER
            contentDescription = content
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun datePicker() {
        val picker =
            DatePickerDialog(
                this,
                android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK,
                { _, year, monthIndex, date -> setDay(LocalDate.of(year, monthIndex + 1, date)) },
                selected.year,
                selected.monthValue - 1,
                selected.dayOfMonth,
            )
        picker.show()
    }

    private fun settingsMenu() {
        D5BbDialogs.choices(
            this,
            "Calendar",
            listOf("Today", "Month", "Week", "Day", "Calendars", "Calendar settings", "Hub"),
        ) { index ->
            when (index) {
                0 -> selectMode("Today")
                1 -> selectMode("Month")
                2 -> selectMode("Week")
                CALENDAR_SETTINGS_MENU_VALUE_3 -> selectMode("Day")
                CALENDAR_SETTINGS_MENU_VALUE_4 -> selectMode("Calendars")
                CALENDAR_SETTINGS_MENU_VALUE_5 -> selectMode("Settings")
                CALENDAR_SETTINGS_MENU_VALUE_6 -> openHub()
            }
        }
    }

    private fun openHub() {
        try {
            startActivity(Intent("org.sableos.start.action.SHOW_HUB").setPackage(packageName))
        } catch (
            _: Exception,
        ) {
            D5BbDialogs.message(this, "Hub", "The Hub is currently unavailable")
        }
    }

    /** Each approved main screen occupies one display. Only event lists and timeline scroll internally. */
    private fun render() {
        TenSevenSafeViewport.darkSystemBars(this)
        val root = views.column()
        val header =
            views.row().apply {
                setPadding(views.dp(CALENDAR_CONTROL_GAP_DP), 0, views.dp(CALENDAR_CONTROL_GAP_DP), 0)
            }
        root.addView(
            header,
            LinearLayout.LayoutParams(
                -CALENDAR_HOME_DP_1,
                views.dp(if (views.fit().compact) CALENDAR_HOME_DP_42 else CALENDAR_HOME_DP_47),
            ),
        )
        val main = mode in listOf("Today", "Week", "Month", "Day")
        if (main) renderMainCalendarHeader(header) else renderSecondaryCalendarHeader(header)
        views.divider(root)
        val content = views.column()
        content.setPadding(views.dp(CALENDAR_COMPACT_GUTTER_DP), 0, views.dp(CALENDAR_COMPACT_GUTTER_DP), 0)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        renderCalendarContent(content)
        if (main) renderCalendarBottomBar(root)
        TenSevenSafeViewport.show(this, root)
    }

    private fun renderCalendarContent(content: LinearLayout) {
        if (mode == "Calendars") {
            optionsContent.renderCalendarSelection(content)
        } else if (mode == "Settings") {
            optionsContent.renderSettings(content)
        } else if (mode == "New") {
            optionsContent.renderEventComposer(content)
        } else if (!views.permission()) {
            views.label(content, "Allow Calendar access to show synced events.", CALENDAR_HOME_SP_15_F)
            val controls = views.row()
            content.addView(controls)
            views.button(
                controls,
                "Allow Calendars",
            ) { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), CALENDAR_HOME_REQUEST_221) }
        } else {
            if (loading) views.label(content, "Loading calendars…", CALENDAR_HOME_SP_12_F, color = views.muted)
            when (mode) {
                "Month" -> scheduleContent.renderMonthPage(content)
                "Week" -> scheduleContent.renderWeekPage(content)
                "Day" -> scheduleContent.renderDayPage(content)
                else -> scheduleContent.renderTodayPage(content)
            }
        }
    }

    private fun renderCalendarBottomBar(root: LinearLayout) {
        views.divider(root)
        val nav =
            views.row().apply {
                setPadding(
                    views.dp(CALENDAR_MICRO_GUTTER_DP),
                    views.dp(CALENDAR_HAIRLINE_DP),
                    views.dp(CALENDAR_MICRO_GUTTER_DP),
                    views.dp(CALENDAR_HAIRLINE_DP),
                )
            }
        root.addView(nav, LinearLayout.LayoutParams(-1, views.dp(views.fit().calendarNavDp)))
        listOf("Today", "Week", "Month", "Day", "+").forEach { destination ->
            nav.addView(
                calendarDestination(destination),
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    setMargins(views.dp(CALENDAR_MICRO_GUTTER_DP), 0, views.dp(CALENDAR_MICRO_GUTTER_DP), 0)
                },
            )
        }
    }

    private fun calendarDestination(destination: String): TextView =
        TextView(this).apply {
            val active = mode == destination
            text = if (destination == "+") "+" else destination
            textSize =
                if (destination == "+") {
                    CALENDAR_HOME_RATIO_22_F
                } else if (views.fit().compact) {
                    CALENDAR_HOME_RATIO_11_F
                } else {
                    CALENDAR_HOME_RATIO_13_F
                }
            setTextColor(if (active) Color.WHITE else views.muted)
            gravity = Gravity.CENTER
            background =
                if (active) {
                    GradientDrawable().apply {
                        cornerRadius = views.dp(CALENDAR_WEEK_NAV_RADIUS_DP).toFloat()
                        setColor(views.accent)
                    }
                } else {
                    null
                }
            contentDescription = if (destination == "+") "New event" else "$destination view"
            isFocusable = true
            setOnClickListener { if (destination == "+") openNewEvent() else selectMode(destination) }
        }

    private fun renderMainCalendarHeader(header: LinearLayout) {
        header.addView(
            glyph(
                "‹",
                "Previous ${if (mode == "Month") {
                    "month"
                } else if (mode == "Week") {
                    "week"
                } else {
                    "day"
                }}",
            ) {
                moveDate(-1)
            },
            LinearLayout.LayoutParams(views.dp(CALENDAR_HEADER_ACTION_STANDARD_DP), -1),
        )
        val heading = mainCalendarHeading()
        header.addView(heading, LinearLayout.LayoutParams(0, -1, 1f))
        header.addView(
            glyph(
                "›",
                "Next ${if (mode == "Month") {
                    "month"
                } else if (mode == "Week") {
                    "week"
                } else {
                    "day"
                }}",
            ) {
                moveDate(1)
            },
            LinearLayout.LayoutParams(views.dp(CALENDAR_HEADER_ACTION_COMPACT_DP), -1),
        )
        header.addView(
            glyph("▤", "Select calendars") { selectMode("Calendars") },
            LinearLayout.LayoutParams(views.dp(CALENDAR_HEADER_ACTION_STANDARD_DP), -1),
        )
        header.addView(
            glyph("⋮", "Calendar menu") { settingsMenu() },
            LinearLayout.LayoutParams(views.dp(CALENDAR_HEADER_ACTION_NARROW_DP), -1),
        )
    }

    private fun renderSecondaryCalendarHeader(header: LinearLayout) {
        header.addView(
            glyph("‹", "Back to Calendar") { returnToMain() },
            LinearLayout.LayoutParams(views.dp(CALENDAR_HEADER_ACTION_WIDE_DP), -1),
        )
        val heading =
            TextView(this).apply {
                text =
                    when (mode) {
                        "Calendars" -> "Calendars"
                        "Settings" -> "Calendar settings"
                        else -> "New event"
                    }
                textSize = CALENDAR_HOME_SP_18_F
                setTextColor(views.fg)
                gravity = Gravity.CENTER_VERTICAL
            }
        header.addView(heading, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun mainCalendarHeading(): TextView =
        TextView(this).apply {
            text =
                if (mode == "Month") {
                    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                } else if (mode == "Today" &&
                    selected == LocalDate.now(zone)
                ) {
                    "Today  ·  ${selected.format(DateTimeFormatter.ofPattern("MMM d"))}"
                } else if (mode == "Today") {
                    selected.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                } else if (mode ==
                    "Week"
                ) {
                    "${weekStart().format(
                        DateTimeFormatter.ofPattern("MMM d"),
                    )} – ${weekStart().plusDays(CALENDAR_HOME_VALUE_6.toLong()).format(
                        DateTimeFormatter
                            .ofPattern("MMM d"),
                    )}"
                } else {
                    selected.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                }
            textSize = CALENDAR_HOME_SP_18_F
            setTypeface(null, Typeface.BOLD)
            setTextColor(views.fg)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { datePicker() }
            contentDescription = "Choose date"
        }
}

/** Calendar rendering geometry is intentionally fixed to the approved BB10 design. */
private object CalendarDrawingSpecs {
    const val DAYS_PER_WEEK = 7
    const val LAST_DAY_INDEX = DAYS_PER_WEEK - 1
    const val CELL_CENTER = 0.5f
    const val HEADER_BASELINE = 0.7f
    const val MONTH_HEADER_DP = 32f
    const val WEEKDAY_TEXT_SP = 12f
    const val MONTH_DAY_TEXT_SP = 14f
    const val MONTH_RING_WIDTH = 0.36f
    const val MONTH_RING_HEIGHT = 0.38f
    const val MONTH_RING_STROKE_DP = 1.1f
    const val SELECTED_DAY_RADIUS = 0.8f
    const val DAY_NUMBER_BASELINE_DP = 5f
    const val ARC_EVENT_LIMIT = 24
    const val ARC_FULL_DEGREES = 360f
    const val ARC_START_DEGREES = -90f
    const val ARC_MIN_DEGREES = 3f
    const val ARC_LAYER_COUNT = 3
    const val ARC_STROKE_DP = 2.7f
    const val MONTH_SWIPE_DP = 65f
    const val TIMELINE_HOURS = 24
    const val TIMELINE_HOUR_MINUTES = 60f
    const val TIMELINE_GUTTER_DP = 54f
    const val TIMELINE_EVENT_LIMIT = 100
    const val TIMELINE_MAX_LANES = 3
}

internal data class CalendarEventPaint(
    val events: List<D5R2Event>,
    val colors: Map<Long, Int>,
    val zone: ZoneId,
)

internal data class MonthRenderModel(
    val month: YearMonth,
    val selected: LocalDate,
    val paint: CalendarEventPaint,
    val mondayStart: Boolean,
    val onSelected: (LocalDate) -> Unit,
    val onMonthSwipe: (Int) -> Unit,
)

internal data class DayRenderModel(
    val day: LocalDate,
    val paint: CalendarEventPaint,
    val onEvent: (D5R2Event) -> Unit,
)

/** Occupied-time arcs, one color per calendar, a real month grid with stable hit testing. */
internal class BusyMonthView private constructor(
    context: Context,
    attrs: AttributeSet?,
    private val model: MonthRenderModel,
) : View(context, attrs) {
    private val month get() = model.month
    private val selected get() = model.selected
    private val events get() = model.paint.events
    private val colors get() = model.paint.colors
    private val zone get() = model.paint.zone
    private val mondayStart get() = model.mondayStart
    private val onSelected get() = model.onSelected
    private val onMonthSwipe get() = model.onMonthSwipe

    // Android Layout Editor and inflation: no Activity cast and no external data access.
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : this(
        context,
        attrs,
        MonthRenderModel(
            YearMonth.now(),
            LocalDate.now(),
            CalendarEventPaint(emptyList(), emptyMap(), ZoneId.systemDefault()),
            false,
            {},
            {},
        ),
    )

    // Runtime uses the same immutable render state and callbacks as before.
    constructor(activity: TenSevenCalendarActivity, model: MonthRenderModel) : this(activity, null, model)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val fg = Color.rgb(245, 248, 250)
    private val selectedColor = Color.rgb(0, 150, 232)
    private val outline = Color.rgb(67, 77, 88)
    private val offset =
        if (mondayStart) {
            month.atDay(1).dayOfWeek.value - 1
        } else {
            month.atDay(1).dayOfWeek.value %
                CalendarDrawingSpecs.DAYS_PER_WEEK
        }
    private val start = month.atDay(1).minusDays(offset.toLong())
    private val rows =
        (offset + month.lengthOfMonth() + CalendarDrawingSpecs.LAST_DAY_INDEX) /
            CalendarDrawingSpecs
                .DAYS_PER_WEEK
    private var touchX = 0f
    private val arcBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width.toFloat() / CalendarDrawingSpecs.DAYS_PER_WEEK
        val titleHeight = CalendarDrawingSpecs.MONTH_HEADER_DP * density
        val cellHeight = (height.toFloat() - titleHeight) / rows
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.style = Paint.Style.FILL
        paint.textSize = CalendarDrawingSpecs.WEEKDAY_TEXT_SP * density
        paint.color = fg
        (
            if (mondayStart) {
                listOf("M", "T", "W", "T", "F", "S", "S")
            } else {
                listOf("S", "M", "T", "W", "T", "F", "S")
            }
        ).forEachIndexed { i, label ->
            canvas.drawText(
                label,
                cellWidth * (i + CalendarDrawingSpecs.CELL_CENTER),
                titleHeight * CalendarDrawingSpecs.HEADER_BASELINE,
                paint,
            )
        }
        for (index in 0 until rows * CalendarDrawingSpecs.DAYS_PER_WEEK) {
            drawMonthCell(canvas, index, cellWidth, titleHeight, cellHeight)
        }
    }

    private fun drawMonthCell(
        canvas: Canvas,
        index: Int,
        cellWidth: Float,
        titleHeight: Float,
        cellHeight: Float,
    ) {
        val day = start.plusDays(index.toLong())
        if (day.month != month.month) return
        val x = cellWidth * (index % CalendarDrawingSpecs.DAYS_PER_WEEK + CalendarDrawingSpecs.CELL_CENTER)
        val y =
            titleHeight + cellHeight *
                (index / CalendarDrawingSpecs.DAYS_PER_WEEK + CalendarDrawingSpecs.CELL_CENTER)
        val radius =
            minOf(
                cellWidth * CalendarDrawingSpecs.MONTH_RING_WIDTH,
                cellHeight * CalendarDrawingSpecs.MONTH_RING_HEIGHT,
            )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = CalendarDrawingSpecs.MONTH_RING_STROKE_DP * density
        paint.color = outline
        canvas.drawCircle(x, y, radius, paint)
        drawMonthArcs(canvas, day, x, y, radius)
        drawMonthLabel(canvas, day, x, y, radius)
    }

    private fun drawMonthArcs(
        canvas: Canvas,
        day: LocalDate,
        x: Float,
        y: Float,
        radius: Float,
    ) {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd =
            day
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val period = (dayEnd - dayStart).coerceAtLeast(1L).toFloat()
        val matched = events.filter { it.start < dayEnd && it.end > dayStart }
        matched.take(CalendarDrawingSpecs.ARC_EVENT_LIMIT).forEachIndexed { order, event ->
            val first = ((maxOf(event.start, dayStart) - dayStart) / period).coerceIn(0f, 1f)
            val last = ((minOf(event.end, dayEnd) - dayStart) / period).coerceIn(0f, 1f)
            val sweep =
                maxOf(
                    CalendarDrawingSpecs.ARC_MIN_DEGREES,
                    (last - first) * CalendarDrawingSpecs.ARC_FULL_DEGREES,
                )
            val radiusLayer =
                radius - (order % CalendarDrawingSpecs.ARC_LAYER_COUNT) * CalendarDrawingSpecs.ARC_STROKE_DP * density
            paint.color = colors[event.calendarId] ?: selectedColor
            paint.strokeWidth = CalendarDrawingSpecs.ARC_STROKE_DP * density
            arcBounds.set(x - radiusLayer, y - radiusLayer, x + radiusLayer, y + radiusLayer)
            canvas.drawArc(
                arcBounds,
                CalendarDrawingSpecs.ARC_START_DEGREES + first * CalendarDrawingSpecs.ARC_FULL_DEGREES,
                sweep,
                false,
                paint,
            )
        }
    }

    private fun drawMonthLabel(
        canvas: Canvas,
        day: LocalDate,
        x: Float,
        y: Float,
        radius: Float,
    ) {
        if (day == selected) {
            paint.style = Paint.Style.FILL
            paint.color = selectedColor
            canvas.drawCircle(x, y, radius * CalendarDrawingSpecs.SELECTED_DAY_RADIUS, paint)
        }
        paint.style = Paint.Style.FILL
        paint.typeface = if (day == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textSize = CalendarDrawingSpecs.MONTH_DAY_TEXT_SP * density
        paint.color = if (day == selected) Color.WHITE else fg
        canvas.drawText(
            day.dayOfMonth.toString(),
            x,
            y + CalendarDrawingSpecs.DAY_NUMBER_BASELINE_DP * density,
            paint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                true
            }

            MotionEvent.ACTION_UP -> {
                val handled = handleMonthTouchUp(event)
                if (handled) performClick()
                handled
            }

            else -> {
                super.onTouchEvent(event)
            }
        }

    private fun handleMonthTouchUp(event: MotionEvent): Boolean {
        val swipe = event.x - touchX
        if (kotlin.math.abs(swipe) > CalendarDrawingSpecs.MONTH_SWIPE_DP * density) {
            onMonthSwipe(if (swipe < 0) 1 else -1)
            return true
        }
        val titleHeight = CalendarDrawingSpecs.MONTH_HEADER_DP * density
        if (event.y >= titleHeight) {
            selectMonthCell(event, titleHeight)
        }
        return true
    }

    private fun selectMonthCell(
        event: MotionEvent,
        titleHeight: Float,
    ) {
        val cellHeight = (height.toFloat() - titleHeight) / rows
        val col =
            (event.x / (width.toFloat() / CalendarDrawingSpecs.DAYS_PER_WEEK)).toInt().coerceIn(
                0,
                CalendarDrawingSpecs.LAST_DAY_INDEX,
            )
        val row = ((event.y - titleHeight) / cellHeight).toInt().coerceIn(0, rows - 1)
        val day = start.plusDays((row * CalendarDrawingSpecs.DAYS_PER_WEEK + col).toLong())
        if (day.month == month.month) onSelected(day)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/** Single-day full timeline. Scrolls inside Day view; primary Calendar navigation remains fixed. */
internal class D5DayTimelineView private constructor(
    context: Context,
    attrs: AttributeSet?,
    private val model: DayRenderModel,
) : View(context, attrs) {
    private val day get() = model.day
    private val events get() = model.paint.events
    private val colors get() = model.paint.colors
    private val zone get() = model.paint.zone
    private val onEvent get() = model.onEvent

    // Android Layout Editor: a valid empty day view, without Activity assumptions.
    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : this(
        context,
        attrs,
        DayRenderModel(LocalDate.now(), CalendarEventPaint(emptyList(), emptyMap(), ZoneId.systemDefault()), {}),
    )

    constructor(activity: TenSevenCalendarActivity, model: DayRenderModel) : this(activity, null, model)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val targets = mutableListOf<Pair<RectF, D5R2Event>>()
    private val targetBounds = Array(CalendarDrawingSpecs.TIMELINE_EVENT_LIMIT) { RectF() }
    private val laneBuffers = Array(CalendarDrawingSpecs.TIMELINE_MAX_LANES) { LongArray(it + 1) }
    private val gutter = CalendarDrawingSpecs.TIMELINE_GUTTER_DP * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        targets.clear()
        val hourHeight = height / CalendarDrawingSpecs.TIMELINE_HOURS.toFloat()
        drawTimelineRuler(canvas, hourHeight)
        val beginning = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val ending =
            day
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val ordered = events.filter { it.start < ending && it.end > beginning }.sortedBy { it.start }
        val peak =
            (
                ordered.maxOfOrNull { first ->
                    ordered.count { other -> other.start < first.end && other.end > first.start }
                } ?: 1
            ).coerceIn(1, CalendarDrawingSpecs.TIMELINE_MAX_LANES)
        val laneEnd = laneBuffers[peak - 1].apply { fill(Long.MIN_VALUE) }
        ordered.take(CalendarDrawingSpecs.TIMELINE_EVENT_LIMIT).forEach { event ->
            val first = maxOf(event.start, beginning)
            val last = minOf(event.end, ending)
            if (last <= first) return@forEach
            val free = laneEnd.indexOfFirst { it <= first }
            val lane = if (free >= 0) free else laneEnd.indices.minByOrNull { laneEnd[it] } ?: 0
            laneEnd[lane] = last
            val y =
                (
                    (first - beginning).toFloat() / (ending - beginning) *
                        CalendarDrawingSpecs
                            .TIMELINE_HOURS
                ) * hourHeight
            val bottom =
                (
                    (last - beginning).toFloat() / (ending - beginning) *
                        CalendarDrawingSpecs
                            .TIMELINE_HOURS
                ) * hourHeight
            val fullWidth = (width - gutter - CALENDAR_DRAW_VALUE_7 * density).coerceAtLeast(CALENDAR_DRAW_RATIO_30_F)
            val laneWidth = fullWidth / peak.toFloat()
            val x = gutter + CALENDAR_DRAW_RATIO_4_F * density + lane * laneWidth
            val rect =
                targetBounds[targets.size].apply {
                    set(
                        x,
                        y + density,
                        x + laneWidth - 2f * density,
                        maxOf(y + CALENDAR_DRAW_RATIO_26_F * density, bottom - density),
                    )
                }
            drawTimelineEventBox(canvas, rect, event)
            targets.add(rect to event)
        }
        drawNowIndicator(canvas, hourHeight)
    }

    private fun drawNowIndicator(
        canvas: Canvas,
        hourHeight: Float,
    ) {
        if (day == LocalDate.now(zone)) {
            val now = java.time.LocalTime.now(zone)
            val y = (now.hour + now.minute / CalendarDrawingSpecs.TIMELINE_HOUR_MINUTES) * hourHeight
            paint.color = Color.rgb(0, CALENDAR_DRAW_RGB_169, CALENDAR_DRAW_RGB_238)
            paint.strokeWidth = 2f * density
            canvas.drawLine(gutter, y, width.toFloat(), y, paint)
        }
    }

    private fun drawTimelineRuler(
        canvas: Canvas,
        hourHeight: Float,
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = CALENDAR_DRAW_SP_11_F * density
        for (hour in 0..CalendarDrawingSpecs.TIMELINE_HOURS) {
            val y = hour * hourHeight
            paint.color = Color.rgb(CALENDAR_DRAW_RGB_50, CALENDAR_DRAW_RGB_57, CALENDAR_DRAW_RGB_66)
            paint.strokeWidth = density
            canvas.drawLine(gutter, y, width.toFloat(), y, paint)
            if (hour < CalendarDrawingSpecs.TIMELINE_HOURS) {
                paint.color = Color.rgb(CALENDAR_DRAW_RGB_180, CALENDAR_DRAW_RGB_192, CALENDAR_DRAW_RGB_205)
                canvas.drawText(
                    String.format(Locale.US, "%02d:00", hour),
                    gutter - CALENDAR_DRAW_RATIO_8_F * density,
                    y + CALENDAR_DRAW_RATIO_12_F * density,
                    paint,
                )
            }
        }
    }

    private fun drawTimelineEventBox(
        canvas: Canvas,
        rect: RectF,
        event: D5R2Event,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(CALENDAR_DRAW_RGB_24, CALENDAR_DRAW_RGB_31, CALENDAR_DRAW_RGB_40)
        canvas.drawRoundRect(rect, CALENDAR_DRAW_RATIO_4_F * density, CALENDAR_DRAW_RATIO_4_F * density, paint)
        paint.color = colors[event.calendarId] ?: Color.rgb(0, CALENDAR_DRAW_RGB_151, CALENDAR_DRAW_RGB_235)
        canvas.drawRect(rect.left, rect.top, rect.left + CALENDAR_DRAW_RATIO_3_F * density, rect.bottom, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = CALENDAR_DRAW_SP_11_F * density
        canvas.drawText(
            event.title.take(CALENDAR_DRAW_MAX_14),
            rect.left + CALENDAR_DRAW_RATIO_6_F * density,
            minOf(rect.bottom - CALENDAR_DRAW_RATIO_4_F * density, rect.top + CALENDAR_DRAW_RATIO_14_F * density),
            paint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                true
            }

            MotionEvent.ACTION_UP -> {
                targets.firstOrNull { it.first.contains(event.x, event.y) }?.second?.let(onEvent)
                performClick()
                true
            }

            else -> {
                super.onTouchEvent(event)
            }
        }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
