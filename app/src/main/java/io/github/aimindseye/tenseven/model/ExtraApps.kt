package io.github.aimindseye.tenseven.model

enum class ExtraGroup { Tools }

/** One optional app that ships inside Tenseven; [className] is its launcher activity. */
data class ExtraApp(
    val id: String,
    val label: String,
    val summary: String,
    val className: String,
    val glyph: AppGlyph,
    val group: ExtraGroup,
)

/**
 * The six optional tools bundled with TenSeven. None is on by default. Setup lets the user turn each on;
 * an app that is off has no launcher entry. Pure policy, unit tested.
 */
object ExtraApps {
    private const val ROOT = "io.github.aimindseye.tenseven.extras."

    val ALL: List<ExtraApp> =
        listOf(
            ExtraApp(
                "tenseven_phone",
                "TenSeven Phone",
                "BlackBerry-style keypad and contacts with Android Phone handoff",
                "io.github.aimindseye.tenseven.features.phone.TenSevenPhoneActivity",
                AppGlyph.PHONE,
                ExtraGroup.Tools,
            ),
            ExtraApp(
                "tenseven_calendar",
                "TenSeven Calendar",
                "Month busy arcs, agenda and multiple synced calendars",
                "io.github.aimindseye.tenseven.features.calendar.TenSevenCalendarActivity",
                AppGlyph.CALENDAR,
                ExtraGroup.Tools,
            ),
            ExtraApp(
                "calculator",
                "Calc Plus",
                "Exact arithmetic, scientific functions and unit conversion",
                ROOT + "calculator.MainActivity",
                AppGlyph.CALCULATOR,
                ExtraGroup.Tools,
            ),
            ExtraApp(
                "tasks",
                "Tasks",
                "To-dos, due dates, repeating tasks and reminders; a separate app alongside Notes",
                "io.github.aimindseye.tenseven.tasks.TasksLauncherActivity",
                AppGlyph.TASKS,
                ExtraGroup.Tools,
            ),
            ExtraApp(
                "notes",
                "Notes",
                "Notes and checklists kept on the phone; share text into a new note",
                ROOT + "notes.MainActivity",
                AppGlyph.NOTES,
                ExtraGroup.Tools,
            ),
            ExtraApp(
                "profiles",
                "Sound Profiles",
                "BlackBerry sound profiles: Normal, Loud, Vibrate only, Phone calls only, All alerts off",
                ROOT + "profiles.MainActivity",
                AppGlyph.MEDIA,
                ExtraGroup.Tools,
            ),
        )

    private val byClass = ALL.associateBy { it.className }

    fun byClassName(className: String): ExtraApp? = byClass[className]

    fun isExtra(className: String): Boolean = className in byClass

    fun ofGroup(group: ExtraGroup): List<ExtraApp> = ALL.filter { it.group == group }

    /** Drops unknown ids; the default (nothing chosen) is the empty set. */
    fun normalize(ids: Collection<String>?): Set<String> = ids.orEmpty().filter { id -> ALL.any { it.id == id } }.toSet()

    /** What to switch on and off to move from [current] to [wanted], by id. */
    fun changes(
        current: Set<String>,
        wanted: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val target = normalize(wanted)
        val now = normalize(current)
        return (target - now) to (now - target)
    }
}
