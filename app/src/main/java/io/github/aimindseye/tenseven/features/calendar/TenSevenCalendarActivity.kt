package io.github.aimindseye.tenseven.features.calendar

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.aimindseye.tenseven.D5BbDialogs
import io.github.aimindseye.tenseven.model.D5ScreenFitPolicy
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class D5R2Calendar(
    val id: Long,
    val name: String,
    val color: Int,
    val account: String,
)

private data class D5R2Event(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
)

/** Read-only multi-calendar month + busy arcs + week schedule; creation delegated to Android provider UI. */
class TenSevenCalendarActivity : Activity() {
    private var selected = LocalDate.now()
    private var month = YearMonth.from(selected)

    // One view model for all approved screens; no stacked Month+Schedule page scrolling.
    private var mode = "Today"
    private var previousMode = "Today"
    private var calendars = listOf<D5R2Calendar>()
    private var events = listOf<D5R2Event>()
    private var loading = false
    private var loadGeneration = 0
    private val zone = ZoneId.systemDefault()

    // BB10 dark takes precedence over Android light/dark UI mode.
    private val bg get() = Color.rgb(3, 5, 8)
    private val fg get() = Color.rgb(243, 247, 250)
    private val muted get() = Color.rgb(160, 173, 186)
    private val accent get() = Color.rgb(0, 151, 235)

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

    private fun permission() = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun dateMs(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun column() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

    private fun row() =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun label(
        parent: LinearLayout,
        value: String,
        size: Float = 16f,
        bold: Boolean = false,
        color: Int = fg,
    ): TextView {
        val text =
            TextView(this).apply {
                this.text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(7), dp(10), dp(7))
            }
        parent.addView(text)
        return text
    }

    private val panel get() = Color.rgb(18, 22, 29)
    private val border get() = Color.rgb(49, 56, 64)

    private fun fit() = TenSevenSafeViewport.fit(this)

    private fun mondayStart() =
        getSharedPreferences("tenseven_calendar_view", MODE_PRIVATE)
            .getBoolean("monday_start", false)

    private fun weekStart(day: LocalDate = selected): LocalDate =
        day.minusDays(
            if (mondayStart()) {
                (day.dayOfWeek.value - 1).toLong()
            } else {
                (day.dayOfWeek.value % 7).toLong()
            },
        )

    private fun pill(
        value: String,
        active: Boolean = false,
        size: Float = 14f,
        onClick: () -> Unit,
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(if (active) Color.WHITE else fg)
            gravity = Gravity.CENTER
            setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            background =
                GradientDrawable().apply {
                    cornerRadius = dp(25).toFloat()
                    setColor(if (active) accent else panel)
                    setStroke(dp(1), if (active) accent else border)
                }
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
        }

    private fun button(
        parent: LinearLayout,
        name: String,
        action: () -> Unit,
    ) {
        val view = pill(name, onClick = action)
        parent.addView(
            view,
            LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            },
        )
    }

    private fun selectedIds(): Set<Long> {
        val prefs = getSharedPreferences("tenseven_calendars", MODE_PRIVATE)
        val saved = prefs.getStringSet("selected_ids", null)
        return if (saved == null) calendars.map { it.id }.toSet() else saved.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun storeIds(ids: Set<Long>) {
        getSharedPreferences(
            "tenseven_calendars",
            MODE_PRIVATE,
        ).edit().putStringSet("selected_ids", ids.map { it.toString() }.toSet()).apply()
    }

    private fun load() {
        val generation = ++loadGeneration
        if (!permission()) {
            calendars = emptyList()
            events = emptyList()
            loading = false
            return
        }
        val targetMonth = month
        loading = true
        // CalendarContract.Instances.query expands recurrence and can block; never run it on the UI thread.
        Thread {
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
                                    cursor.getString(3).orEmpty(),
                                ),
                            )
                        }
                    }
            } catch (_: Exception) {
                // Permission or provider change.
            }
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
                CalendarContract.Instances.query(contentResolver, columns, dateMs(from), dateMs(to.plusDays(1)))?.use { cursor ->
                    while (cursor.moveToNext() && loaded.size < 3000) {
                        loaded.add(
                            D5R2Event(
                                cursor.getLong(0),
                                cursor.getLong(1),
                                cursor.getString(2) ?: "(untitled)",
                                cursor.getLong(3),
                                cursor.getLong(4),
                                cursor.getInt(5) != 0,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                // Do not invent events when the provider fails.
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != loadGeneration) return@runOnUiThread
                calendars = found
                events = loaded
                loading = false
                render()
            }
        }.start()
    }

    private fun visibleEvents() = events.filter { it.calendarId in selectedIds() }

    private fun eventsOn(day: LocalDate): List<D5R2Event> {
        val start = dateMs(day)
        val end = dateMs(day.plusDays(1))
        return visibleEvents().filter { it.start < end && it.end > start }.sortedBy { it.start }
    }

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
            selected = selected.plusDays(if (mode == "Week") 7L * direction else direction.toLong())
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

    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun glyph(
        value: String,
        content: String,
        width: Int,
        action: () -> Unit,
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = 23f
            setTextColor(fg)
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
                3 -> selectMode("Day")
                4 -> selectMode("Calendars")
                5 -> selectMode("Settings")
                6 -> openHub()
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
        val root = column()
        val header = row().apply { setPadding(dp(3), 0, dp(3), 0) }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(if (fit().compact) 42 else 47)))
        val main = mode in listOf("Today", "Week", "Month", "Day")
        if (main) {
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
                    39,
                ) {
                    moveDate(-1)
                },
                LinearLayout.LayoutParams(dp(39), -1),
            )
            val heading =
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
                            )} – ${weekStart().plusDays(6).format(DateTimeFormatter.ofPattern("MMM d"))}"
                        } else {
                            selected.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                        }
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(fg)
                    gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener { datePicker() }
                    contentDescription = "Choose date"
                }
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
                    39,
                ) {
                    moveDate(1)
                },
                LinearLayout.LayoutParams(dp(38), -1),
            )
            header.addView(
                glyph("▤", "Select calendars", 39) { selectMode("Calendars") },
                LinearLayout.LayoutParams(dp(39), -1),
            )
            header.addView(
                glyph("⋮", "Calendar menu", 36) { settingsMenu() },
                LinearLayout.LayoutParams(dp(33), -1),
            )
        } else {
            header.addView(
                glyph("‹", "Back to Calendar", 40) { returnToMain() },
                LinearLayout.LayoutParams(dp(41), -1),
            )
            val heading =
                TextView(this).apply {
                    text =
                        when (mode) {
                            "Calendars" -> "Calendars"
                            "Settings" -> "Calendar settings"
                            else -> "New event"
                        }
                    textSize = 18f
                    setTextColor(fg)
                    gravity = Gravity.CENTER_VERTICAL
                }
            header.addView(heading, LinearLayout.LayoutParams(0, -1, 1f))
        }
        divider(root)
        val content = column()
        content.setPadding(dp(5), 0, dp(5), 0)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        if (mode == "Calendars") {
            renderCalendarSelection(content)
        } else if (mode == "Settings") {
            renderSettings(content)
        } else if (mode == "New") {
            renderEventComposer(content)
        } else if (!permission()) {
            label(content, "Allow Calendar access to show synced events.", 15f)
            val controls = row()
            content.addView(controls)
            button(controls, "Allow Calendars") { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 221) }
        } else {
            if (loading) label(content, "Loading calendars…", 12f, color = muted)
            when (mode) {
                "Month" -> renderMonthPage(content)
                "Week" -> renderWeekPage(content)
                "Day" -> renderDayPage(content)
                else -> renderTodayPage(content)
            }
        }
        if (main) {
            divider(root)
            val nav = row().apply { setPadding(dp(2), dp(1), dp(2), dp(1)) }
            root.addView(nav, LinearLayout.LayoutParams(-1, dp(fit().calendarNavDp)))
            listOf("Today", "Week", "Month", "Day", "+").forEach { destination ->
                val active = mode == destination
                val target =
                    TextView(this).apply {
                        text = if (destination == "+") "+" else destination
                        textSize =
                            if (destination == "+") {
                                22f
                            } else if (fit().compact) {
                                11f
                            } else {
                                13f
                            }
                        setTextColor(if (active) Color.WHITE else muted)
                        gravity = Gravity.CENTER
                        background =
                            if (active) {
                                GradientDrawable().apply {
                                    cornerRadius = dp(8).toFloat()
                                    setColor(accent)
                                }
                            } else {
                                null
                            }
                        contentDescription = if (destination == "+") "New event" else "$destination view"
                        isFocusable = true
                        setOnClickListener { if (destination == "+") openNewEvent() else selectMode(destination) }
                    }
                nav.addView(
                    target,
                    LinearLayout.LayoutParams(0, -1, 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    },
                )
            }
        }
        TenSevenSafeViewport.show(this, root)
    }

    private fun weekBar(body: LinearLayout) {
        val start = weekStart()
        val week = row().apply { setPadding(dp(1), dp(2), dp(1), dp(2)) }
        body.addView(week, LinearLayout.LayoutParams(-1, dp(fit().weekStripDp)))
        repeat(7) { i ->
            val day = start.plusDays(i.toLong())
            val active = day == selected
            val tile =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background =
                        if (active) {
                            GradientDrawable().apply {
                                cornerRadius = dp(6).toFloat()
                                setColor(accent)
                            }
                        } else {
                            null
                        }
                    isFocusable = true
                    contentDescription = "Select ${day.dayOfWeek} ${day.dayOfMonth}"
                    setOnClickListener { setDay(day) }
                }
            val letters = listOf("S", "M", "T", "W", "T", "F", "S")
            val position = if (mondayStart()) (i + 1) % 7 else i
            tile.addView(
                TextView(this).apply {
                    text = letters[position]
                    textSize = 10f
                    setTextColor(if (active) Color.WHITE else muted)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
            tile.addView(
                TextView(this).apply {
                    text = day.dayOfMonth.toString()
                    textSize = 15f
                    setTextColor(fg)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, 0, 2f),
            )
            val colors =
                eventsOn(day)
                    .mapNotNull { event ->
                        calendars.firstOrNull { it.id == event.calendarId }?.color
                    }.distinct()
                    .take(3)
            val dots = row().apply { gravity = Gravity.CENTER }
            colors.forEach { color ->
                dots.addView(
                    View(this).apply {
                        background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(color)
                            }
                    },
                    LinearLayout.LayoutParams(dp(4), dp(4)).apply { setMargins(dp(1), 0, dp(1), 0) },
                )
            }
            tile.addView(dots, LinearLayout.LayoutParams(-1, dp(7)))
            week.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
        }
        divider(body)
    }

    private fun agendaContainer(body: LinearLayout) {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val agenda = column()
        scroll.addView(agenda)
        renderAgenda(agenda)
    }

    private fun renderTodayPage(body: LinearLayout) {
        weekBar(body)
        agendaContainer(body)
    }

    private fun renderMonthPage(body: LinearLayout) {
        val weeks =
            (
                (
                    if (mondayStart()) {
                        month.atDay(1).dayOfWeek.value - 1
                    } else {
                        month.atDay(1).dayOfWeek.value % 7
                    }
                ) + month.lengthOfMonth() + 6
            ) / 7
        val height = 27 + fit().monthCellDp * weeks
        val grid =
            BusyMonthView(
                this,
                month,
                selected,
                visibleEvents(),
                calendars.associate { it.id to it.color },
                zone,
                mondayStart(),
                { setDay(it) },
                { direction -> moveDate(direction) },
            )
        body.addView(grid, LinearLayout.LayoutParams(-1, dp(height)))
        divider(body)
        // Compact week selector is kept small enough for today's events below the month.
        if (!fit().compact) weekBar(body)
        agendaContainer(body)
    }

    private fun renderWeekPage(body: LinearLayout) {
        weekBar(body)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = column()
        scroll.addView(list)
        val start = weekStart()
        repeat(7) { i ->
            val day = start.plusDays(i.toLong())
            val daily = eventsOn(day)
            val heading =
                label(
                    list,
                    "${day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}   ·   ${daily.size} events",
                    15f,
                    day == selected,
                    if (day == selected) accent else fg,
                )
            heading.setOnClickListener { setDay(day) }
            daily.take(5).forEach { event -> renderEventRow(list, event) }
            divider(list)
        }
    }

    private fun renderDayPage(body: LinearLayout) {
        weekBar(body)
        val allDay = eventsOn(selected).filter { it.allDay }
        if (allDay.isNotEmpty()) label(body, "ALL DAY  ${allDay.take(2).joinToString(" · ") { it.title }}", 12f, color = muted)
        val timelineScroll = ScrollView(this)
        body.addView(timelineScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val eventList = eventsOn(selected).filterNot { it.allDay }
        timelineScroll.addView(
            D5DayTimelineView(
                this,
                selected,
                eventList,
                calendars.associate { it.id to it.color },
                zone,
            ) { event ->
                showEvent(event, "Schedule", calendars.firstOrNull { it.id == event.calendarId }?.name.orEmpty())
            },
            LinearLayout.LayoutParams(-1, dp(24 * 50)),
        )
        val hour =
            if (selected == LocalDate.now(zone)) {
                java.time.LocalTime
                    .now(zone)
                    .hour
            } else {
                eventList.minOfOrNull { Instant.ofEpochMilli(it.start).atZone(zone).hour } ?: 8
            }
        timelineScroll.post { timelineScroll.scrollTo(0, dp((hour - 1).coerceIn(0, 22) * 50)) }
    }

    private fun renderEventRow(
        body: LinearLayout,
        event: D5R2Event,
    ) {
        val source = calendars.firstOrNull { it.id == event.calendarId }
        val whenText =
            if (event.allDay) {
                "All day"
            } else {
                Instant
                    .ofEpochMilli(event.start)
                    .atZone(zone)
                    .format(DateTimeFormatter.ofPattern("h:mm a"))
            }
        val r = row().apply { setPadding(dp(5), dp(4), dp(5), dp(4)) }
        body.addView(r, LinearLayout.LayoutParams(-1, dp(if (fit().compact) 43 else 51)))
        r.addView(
            View(this).apply { setBackgroundColor(source?.color ?: accent) },
            LinearLayout.LayoutParams(dp(4), -1),
        )
        val titles = column()
        r.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))
        label(titles, "$whenText   ${event.title}", 14f, true).setPadding(dp(9), 0, 0, 0)
        if (!fit().compact) label(titles, source?.name.orEmpty(), 11f, color = muted).setPadding(dp(9), 0, 0, 0)
        r.addView(
            TextView(this).apply {
                text = "›"
                textSize = 23f
                setTextColor(muted)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(22), -1),
        )
        r.setOnClickListener { showEvent(event, whenText, source?.name.orEmpty()) }
        divider(body)
    }

    private fun renderAgenda(body: LinearLayout) {
        label(body, selected.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())), 15f, true)
        val items = eventsOn(selected)
        if (items.isEmpty()) label(body, "No events today in the selected calendars", 13f, color = muted)
        items.forEach { renderEventRow(body, it) }
    }

    private fun renderCalendarSelection(body: LinearLayout) {
        if (!permission()) {
            label(body, "Allow Calendar access to select your calendars.", 15f)
            val actions = row()
            body.addView(actions)
            button(actions, "Allow Calendars") { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 221) }
            return
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = column()
        scroll.addView(list)
        val picked = selectedIds().toMutableSet()
        val grouped = calendars.groupBy { it.account.ifBlank { "On this device" } }
        grouped.forEach { (account, items) ->
            label(list, account, 14f, true, muted)
            items.forEach { source ->
                val item = row().apply { setPadding(dp(9), 0, dp(9), 0) }
                list.addView(item, LinearLayout.LayoutParams(-1, dp(49)))
                val toggle =
                    CheckBox(this).apply {
                        isChecked = source.id in picked
                        buttonTintList = ColorStateList.valueOf(accent)
                        contentDescription = "Show ${source.name} from $account"
                        setOnCheckedChangeListener { _, enabled ->
                            if (enabled) picked.add(source.id) else picked.remove(source.id)
                            storeIds(picked)
                        }
                    }
                item.addView(toggle, LinearLayout.LayoutParams(dp(42), -1))
                item.addView(
                    View(this).apply {
                        background =
                            GradientDrawable().apply {
                                cornerRadius = dp(3).toFloat()
                                setColor(source.color)
                            }
                    },
                    LinearLayout.LayoutParams(dp(13), dp(13)),
                )
                item.addView(
                    TextView(this).apply {
                        text = source.name
                        textSize = 15f
                        setTextColor(fg)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                        setOnClickListener { toggle.isChecked = !toggle.isChecked }
                    },
                    LinearLayout.LayoutParams(0, -1, 1f),
                )
                divider(list)
            }
        }
        if (calendars.isEmpty()) label(list, "No calendars available. Check account sync.", 14f, color = muted)
        val manage = label(list, "Manage synced accounts  ›", 15f, color = accent)
        manage.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
            } catch (
                _: Exception,
            ) {
                D5BbDialogs.message(this, "Accounts", "Open Android Settings > Accounts to manage calendar sync")
            }
        }
        // Done is anchored outside the scrolling calendar-account list.
        val done = label(body, "Done", 16f, true, accent)
        done.gravity = Gravity.CENTER
        done.setOnClickListener {
            load()
            returnToMain()
        }
    }

    private fun renderSettings(body: LinearLayout) {
        val scroll = ScrollView(this)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = column()
        scroll.addView(list)
        val prefs = getSharedPreferences("tenseven_calendar_view", MODE_PRIVATE)
        val defaultView = prefs.getString("default_view", "Today") ?: "Today"
        val default = label(list, "Default view                         $defaultView   ›", 15f)
        default.setOnClickListener {
            D5BbDialogs.choices(this, "Default view", listOf("Today", "Week", "Month", "Day")) { index ->
                prefs.edit().putString("default_view", listOf("Today", "Week", "Month", "Day")[index]).apply()
                render()
            }
        }
        divider(list)
        val week = label(list, "Week starts                  ${if (mondayStart()) "Monday" else "Sunday"}   ›", 15f)
        week.setOnClickListener {
            prefs.edit().putBoolean("monday_start", !mondayStart()).apply()
            render()
        }
        divider(list)
        val manage = label(list, "Calendars & accounts   ›", 15f)
        manage.setOnClickListener { selectMode("Calendars") }
        divider(list)
        label(list, "Theme                               BlackBerry dark", 15f)
        divider(list)
        label(
            list,
            "Calendar colors indicate the selected source; navigation and controls stay in BB10 blue, white and gray.",
            12f,
            color = muted,
        )
    }

    private fun renderEventComposer(body: LinearLayout) {
        val scroll = ScrollView(this)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = column()
        scroll.addView(form)
        val title =
            EditText(this).apply {
                hint = "Event title"
                setSingleLine(true)
                textSize = 17f
                setTextColor(fg)
                setHintTextColor(muted)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        form.addView(title, LinearLayout.LayoutParams(-1, dp(54)))
        divider(form)
        label(form, "${selected.format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy"))} · 10:00 AM", 15f)
        val source = calendars.firstOrNull { it.id in selectedIds() }
        label(form, "Calendar: ${source?.name ?: "Choose in Android Calendar"}", 15f)
        val location =
            EditText(this).apply {
                hint = "Location"
                setSingleLine(true)
                setTextColor(fg)
                setHintTextColor(muted)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        form.addView(location, LinearLayout.LayoutParams(-1, dp(54)))
        val notes =
            EditText(this).apply {
                hint = "Notes (optional)"
                setTextColor(fg)
                setHintTextColor(muted)
                minLines = 2
                maxLines = 3
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        form.addView(notes, LinearLayout.LayoutParams(-1, dp(95)))
        // The Save handoff is a fixed action, never below a long event form.
        val controls = row()
        body.addView(controls, LinearLayout.LayoutParams(-1, dp(53)))
        button(controls, "Continue to Android Calendar") {
            val intent =
                Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, title.text.toString().trim())
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, location.text.toString().trim())
                    .putExtra(CalendarContract.Events.DESCRIPTION, notes.text.toString().trim())
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dateMs(selected) + 10 * 3600_000L)
            source?.let { intent.putExtra(CalendarContract.Events.CALENDAR_ID, it.id) }
            try {
                startActivity(intent)
            } catch (
                _: Exception,
            ) {
                D5BbDialogs.message(this, "Calendar editor", "No Android Calendar editor is available to save this event")
            }
        }
        label(
            form,
            "Android Calendar confirms and saves the event; TenSeven currently has read-only calendar access.",
            12f,
            color = muted,
        )
    }

    private fun showEvent(
        event: D5R2Event,
        time: String,
        source: String,
    ) {
        D5BbDialogs.message(this, event.title, "$time\nCalendar: $source")
    }

    private fun chooseCalendars() {
        selectMode("Calendars")
    }

    private fun createEvent() {
        openNewEvent()
    }
}

/** Occupied-time arcs, one color per calendar, a real month grid with stable hit testing. */
private class BusyMonthView(
    activity: TenSevenCalendarActivity,
    private val month: YearMonth,
    private val selected: LocalDate,
    private val events: List<D5R2Event>,
    private val colors: Map<Long, Int>,
    private val zone: ZoneId,
    private val mondayStart: Boolean,
    private val onSelected: (LocalDate) -> Unit,
    private val onMonthSwipe: (Int) -> Unit,
) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val fg = Color.rgb(245, 248, 250)
    private val selectedColor = Color.rgb(0, 150, 232)
    private val outline = Color.rgb(67, 77, 88)
    private val offset = if (mondayStart) month.atDay(1).dayOfWeek.value - 1 else month.atDay(1).dayOfWeek.value % 7
    private val start = month.atDay(1).minusDays(offset.toLong())
    private val rows = (offset + month.lengthOfMonth() + 6) / 7
    private var touchX = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width.toFloat() / 7f
        val titleHeight = 32f * density
        val cellHeight = (height.toFloat() - titleHeight) / rows
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.style = Paint.Style.FILL
        paint.textSize = 12f * density
        paint.color = fg
        (
            if (mondayStart) {
                listOf("M", "T", "W", "T", "F", "S", "S")
            } else {
                listOf("S", "M", "T", "W", "T", "F", "S")
            }
        ).forEachIndexed { i, label ->
            canvas.drawText(label, cellWidth * (i + .5f), titleHeight * .7f, paint)
        }
        for (index in 0 until rows * 7) {
            val day = start.plusDays(index.toLong())
            if (day.month != month.month) continue
            val x = cellWidth * (index % 7 + .5f)
            val y = titleHeight + cellHeight * (index / 7 + .5f)
            val radius = minOf(cellWidth * .36f, cellHeight * .38f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.1f * density
            paint.color = outline
            canvas.drawCircle(x, y, radius, paint)
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd =
                day
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val period = (dayEnd - dayStart).coerceAtLeast(1L).toFloat()
            val matched = events.filter { it.start < dayEnd && it.end > dayStart }
            matched.take(24).forEachIndexed { order, event ->
                val first = ((maxOf(event.start, dayStart) - dayStart) / period).coerceIn(0f, 1f)
                val last = ((minOf(event.end, dayEnd) - dayStart) / period).coerceIn(0f, 1f)
                val sweep = maxOf(3f, (last - first) * 360f)
                val radiusLayer = radius - (order % 3) * 2.7f * density
                paint.color = colors[event.calendarId] ?: selectedColor
                paint.strokeWidth = 2.7f * density
                canvas.drawArc(
                    RectF(x - radiusLayer, y - radiusLayer, x + radiusLayer, y + radiusLayer),
                    -90f + first * 360f,
                    sweep,
                    false,
                    paint,
                )
            }
            if (day == selected) {
                paint.style = Paint.Style.FILL
                paint.color = selectedColor
                canvas.drawCircle(x, y, radius * .8f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.typeface = if (day == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textSize = 14f * density
            paint.color = if (day == selected) Color.WHITE else fg
            canvas.drawText(day.dayOfMonth.toString(), x, y + 5f * density, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            touchX = event.x
            return true
        }
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        val swipe = event.x - touchX
        if (kotlin.math.abs(swipe) > 65f * density) {
            onMonthSwipe(if (swipe < 0) 1 else -1)
            performClick()
            return true
        }
        val titleHeight = 32f * density
        val cellHeight = (height.toFloat() - titleHeight) / rows
        if (event.y < titleHeight) return true
        val col = (event.x / (width.toFloat() / 7f)).toInt().coerceIn(0, 6)
        val row = ((event.y - titleHeight) / cellHeight).toInt().coerceIn(0, rows - 1)
        val day = start.plusDays((row * 7 + col).toLong())
        if (day.month == month.month) onSelected(day)
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/** Single-day full timeline. Scrolls inside Day view; primary Calendar navigation remains fixed. */
private class D5DayTimelineView(
    activity: TenSevenCalendarActivity,
    private val day: LocalDate,
    private val events: List<D5R2Event>,
    private val colors: Map<Long, Int>,
    private val zone: ZoneId,
    private val onEvent: (D5R2Event) -> Unit,
) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val targets = mutableListOf<Pair<RectF, D5R2Event>>()
    private val gutter = 54f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        targets.clear()
        val hourHeight = height / 24f
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 11f * density
        for (hour in 0..24) {
            val y = hour * hourHeight
            paint.color = Color.rgb(50, 57, 66)
            paint.strokeWidth = density
            canvas.drawLine(gutter, y, width.toFloat(), y, paint)
            if (hour < 24) {
                paint.color = Color.rgb(180, 192, 205)
                canvas.drawText(
                    String.format(Locale.US, "%02d:00", hour),
                    gutter - 8f * density,
                    y + 12f * density,
                    paint,
                )
            }
        }
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
            ).coerceIn(1, 3)
        val laneEnd = LongArray(peak) { Long.MIN_VALUE }
        ordered.take(100).forEach { event ->
            val first = maxOf(event.start, beginning)
            val last = minOf(event.end, ending)
            if (last <= first) return@forEach
            val free = laneEnd.indexOfFirst { it <= first }
            val lane = if (free >= 0) free else laneEnd.indices.minByOrNull { laneEnd[it] } ?: 0
            laneEnd[lane] = last
            val y = ((first - beginning).toFloat() / (ending - beginning) * 24f) * hourHeight
            val bottom = ((last - beginning).toFloat() / (ending - beginning) * 24f) * hourHeight
            val fullWidth = (width - gutter - 7 * density).coerceAtLeast(30f)
            val laneWidth = fullWidth / peak.toFloat()
            val x = gutter + 4f * density + lane * laneWidth
            val rect =
                RectF(
                    x,
                    y + density,
                    x + laneWidth - 2f * density,
                    maxOf(y + 26f * density, bottom - density),
                )
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(24, 31, 40)
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint)
            paint.color = colors[event.calendarId] ?: Color.rgb(0, 151, 235)
            canvas.drawRect(rect.left, rect.top, rect.left + 3f * density, rect.bottom, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 11f * density
            canvas.drawText(
                event.title.take(14),
                rect.left + 6f * density,
                minOf(rect.bottom - 4f * density, rect.top + 14f * density),
                paint,
            )
            targets.add(rect to event)
        }
        if (day == LocalDate.now(zone)) {
            val now = java.time.LocalTime.now(zone)
            val y = (now.hour + now.minute / 60f) * hourHeight
            paint.color = Color.rgb(0, 169, 238)
            paint.strokeWidth = 2f * density
            canvas.drawLine(gutter, y, width.toFloat(), y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action == MotionEvent.ACTION_UP) {
            targets.firstOrNull { it.first.contains(event.x, event.y) }?.second?.let(onEvent)
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
