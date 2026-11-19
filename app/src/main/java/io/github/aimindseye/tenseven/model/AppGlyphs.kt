package io.github.aimindseye.tenseven.model

// Named, immutable icon accents. Values are the approved ARGB palette;
// no changes to icons, colors or app-to-glyph mapping.
private const val GLYPH_ACCENT_BLUE: Long = 0xFF4D9CFF
private const val GLYPH_ACCENT_ORANGE: Long = 0xFFF28C45
private const val GLYPH_ACCENT_GREEN: Long = 0xFF35C66B
private const val GLYPH_ACCENT_SLATE: Long = 0xFF64748B
private const val GLYPH_ACCENT_PURPLE: Long = 0xFF7A42E8
private const val GLYPH_ACCENT_MAGENTA: Long = 0xFFE8458F
private const val GLYPH_ACCENT_GOLD: Long = 0xFFF5C542
private const val GLYPH_ACCENT_TEAL: Long = 0xFF2BB5A0
private const val GLYPH_ACCENT_EMERALD: Long = 0xFF3FA37A
private const val GLYPH_ACCENT_CRIMSON: Long = 0xFFE34D59
private const val GLYPH_ACCENT_LILAC: Long = 0xFF9A63E8
private const val GLYPH_ACCENT_LIGHT_SLATE: Long = 0xFF94A3B8
private const val GLYPH_ACCENT_CHERRY: Long = 0xFFD32F45
private const val GLYPH_ACCENT_FOREST_GREEN: Long = 0xFF2E8B57
private const val GLYPH_ACCENT_OCHRE: Long = 0xFFD9A441
private const val GLYPH_ACCENT_VIOLET: Long = 0xFF8B5CF6
private const val GLYPH_ACCENT_CORAL: Long = 0xFFE5534B
private const val GLYPH_ACCENT_MUSTARD: Long = 0xFFE8B931
private const val GLYPH_ACCENT_CYAN: Long = 0xFF34C8E8

/**
 * One Start glyph per app the Q25 image ships (Q4 device review, icon style
 * Q7: one tile-free glyph set, Ripe-like). The glyph geometry is the Tabler
 * Icons outline set at the version pinned in third_party/tabler-icons (drawn in
 * ui/SableGlyphIcon.kt from the generated TablerGlyphPaths); [accent] tints the glyph's lower half and matches the
 * app's own launcher icon accent (ICON_MANIFEST.tsv) for Sable apps.
 */
enum class AppGlyph(
    /** The Tabler outline icon name, as in third_party/tabler-icons/icons/outline. */
    val tabler: String,
    /** ARGB accent. */
    val accent: Long,
) {
    PHONE("phone-call", GLYPH_ACCENT_BLUE),
    CONTACTS("address-book", GLYPH_ACCENT_ORANGE),
    MESSAGES("message-dots", GLYPH_ACCENT_GREEN),
    MAIL("mail", GLYPH_ACCENT_SLATE),
    CALENDAR("calendar", GLYPH_ACCENT_ORANGE),
    WEATHER("cloud", GLYPH_ACCENT_BLUE),
    PHOTOS("photo", GLYPH_ACCENT_PURPLE),
    MEDIA("music", GLYPH_ACCENT_PURPLE),
    CAMERA("camera", GLYPH_ACCENT_MAGENTA),

    /** The base camera beside Sable Camera: Aperture keeps video and the secure camera on the Q25. */
    VIDEO_CAMERA("video", GLYPH_ACCENT_MAGENTA),
    CALCULATOR("calculator", GLYPH_ACCENT_BLUE),
    FILES("folder", GLYPH_ACCENT_GOLD),
    CLOCK("clock", GLYPH_ACCENT_SLATE),
    BROWSER("world", GLYPH_ACCENT_BLUE),
    SETTINGS("settings", GLYPH_ACCENT_SLATE),
    RECORDER("microphone", GLYPH_ACCENT_CORAL),
    READER("book", GLYPH_ACCENT_GREEN),
    TEXT_READER("align-left", GLYPH_ACCENT_BLUE),
    NOTES("notes", GLYPH_ACCENT_GOLD),
    TASKS("clipboard-check", GLYPH_ACCENT_TEAL),
    TERMINAL("terminal-2", GLYPH_ACCENT_EMERALD),
    SUDOKU("grid-3x3", GLYPH_ACCENT_BLUE),
    MINESWEEPER("bomb", GLYPH_ACCENT_ORANGE),
    GAME_2048("layout-grid", GLYPH_ACCENT_SLATE),
    BRICKS("wall", GLYPH_ACCENT_CRIMSON),
    SNAKE("route", GLYPH_ACCENT_GREEN),
    BLOCKS("apps", GLYPH_ACCENT_LILAC),
    REVERSI("circle-half-2", GLYPH_ACCENT_LIGHT_SLATE),
    SOLITAIRE("cards", GLYPH_ACCENT_CHERRY),
    MAHJONG("stack-2", GLYPH_ACCENT_FOREST_GREEN),
    WORDS("letter-case", GLYPH_ACCENT_OCHRE),
    VAULT("lock", GLYPH_ACCENT_VIOLET),
    TOOLS("tool", GLYPH_ACCENT_CORAL),
    RADIO("antenna-bars-5", GLYPH_ACCENT_CORAL),
    KEYBOARD("keyboard", GLYPH_ACCENT_MUSTARD),
    DISPLAY("frame", GLYPH_ACCENT_CYAN),
    SETUP("list-check", GLYPH_ACCENT_TEAL),
    STORE("shopping-bag", GLYPH_ACCENT_GREEN),
    SERVICES("puzzle", GLYPH_ACCENT_BLUE),
}

/**
 * Package -> glyph, by package only: a label never picks a glyph, so the
 * LineageOS "Calendar" (Etar) can no longer borrow Sable Calendar's mark.
 * Apps not listed here draw their own icon ([AppGlyph] style: their themed
 * monochrome layer when they have one, else the icon itself, no tile).
 *
 * Pure Kotlin; unit tested in apps/r8/android/sablestart-presentation-check.
 */
object AppGlyphs {
    /** Canonical packages ([CoreAppIdentity.canonicalPackage]); LineageOS equivalents resolve through it. */
    val BY_PACKAGE: Map<String, AppGlyph> =
        mapOf(
            // TenSeven itself and common Android providers on the Titan 2.
            // Use the same outlined Tabler geometry as bundled apps when TenSeven style is enabled.
            "io.github.aimindseye.tenseven" to AppGlyph.SETTINGS,
            "com.whatsapp" to AppGlyph.MESSAGES,
            "com.whatsapp.w4b" to AppGlyph.MESSAGES,
            "com.google.android.apps.messaging" to AppGlyph.MESSAGES,
            "org.thoughtcrime.securesms" to AppGlyph.MESSAGES,
            "com.discord" to AppGlyph.MESSAGES,
            "com.google.android.gm" to AppGlyph.MAIL,
            "com.yahoo.mobile.client.android.mail" to AppGlyph.MAIL,
            "com.microsoft.office.outlook" to AppGlyph.MAIL,
            "com.google.android.youtube" to AppGlyph.VIDEO_CAMERA,
            "com.google.android.apps.youtube.music" to AppGlyph.MEDIA,
            "com.spotify.music" to AppGlyph.MEDIA,
            // Sable apps (product/q25/apps.tsv).
            "org.sableos.hub" to AppGlyph.MESSAGES,
            "org.sableos.messages" to AppGlyph.MESSAGES,
            "org.sableos.mail" to AppGlyph.MAIL,
            "org.sableos.calendar" to AppGlyph.CALENDAR,
            "org.sableos.weather" to AppGlyph.WEATHER,
            "org.sableos.media" to AppGlyph.MEDIA,
            "org.sableos.calculator" to AppGlyph.CALCULATOR,
            "org.sableos.reader" to AppGlyph.READER,
            "org.sableos.textreader" to AppGlyph.TEXT_READER,
            "org.sableos.notes" to AppGlyph.NOTES,
            "org.sableos.terminal" to AppGlyph.TERMINAL,
            "org.sableos.sudoku" to AppGlyph.SUDOKU,
            "org.sableos.minesweeper" to AppGlyph.MINESWEEPER,
            "org.sableos.game2048" to AppGlyph.GAME_2048,
            "org.sableos.bricks" to AppGlyph.BRICKS,
            "org.sableos.snake" to AppGlyph.SNAKE,
            "org.sableos.blocks" to AppGlyph.BLOCKS,
            "org.sableos.reversi" to AppGlyph.REVERSI,
            "org.sableos.solitaire" to AppGlyph.SOLITAIRE,
            "org.sableos.mahjong" to AppGlyph.MAHJONG,
            "org.sableos.words" to AppGlyph.WORDS,
            "org.sableos.tools" to AppGlyph.TOOLS,
            CoreAppIdentity.SABLE_CAMERA to AppGlyph.CAMERA,
            "org.sableos.titan2.vault" to AppGlyph.VAULT,
            "org.sableos.titan2.radiodiag" to AppGlyph.RADIO,
            "org.sableos.titan2.keyboard" to AppGlyph.KEYBOARD,
            "org.sableos.titan2.displaycompat" to AppGlyph.DISPLAY,
            "org.sableos.titan2.setup" to AppGlyph.SETUP,
            // LineageOS apps the image keeps (Aperture, Glimpse and Jelly via CoreAppIdentity).
            "com.android.dialer" to AppGlyph.PHONE,
            "com.android.contacts" to AppGlyph.CONTACTS,
            "com.android.messaging" to AppGlyph.MESSAGES,
            "com.android.settings" to AppGlyph.SETTINGS,
            CoreAppIdentity.PHOTOS to AppGlyph.PHOTOS,
            CoreAppIdentity.CAMERA to AppGlyph.VIDEO_CAMERA,
            CoreAppIdentity.BROWSER to AppGlyph.BROWSER,
            CoreAppIdentity.FILES to AppGlyph.FILES,
            CoreAppIdentity.CLOCK to AppGlyph.CLOCK,
            "org.lineageos.recorder" to AppGlyph.RECORDER,
            // microG Settings and Aurora Store (product/q25/microg).
            "com.google.android.gms" to AppGlyph.SERVICES,
            "com.aurora.store" to AppGlyph.STORE,
        )

    /**
     * The glyph for an activity: Tenseven's own extra apps by class (they share the launcher's package), else by
     * [packageName].
     */
    fun forComponent(
        packageName: String,
        className: String,
    ): AppGlyph? = ExtraApps.byClassName(className)?.glyph ?: forPackage(packageName)

    /** The glyph for [packageName], or null when the app draws its own icon. */
    fun forPackage(packageName: String): AppGlyph? = BY_PACKAGE[CoreAppIdentity.canonicalPackage(packageName)]
}
