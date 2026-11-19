package io.github.aimindseye.tenseven.features.calendar

import java.time.LocalDate

/** Navigation and redraw requests stay owned by the Activity, including permission/load guards. */
internal data class CalendarContentActions(
    val setDay: (LocalDate) -> Unit,
    val moveDate: (Int) -> Unit,
    val selectMode: (String) -> Unit,
    val load: () -> Unit,
    val returnToMain: () -> Unit,
    val render: () -> Unit,
)
