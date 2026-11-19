package io.github.aimindseye.tenseven.features.calendar

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.aimindseye.tenseven.D5BbDialogs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Activity-bound CalendarScheduleContent renderer; lifecycle and mutable state remain with the activity. */
internal class CalendarScheduleContent(
    private val activity: TenSevenCalendarActivity,
    private val actions: CalendarContentActions,
) {
    private fun weekBar(body: LinearLayout) {
        val start = activity.weekStart()
        val week =
            activity.views.row().apply {
                setPadding(
                    activity.views.dp(CALENDAR_HAIRLINE_DP),
                    activity.views.dp(CALENDAR_MICRO_GUTTER_DP),
                    activity.views.dp(CALENDAR_HAIRLINE_DP),
                    activity.views.dp(CALENDAR_MICRO_GUTTER_DP),
                )
            }
        body.addView(week, LinearLayout.LayoutParams(-1, activity.views.dp(activity.views.fit().weekStripDp)))
        repeat(CALENDAR_WEEK_BAR_COUNT_7) { i ->
            val day = start.plusDays(i.toLong())
            renderWeekDayTile(week, day, i)
        }
        activity.views.divider(body)
    }

    private fun renderWeekDayTile(
        week: LinearLayout,
        day: LocalDate,
        i: Int,
    ) {
        val active = day == activity.selected
        val tile =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background =
                    if (active) {
                        GradientDrawable().apply {
                            cornerRadius = activity.views.dp(CALENDAR_WEEK_PILL_RADIUS_DP).toFloat()
                            setColor(activity.views.accent)
                        }
                    } else {
                        null
                    }
                isFocusable = true
                contentDescription = "Select ${day.dayOfWeek} ${day.dayOfMonth}"
                setOnClickListener { actions.setDay(day) }
            }
        val letters = listOf("S", "M", "T", "W", "T", "F", "S")
        val position = if (activity.mondayStart()) (i + 1) % CALENDAR_WEEK_BAR_VALUE_7 else i
        tile.addView(
            TextView(activity).apply {
                text = letters[position]
                textSize = CALENDAR_WEEK_BAR_SP_10_F
                setTextColor(if (active) Color.WHITE else activity.views.muted)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        tile.addView(
            TextView(activity).apply {
                text = String.format(Locale.getDefault(), "%d", day.dayOfMonth)
                textSize = CALENDAR_WEEK_BAR_SP_15_F
                setTextColor(activity.views.fg)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, 0, 2f),
        )
        renderWeekActivityDots(tile, day)
        week.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun renderWeekActivityDots(
        tile: LinearLayout,
        day: LocalDate,
    ) {
        val colors =
            activity
                .eventsOn(day)
                .mapNotNull { event ->
                    activity.calendars.firstOrNull { it.id == event.calendarId }?.color
                }.distinct()
                .take(CALENDAR_WEEK_BAR_MAX_3)
        val dots = activity.views.row().apply { gravity = Gravity.CENTER }
        colors.forEach { color ->
            dots.addView(
                View(activity).apply {
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(color)
                        }
                },
                LinearLayout
                    .LayoutParams(
                        activity.views.dp(CALENDAR_SMALL_GUTTER_DP),
                        activity.views.dp(CALENDAR_SMALL_GUTTER_DP),
                    ).apply {
                        setMargins(
                            activity.views.dp(CALENDAR_HAIRLINE_DP),
                            0,
                            activity.views.dp(CALENDAR_HAIRLINE_DP),
                            0,
                        )
                    },
            )
        }
        tile.addView(dots, LinearLayout.LayoutParams(-1, activity.views.dp(CALENDAR_LABEL_VERTICAL_INSET_DP)))
    }

    private fun agendaContainer(body: LinearLayout) {
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val agenda = activity.views.column()
        scroll.addView(agenda)
        renderAgenda(agenda)
    }

    fun renderTodayPage(body: LinearLayout) {
        weekBar(body)
        agendaContainer(body)
    }

    fun renderMonthPage(body: LinearLayout) {
        val weeks =
            (
                (
                    if (activity.mondayStart()) {
                        activity.month
                            .atDay(1)
                            .dayOfWeek.value - 1
                    } else {
                        activity.month
                            .atDay(1)
                            .dayOfWeek.value % CALENDAR_MONTH_DATE_7
                    }
                ) + activity.month.lengthOfMonth() + CALENDAR_MONTH_DATE_6
            ) / CALENDAR_MONTH_VALUE_7
        val height = CALENDAR_MONTH_VALUE_27 + activity.views.fit().monthCellDp * weeks
        val grid =
            BusyMonthView(
                activity,
                MonthRenderModel(
                    month = activity.month,
                    selected = activity.selected,
                    paint =
                        CalendarEventPaint(
                            activity.visibleEvents(),
                            activity.calendars.associate {
                                it.id to
                                    it.color
                            },
                            activity.zone,
                        ),
                    mondayStart = activity.mondayStart(),
                    onSelected = { actions.setDay(it) },
                    onMonthSwipe = { direction -> actions.moveDate(direction) },
                ),
            )
        body.addView(grid, LinearLayout.LayoutParams(-1, activity.views.dp(height)))
        activity.views.divider(body)
        // Compact week selector is kept small enough for today's events below the month.
        if (!activity.views.fit().compact) weekBar(body)
        agendaContainer(body)
    }

    fun renderWeekPage(body: LinearLayout) {
        weekBar(body)
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = activity.views.column()
        scroll.addView(list)
        val start = activity.weekStart()
        repeat(CALENDAR_WEEK_COUNT_7) { i ->
            val day = start.plusDays(i.toLong())
            val daily = activity.eventsOn(day)
            val heading =
                activity.views.label(
                    list,
                    "${day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}   ·   ${daily.size} events",
                    CALENDAR_WEEK_RATIO_15_F,
                    day == activity.selected,
                    if (day == activity.selected) activity.views.accent else activity.views.fg,
                )
            heading.setOnClickListener { actions.setDay(day) }
            daily.take(CALENDAR_WEEK_MAX_5).forEach { event -> renderEventRow(list, event) }
            activity.views.divider(list)
        }
    }

    fun renderDayPage(body: LinearLayout) {
        weekBar(body)
        val allDay = activity.eventsOn(activity.selected).filter { it.allDay }
        if (allDay.isNotEmpty()) {
            activity.views.label(
                body,
                "ALL DAY  ${allDay.take(2).joinToString(" · ") { it.title }}",
                CALENDAR_DAY_SP_12_F,
                color = activity.views.muted,
            )
        }
        val timelineScroll = ScrollView(activity)
        body.addView(timelineScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val eventList = activity.eventsOn(activity.selected).filterNot { it.allDay }
        timelineScroll.addView(
            D5DayTimelineView(
                activity,
                DayRenderModel(
                    day = activity.selected,
                    paint =
                        CalendarEventPaint(
                            eventList,
                            activity.calendars.associate { it.id to it.color },
                            activity.zone,
                        ),
                    onEvent = { event ->
                        showEvent(
                            event,
                            "Schedule",
                            activity.calendars
                                .firstOrNull { it.id == event.calendarId }
                                ?.name
                                .orEmpty(),
                        )
                    },
                ),
            ),
            LinearLayout.LayoutParams(-CALENDAR_DAY_DP_1, activity.views.dp(CALENDAR_DAY_DP_24 * CALENDAR_DAY_DP_50)),
        )
        scrollToDayHour(timelineScroll, eventList)
    }

    private fun scrollToDayHour(
        timelineScroll: ScrollView,
        eventList: List<D5R2Event>,
    ) {
        val hour =
            if (activity.selected == LocalDate.now(activity.zone)) {
                java.time.LocalTime
                    .now(activity.zone)
                    .hour
            } else {
                eventList.minOfOrNull { Instant.ofEpochMilli(it.start).atZone(activity.zone).hour }
                    ?: CALENDAR_DAY_VALUE_8
            }
        timelineScroll.post {
            timelineScroll.scrollTo(
                0,
                activity.views.dp(
                    (hour - 1).coerceIn(
                        0,
                        CALENDAR_DAY_DP_22,
                    ) * CALENDAR_DAY_DP_50,
                ),
            )
        }
    }

    private fun renderEventRow(
        body: LinearLayout,
        event: D5R2Event,
    ) {
        val source = activity.calendars.firstOrNull { it.id == event.calendarId }
        val whenText =
            if (event.allDay) {
                "All day"
            } else {
                Instant
                    .ofEpochMilli(event.start)
                    .atZone(activity.zone)
                    .format(DateTimeFormatter.ofPattern("h:mm a"))
            }
        val r =
            activity.views.row().apply {
                setPadding(
                    activity.views.dp(CALENDAR_COMPACT_GUTTER_DP),
                    activity.views.dp(CALENDAR_SMALL_GUTTER_DP),
                    activity.views.dp(CALENDAR_COMPACT_GUTTER_DP),
                    activity.views.dp(CALENDAR_SMALL_GUTTER_DP),
                )
            }
        body.addView(
            r,
            LinearLayout.LayoutParams(
                -CALENDAR_EVENT_ROW_DP_1,
                activity.views.dp(
                    if (activity.views.fit().compact) CALENDAR_EVENT_ROW_DP_43 else CALENDAR_EVENT_ROW_DP_51,
                ),
            ),
        )
        r.addView(
            View(activity).apply { setBackgroundColor(source?.color ?: activity.views.accent) },
            LinearLayout.LayoutParams(activity.views.dp(CALENDAR_SMALL_GUTTER_DP), -1),
        )
        val titles = activity.views.column()
        r.addView(titles, LinearLayout.LayoutParams(0, -1, 1f))
        activity.views
            .label(
                titles,
                "$whenText   ${event.title}",
                CALENDAR_EVENT_ROW_SP_14_F,
                true,
            ).setPadding(activity.views.dp(CALENDAR_LIST_ITEM_GUTTER_DP), 0, 0, 0)
        if (!activity.views.fit().compact) {
            activity.views
                .label(titles, source?.name.orEmpty(), CALENDAR_EVENT_ROW_SP_11_F, color = activity.views.muted)
                .setPadding(activity.views.dp(CALENDAR_LIST_ITEM_GUTTER_DP), 0, 0, 0)
        }
        r.addView(
            TextView(activity).apply {
                text = "›"
                textSize = CALENDAR_EVENT_ROW_SP_23_F
                setTextColor(activity.views.muted)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(activity.views.dp(CALENDAR_EVENT_DISCLOSURE_WIDTH_DP), -1),
        )
        r.setOnClickListener { showEvent(event, whenText, source?.name.orEmpty()) }
        activity.views.divider(body)
    }

    private fun renderAgenda(body: LinearLayout) {
        activity.views.label(
            body,
            activity.selected.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
            CALENDAR_AGENDA_SP_15_F,
            true,
        )
        val items = activity.eventsOn(activity.selected)
        if (items.isEmpty()) {
            activity.views.label(
                body,
                "No events today in the selected calendars",
                CALENDAR_AGENDA_SP_13_F,
                color = activity.views.muted,
            )
        }
        items.forEach { renderEventRow(body, it) }
    }

    private fun showEvent(
        event: D5R2Event,
        time: String,
        source: String,
    ) {
        D5BbDialogs.message(activity, event.title, "$time\nCalendar: $source")
    }
}

/** Preserves the provider instance overlap rule and stable start ordering used by every schedule view. */
internal fun calendarEventsOn(
    visibleEvents: List<D5R2Event>,
    day: LocalDate,
    zone: ZoneId,
): List<D5R2Event> {
    val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end =
        day
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    return visibleEvents.filter { it.start < end && it.end > start }.sortedBy { it.start }
}
