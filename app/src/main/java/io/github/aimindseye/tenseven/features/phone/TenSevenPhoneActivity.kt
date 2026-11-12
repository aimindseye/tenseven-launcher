package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.D5BbDialogs
import io.github.aimindseye.tenseven.D5PhoneRoleRequestActivity
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val PHONE_BLOCKLIST_MAX_25 = 25
private const val PHONE_BLOCKLIST_VALUE_7 = 7
private const val PHONE_CONTACTS_VALUE_14 = 14
private const val PHONE_CONTACT_DETAILS_SP_23_F = 23f
private const val PHONE_CONTACT_DETAILS_VALUE_10 = 10
private const val PHONE_CONTACT_DETAILS_VALUE_78 = 78
private const val PHONE_DETAIL_HEADER_RATIO_24_F = 24f
private const val PHONE_DETAIL_HEADER_SP_16_F = 16f
private const val PHONE_DIAL_DP_1 = 1
private const val PHONE_DIAL_DP_43 = 43
private const val PHONE_DIAL_DP_52 = 52
private const val PHONE_DIAL_RATIO_25_F = 25f
private const val PHONE_DIAL_SP_24_F = 24f
private const val PHONE_DIAL_SP_28_F = 28f
private const val PHONE_FLOATING_RATIO_28_F = 28f
private const val PHONE_INFO_ROW_DP_2 = 2
private const val PHONE_KEYPAD_DP_126 = 126
private const val PHONE_KEYPAD_DP_145 = 145
private const val PHONE_KEYPAD_DP_8 = 8
private const val PHONE_KEYPAD_RATIO_9_F = 9f
private const val PHONE_KEYPAD_SP_10_F = 10f
private const val PHONE_KEYPAD_SP_23_F = 23f
private const val PHONE_KEYPAD_SP_9_F = 9f
private const val PHONE_KEYPAD_VALUE_15 = 15
private const val PHONE_KEYPAD_VALUE_16 = 16
private const val PHONE_KEYPAD_VALUE_24 = 24
private const val PHONE_KEYPAD_VALUE_3 = 3
private const val PHONE_MATCHES_DP_1 = 1
private const val PHONE_MATCHES_DP_2 = 2
private const val PHONE_MATCHES_DP_36 = 36
private const val PHONE_MATCHES_DP_37 = 37
private const val PHONE_MATCHES_DP_43 = 43
private const val PHONE_MATCHES_DP_44 = 44
private const val PHONE_MATCHES_SP_11_F = 11f
private const val PHONE_MATCHES_VALUE_32 = 32
private const val PHONE_OPEN_MENU_VALUE_3 = 3
private const val PHONE_POPULATE_VALUE_36 = 36
private const val PHONE_QUICK_ACTION_RATIO_23_F = 23f
private const val PHONE_RECENTS_DP_1 = 1
private const val PHONE_RECENTS_DP_2 = 2
private const val PHONE_RECENTS_RATIO_25_F = 25f
private const val PHONE_RECENTS_SP_11_F = 11f
private const val PHONE_RECENTS_SP_21_F = 21f
private const val PHONE_RECENTS_SP_24_F = 24f
private const val PHONE_RECENTS_VALUE_15 = 15
private const val PHONE_RECENTS_VALUE_16 = 16

/**
 * D5-R9.1: FULL REWRITE of the Phone activity's presentation.
 * Approved contract: one top Dial / Contacts / Recents navigation; fixed Dial call action;
 * inline number matches; A–Z contacts; filters, voicemail, photos and top-level block action.
 *
 * This file does not own Android call state. TenSevenInCallService remains in TenSevenTelecom.kt.
 * It does not change Android default dialer roles without an explicit user action.
 */
class TenSevenPhoneActivity : ComponentActivity() {
    // D5 approved Phone layout tokens: preserve exact Titan 2 spacing and typography.
    private companion object {
        // Remaining D5-approved Phone dimensions; retain exact original spacing.
        const val PHONE_AUXILIARY_INSET_DP = 15
        const val PHONE_DETAIL_CAPTION_HEIGHT_DP = 21
        const val PHONE_MINI_ICON_EDGE_DP = 24
        const val PHONE_CONTACT_LETTER_HEADING_DP = 26
        const val PHONE_LIST_FOOTER_INSET_DP = 29
        const val PHONE_COMPACT_AVATAR_EDGE_DP = 32
        const val PHONE_OPTIONS_ICON_EDGE_DP = 35
        const val PHONE_CONTACT_AVATAR_EDGE_DP = 36
        const val PHONE_COMPACT_MATCH_HEIGHT_DP = 37
        const val PHONE_CALL_TYPE_ICON_WIDTH_DP = 39
        const val PHONE_BLOCK_ACTION_HEIGHT_DP = 41
        const val PHONE_SEARCH_AND_ACTION_HEIGHT_DP = 42
        const val PHONE_COMPACT_NAVIGATION_HEIGHT_DP = 43
        const val PHONE_STANDARD_NAVIGATION_HEIGHT_DP = 44
        const val PHONE_MENU_ACTION_HEIGHT_DP = 46
        const val PHONE_MENU_ROW_HEIGHT_DP = 47
        const val PHONE_CONTACT_OR_MENU_ROW_DP = 49
        const val PHONE_FLOATING_ACTION_EDGE_DP = 51
        const val PHONE_EXPANDED_INPUT_HEIGHT_DP = 52
        const val PHONE_SEARCH_BAND_HEIGHT_DP = 54
        const val PHONE_VOICEMAIL_ACTION_HEIGHT_DP = 60
        const val PHONE_RECENT_CALL_ROW_HEIGHT_DP = 62
        const val PHONE_ALPHABET_SCROLL_MARGIN_DP = 64
        const val PHONE_CONTACT_LIST_BOTTOM_INSET_DP = 70
        const val PHONE_CONTACT_QUICK_ACTION_HEIGHT_DP = 74
        const val PHONE_RECENTS_BOTTOM_INSET_DP = 75
        const val PHONE_CONTACT_PHOTO_EDGE_DP = 78
        const val PHONE_CONTACT_PHOTO_AREA_HEIGHT_DP = 86
        const val PHONE_HAIRLINE_DP = 1
        const val PHONE_MICRO_INSET_DP = 2
        const val PHONE_EXTRA_TIGHT_INSET_DP = 3
        const val PHONE_TIGHT_INSET_DP = 4
        const val PHONE_COMPACT_INSET_DP = 5
        const val PHONE_SMALL_INSET_DP = 6
        const val PHONE_NARROW_GUTTER_DP = 7
        const val PHONE_STANDARD_INSET_DP = 8
        const val PHONE_ROW_CONTENT_GUTTER_DP = 9
        const val PHONE_SECTION_INSET_DP = 10
        const val PHONE_LIST_CONTROL_INSET_DP = 11
        const val PHONE_PAGE_CONTENT_INSET_DP = 12
        const val PHONE_LIST_CONTENT_INSET_DP = 13
        const val PHONE_DETAIL_CONTENT_INSET_DP = 14
        const val PHONE_CALLOUT_CONTENT_INSET_DP = 16
        const val PHONE_CALLOUT_TOP_INSET_DP = 18
        const val PHONE_BODY_TEXT_SP = 15f
        const val PHONE_CAPTION_TEXT_SP = 12f
        const val PHONE_SECONDARY_TEXT_SP = 14f
        const val PHONE_COMPACT_TEXT_SP = 13f
        const val PHONE_SYMBOL_TEXT_SP = 22f
        const val PHONE_LARGE_SYMBOL_TEXT_SP = 29f
        const val PHONE_REQUEST_CONTACTS = 201
        const val PHONE_REQUEST_CALL_LOG = 202
        const val PHONE_REQUEST_CALL = 203
    }

    private enum class Screen { DIAL, CONTACTS, RECENTS, CONTACT_DETAIL, BLOCKLIST }

    private var screen = Screen.DIAL
    private var returnTo = Screen.CONTACTS
    private var selectedContactId: Long? = null
    private var dialNumber = ""
    private var contactSearch = ""
    private var callFilter = "All"
    private var queuedNumber: String? = null
    private val colors by lazy { PhonePalette(this) }
    private val data by lazy { TenSevenPhoneData(this) }

    private fun px(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!navigatePhoneBack()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
        if (savedInstanceState != null) {
            dialNumber = savedInstanceState.getString("dial", "")
            contactSearch = savedInstanceState.getString("contactSearch", "")
            callFilter = savedInstanceState.getString("callFilter", "All")
            screen = Screen.entries.firstOrNull { it.name == savedInstanceState.getString("screen") } ?: Screen.DIAL
            selectedContactId = savedInstanceState.getLong("contactId", 0L).takeIf { it > 0L }
        }
        readIntent(intent)
        draw()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        draw()
    }

    override fun onResume() {
        super.onResume()
        // Contacts may have changed in the system editor or Android role selection.
        draw()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("dial", dialNumber)
        outState.putString("contactSearch", contactSearch)
        outState.putString("callFilter", callFilter)
        outState.putString("screen", screen.name)
        outState.putLong("contactId", selectedContactId ?: 0L)
    }

    private fun navigatePhoneBack(): Boolean =
        when (screen) {
            Screen.CONTACT_DETAIL -> {
                screen = returnTo
                selectedContactId = null
                draw()
                true
            }

            Screen.BLOCKLIST -> {
                screen = Screen.RECENTS
                draw()
                true
            }

            else -> {
                false
            }
        }

    private fun readIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL) {
            dialNumber = intent.data?.schemeSpecificPart.orEmpty()
            screen = Screen.DIAL
        }
    }

    private fun column() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.bg)
        }

    private fun row() =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun label(
        value: String,
        size: Float = PHONE_BODY_TEXT_SP,
        color: Int = colors.fg,
        bold: Boolean = false,
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            maxLines = 2
        }

    private fun rule(parent: LinearLayout) =
        parent.addView(
            View(this).apply { setBackgroundColor(colors.line) },
            LinearLayout.LayoutParams(
                -1,
                px(PHONE_HAIRLINE_DP),
            ),
        )

    private fun round(
        color: Int,
        radius: Int = 12,
        stroke: Boolean = false,
    ) = GradientDrawable().apply {
        cornerRadius = px(radius).toFloat()
        setColor(color)
        if (stroke) setStroke(px(PHONE_HAIRLINE_DP), this@TenSevenPhoneActivity.colors.line)
    }

    private fun circular(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun clickLabel(
        text: String,
        description: String = text,
        size: Float = PHONE_BODY_TEXT_SP,
        action: () -> Unit,
    ): TextView =
        label(text, size).apply {
            gravity = Gravity.CENTER
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun notice(
        body: LinearLayout,
        message: String,
        action: String? = null,
        onClick: (() -> Unit)? = null,
    ) {
        val note = label(message, PHONE_SECONDARY_TEXT_SP, colors.muted)
        note.setPadding(
            px(PHONE_CALLOUT_CONTENT_INSET_DP),
            px(PHONE_CALLOUT_TOP_INSET_DP),
            px(PHONE_CALLOUT_CONTENT_INSET_DP),
            px(PHONE_DETAIL_CONTENT_INSET_DP),
        )
        body.addView(note)
        if (action != null && onClick != null) {
            val go =
                clickLabel(action, action, PHONE_BODY_TEXT_SP, onClick).apply {
                    setTextColor(colors.accent)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        px(PHONE_CALLOUT_CONTENT_INSET_DP),
                        px(PHONE_SMALL_INSET_DP),
                        px(PHONE_CALLOUT_CONTENT_INSET_DP),
                        px(PHONE_PAGE_CONTENT_INSET_DP),
                    )
                }
            body.addView(go)
        }
    }

    private fun draw() {
        TenSevenSafeViewport.darkSystemBars(this)
        val shell = column()
        if (screen == Screen.CONTACT_DETAIL || screen == Screen.BLOCKLIST) {
            detailHeader(shell)
        } else {
            tabs(shell)
        }
        val body = FrameLayout(this)
        shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        when (screen) {
            Screen.DIAL -> dial(body)
            Screen.CONTACTS -> contacts(body)
            Screen.RECENTS -> recents(body)
            Screen.CONTACT_DETAIL -> contactDetails(body)
            Screen.BLOCKLIST -> blocklist(body)
        }
        TenSevenSafeViewport.show(this, shell)
        Log.i("TenSevenD5R9R1", "Phone rebuild active: ${screen.name}")
    }

    /** The only main navigation bar; no redundant 'TenSeven Phone' heading. */
    private fun tabs(shell: LinearLayout) {
        val navigation = row()
        shell.addView(navigation, LinearLayout.LayoutParams(-1, px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP)))
        val tabRegion = row()
        navigation.addView(tabRegion, LinearLayout.LayoutParams(0, -1, 1f))
        listOf(
            Triple("Dial", Screen.DIAL, "Dial"),
            Triple("Contacts", Screen.CONTACTS, "Contacts"),
            Triple("Recents", Screen.RECENTS, "Recents"),
        ).forEach { (title, target, description) ->
            val selected = screen == target
            val tab =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    contentDescription = "Open $description"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        screen = target
                        draw()
                    }
                }
            tab.addView(
                label(title, PHONE_BODY_TEXT_SP, if (selected) colors.fg else colors.muted).apply {
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
            tab.addView(
                View(this).apply { setBackgroundColor(if (selected) colors.accent else colors.bg) },
                LinearLayout.LayoutParams(-1, px(PHONE_EXTRA_TIGHT_INSET_DP)),
            )
            tabRegion.addView(tab, LinearLayout.LayoutParams(0, -1, 1f))
        }
        navigation.addView(
            clickLabel("⋮", "Phone options", PHONE_SYMBOL_TEXT_SP) { openMenu() },
            LinearLayout.LayoutParams(px(PHONE_OPTIONS_ICON_EDGE_DP), -1),
        )
        rule(shell)
    }

    private fun detailHeader(shell: LinearLayout) {
        val head = row().apply { setPadding(px(PHONE_NARROW_GUTTER_DP), 0, px(PHONE_STANDARD_INSET_DP), 0) }
        shell.addView(head, LinearLayout.LayoutParams(-1, px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)))
        head.addView(
            clickLabel("‹", "Back", PHONE_LARGE_SYMBOL_TEXT_SP) {
                onBackPressedDispatcher.onBackPressed()
            },
            LinearLayout.LayoutParams(px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP), -1),
        )
        head.addView(
            label(if (screen == Screen.BLOCKLIST) "Spam & Blocklist" else "", PHONE_DETAIL_HEADER_SP_16_F),
            LinearLayout.LayoutParams(0, -1, 1f),
        )
        if (screen == Screen.CONTACT_DETAIL) {
            val favorite = selectedContactId?.let { id -> data.people().firstOrNull { it.id == id }?.favorite } == true
            head.addView(
                clickLabel(if (favorite) "★" else "☆", "Edit favorite in Contacts", PHONE_DETAIL_HEADER_RATIO_24_F) {
                    selectedContactId?.let(::openEditor)
                },
                LinearLayout.LayoutParams(px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP), -1),
            )
        }
        head.addView(
            clickLabel("⋮", "Phone options", PHONE_SYMBOL_TEXT_SP) { openMenu() },
            LinearLayout.LayoutParams(px(PHONE_SEARCH_AND_ACTION_HEIGHT_DP), -1),
        )
        rule(shell)
    }

    /** Dial content has no ScrollView; the keypad stretches only inside the inset-safe window. */
    private fun dial(frame: FrameLayout) {
        val metrics = windowManager.currentWindowMetrics
        val safe =
            metrics.windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val density = resources.displayMetrics.density
        val widthDp = (metrics.bounds.width() - safe.left - safe.right) / density
        val heightDp = (metrics.bounds.height() - safe.top - safe.bottom) / density
        val size = PhoneUiRules.bounds(widthDp, heightDp)
        val panel =
            column().apply {
                setPadding(
                    px(PHONE_SECTION_INSET_DP),
                    px(PHONE_EXTRA_TIGHT_INSET_DP),
                    px(PHONE_SECTION_INSET_DP),
                    px(PHONE_COMPACT_INSET_DP),
                )
            }
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))

        val input = renderDialInput(panel, size)
        val matches = column()
        panel.addView(matches, LinearLayout.LayoutParams(-1, 0))
        val people = if (isGranted(Manifest.permission.READ_CONTACTS)) data.people() else emptyList()

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    updateDialMatches(s?.toString().orEmpty(), people, size, matches, input)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        updateDialMatches(dialNumber, people, size, matches, input)

        val keypad = column()
        panel.addView(keypad, LinearLayout.LayoutParams(-1, 0, 1f))
        renderDialKeypad(keypad, input, size)
        renderDialCallAction(panel, input, size)
    }

    private fun renderDialInput(
        panel: LinearLayout,
        size: PhoneUiRules.Bounds,
    ): EditText {
        val numberRow = row()
        panel.addView(
            numberRow,
            LinearLayout.LayoutParams(
                -PHONE_DIAL_DP_1,
                px(
                    if (size
                            .compact
                    ) {
                        PHONE_DIAL_DP_43
                    } else {
                        PHONE_DIAL_DP_52
                    },
                ),
            ),
        )
        val input =
            EditText(this).apply {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_PHONE
                showSoftInputOnFocus = false // Hardware QWERTY and touch keys both remain available.
                textSize = if (size.compact) PHONE_DIAL_SP_24_F else PHONE_DIAL_SP_28_F
                gravity = Gravity.CENTER
                setTextColor(colors.fg)
                setHintTextColor(colors.muted)
                hint = "Enter number"
                background = null
                setText(dialNumber)
                setSelection(text.length)
            }
        numberRow.addView(input, LinearLayout.LayoutParams(0, -1, 1f))
        numberRow.addView(
            clickLabel("⌫", "Delete last digit", PHONE_DIAL_RATIO_25_F) {
                if (input.text.isNotEmpty()) input.text.delete(input.length() - 1, input.length())
            }.apply {
                setOnLongClickListener {
                    input.text.clear()
                    true
                }
            },
            LinearLayout.LayoutParams(px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP), -1),
        )
        rule(panel)

        return input
    }

    private fun updateDialMatches(
        text: String,
        people: List<TenSevenPhoneData.Person>,
        size: PhoneUiRules.Bounds,
        matches: LinearLayout,
        input: EditText,
    ) {
        dialNumber = text
        matches.removeAllViews()
        val results =
            PhoneUiRules.dialMatches(
                text,
                people.map {
                    PhoneUiRules.Match(it.id, it.name, it.number)
                },
                size.maxMatches,
            )
        // No blank reserved match panel when the number matches nobody.
        matches.layoutParams =
            LinearLayout.LayoutParams(
                -PHONE_MATCHES_DP_1,
                px(results.size * (if (size.compact) PHONE_MATCHES_DP_37 else PHONE_MATCHES_DP_44)),
            )
        results.forEach { match ->
            addDialMatchRow(matches, input, size, match)
        }
    }

    private fun addDialMatchRow(
        matches: LinearLayout,
        input: EditText,
        size: PhoneUiRules.Bounds,
        match: PhoneUiRules.Match,
    ) {
        val cell =
            row().apply {
                setPadding(
                    px(PHONE_SMALL_INSET_DP),
                    px(PHONE_MICRO_INSET_DP),
                    px(PHONE_NARROW_GUTTER_DP),
                    px(PHONE_MICRO_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "Dial ${match.name} ${match.number}"
                setOnClickListener {
                    input.setText(match.number)
                    input.setSelection(input.length())
                }
            }
        matches.addView(
            cell,
            LinearLayout.LayoutParams(
                -PHONE_MATCHES_DP_1,
                px(if (size.compact) PHONE_MATCHES_DP_36 else PHONE_MATCHES_DP_43),
            ),
        )
        cell.addView(
            TenSevenContactPhotos.avatar(this, match.contactId, match.name, PHONE_MATCHES_VALUE_32, colors),
            LinearLayout.LayoutParams(px(PHONE_COMPACT_AVATAR_EDGE_DP), px(PHONE_COMPACT_AVATAR_EDGE_DP)),
        )
        val names =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(PHONE_ROW_CONTENT_GUTTER_DP), 0, 0, 0)
            }
        cell.addView(names, LinearLayout.LayoutParams(0, -PHONE_MATCHES_DP_2, 1f))
        names.addView(label(match.name, PHONE_COMPACT_TEXT_SP, bold = true))
        names.addView(label(match.number, PHONE_MATCHES_SP_11_F, colors.muted))
        cell.addView(label("›", PHONE_SYMBOL_TEXT_SP, colors.muted))
        rule(matches)
    }

    private fun renderDialKeypad(
        keypad: LinearLayout,
        input: EditText,
        size: PhoneUiRules.Bounds,
    ) {
        val digits =
            listOf(
                "1" to "",
                "2" to "ABC",
                "3" to "DEF",
                "4" to "GHI",
                "5" to "JKL",
                "6" to "MNO",
                "7" to "PQRS",
                "8" to "TUV",
                "9" to "WXYZ",
                "*" to "",
                "0" to "+",
                "#" to "",
            )
        val keyVisual = DialKeyVisual(input, size)
        digits.chunked(PHONE_KEYPAD_VALUE_3).forEach { trio ->
            val keyRow = row()
            keypad.addView(keyRow, LinearLayout.LayoutParams(-1, 0, 1f))
            trio.forEachIndexed { index, (digit, letters) ->
                addDialKey(keyRow, digit, letters, index, keyVisual)
            }
            rule(keypad)
        }
    }

    private data class DialKeyVisual(
        val input: EditText,
        val size: PhoneUiRules.Bounds,
    )

    private fun addDialKey(
        keyRow: LinearLayout,
        digit: String,
        letters: String,
        index: Int,
        visual: DialKeyVisual,
    ) {
        val input = visual.input
        val size = visual.size
        val key =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "$digit ${letters.ifEmpty { "key" }}"
                setOnClickListener { input.append(digit) }
                if (digit == "0") {
                    setOnLongClickListener {
                        input.append("+")
                        true
                    }
                }
            }
        keyRow.addView(key, LinearLayout.LayoutParams(0, -1, 1f))
        key.addView(
            label(digit, if (size.compact) PHONE_KEYPAD_SP_23_F else PHONE_LARGE_SYMBOL_TEXT_SP)
                .apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, 0, 2f),
        )
        key.addView(
            label(
                letters,
                if (size.compact) PHONE_KEYPAD_SP_9_F else PHONE_KEYPAD_SP_10_F,
                colors.muted,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        if (index < 2) {
            keyRow.addView(
                View(this).apply { setBackgroundColor(colors.line) },
                LinearLayout.LayoutParams(px(PHONE_HAIRLINE_DP), -1),
            )
        }
    }

    private fun renderDialCallAction(
        panel: LinearLayout,
        input: EditText,
        size: PhoneUiRules.Bounds,
    ) {
        val callRow = row().apply { gravity = Gravity.CENTER }
        panel.addView(callRow, LinearLayout.LayoutParams(-1, px(size.primaryCallHeightDp)))
        val call =
            clickLabel("✆", "Place call", PHONE_LARGE_SYMBOL_TEXT_SP) {
                dialNumber = input.text.toString()
                placeCall(dialNumber)
            }.apply {
                background = round(colors.success, PHONE_KEYPAD_VALUE_24)
                setTextColor(Color.WHITE)
            }
        callRow.addView(
            call,
            LinearLayout.LayoutParams(
                px(if (size.compact) PHONE_KEYPAD_DP_126 else PHONE_KEYPAD_DP_145),
                px(size.primaryCallHeightDp - PHONE_KEYPAD_DP_8),
            ),
        )
    }

    private fun appendPhoneContactRow(
        list: LinearLayout,
        person: TenSevenPhoneData.Person,
    ) {
        val entry =
            row().apply {
                setPadding(
                    px(PHONE_PAGE_CONTENT_INSET_DP),
                    px(PHONE_TIGHT_INSET_DP),
                    px(PHONE_STANDARD_INSET_DP),
                    px(PHONE_TIGHT_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "Open ${person.name}"
                setOnClickListener { openContact(person.id, Screen.CONTACTS) }
            }
        list.addView(entry, LinearLayout.LayoutParams(-1, px(PHONE_CONTACT_OR_MENU_ROW_DP)))
        entry.addView(
            TenSevenContactPhotos.avatar(this, person.id, person.name, PHONE_POPULATE_VALUE_36, colors),
            LinearLayout.LayoutParams(px(PHONE_CONTACT_AVATAR_EDGE_DP), px(PHONE_CONTACT_AVATAR_EDGE_DP)),
        )
        entry.addView(
            label(person.name, PHONE_BODY_TEXT_SP).apply {
                setPadding(
                    px(PHONE_PAGE_CONTENT_INSET_DP),
                    0,
                    0,
                    0,
                )
            },
            LinearLayout.LayoutParams(0, -1, 1f),
        )
        rule(list)
    }

    private fun contacts(frame: FrameLayout) {
        val container = column()
        frame.addView(container, FrameLayout.LayoutParams(-1, -1))
        if (!isGranted(Manifest.permission.READ_CONTACTS)) {
            notice(
                container,
                "Allow Contacts to show people stored on this device. No contact information is uploaded.",
                "Allow Contacts",
            ) { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), PHONE_REQUEST_CONTACTS) }
            return
        }
        val people = data.people().distinctBy { it.id }
        val search = phoneContactsSearch(container)
        val area = FrameLayout(this)
        container.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
        area.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val list =
            column().apply {
                setPadding(
                    0,
                    0,
                    px(PHONE_LIST_FOOTER_INSET_DP),
                    px(PHONE_CONTACT_LIST_BOTTOM_INSET_DP),
                )
            }
        scroll.addView(list)
        val sections = mutableMapOf<String, View>()

        attachPhoneContactSearch(search, people, list, sections)
        populatePhoneContacts(contactSearch, people, list, sections)
        installPhoneAlphabet(area, scroll, sections)
        floating(
            area,
            "+",
            "Add contact",
            FloatingPosition(Gravity.END or Gravity.BOTTOM, PHONE_KEYPAD_VALUE_15, PHONE_KEYPAD_VALUE_16),
        ) { addContact() }
    }

    private fun attachPhoneContactSearch(
        search: EditText,
        people: List<TenSevenPhoneData.Person>,
        list: LinearLayout,
        sections: MutableMap<String, View>,
    ) {
        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    populatePhoneContacts(s?.toString().orEmpty(), people, list, sections)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
    }

    private fun populatePhoneContacts(
        query: String,
        people: List<TenSevenPhoneData.Person>,
        list: LinearLayout,
        sections: MutableMap<String, View>,
    ) {
        contactSearch = query
        list.removeAllViews()
        sections.clear()
        val normalized = query.filter(Char::isDigit)
        val filtered =
            people.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (normalized.isNotEmpty() && it.number.filter(Char::isDigit).contains(normalized))
            }
        if (filtered.isEmpty()) {
            notice(list, "No matching contacts")
            return
        }
        var previous = ""
        filtered.forEach { person ->
            val letter = PhoneUiRules.firstLetter(person.name)
            if (letter != previous) {
                val heading =
                    label(letter, PHONE_COMPACT_TEXT_SP, bold = true).apply {
                        setPadding(
                            px(PHONE_LIST_CONTENT_INSET_DP),
                            px(PHONE_EXTRA_TIGHT_INSET_DP),
                            px(PHONE_ROW_CONTENT_GUTTER_DP),
                            px(PHONE_EXTRA_TIGHT_INSET_DP),
                        )
                        setBackgroundColor(colors.panel)
                    }
                list.addView(heading, LinearLayout.LayoutParams(-1, px(PHONE_CONTACT_LETTER_HEADING_DP)))
                sections[letter] = heading
                previous = letter
            }
            appendPhoneContactRow(list, person)
        }
    }

    private fun phoneContactsSearch(container: LinearLayout): EditText {
        val search =
            EditText(this).apply {
                setSingleLine(true)
                textSize = PHONE_BODY_TEXT_SP
                hint = "⌕  Search contacts"
                setTextColor(colors.fg)
                setHintTextColor(colors.muted)
                background = round(colors.panel, PHONE_CONTACTS_VALUE_14, true)
                setPadding(px(PHONE_LIST_CONTENT_INSET_DP), 0, px(PHONE_LIST_CONTENT_INSET_DP), 0)
                setText(contactSearch)
            }
        val searchBand =
            row().apply {
                setPadding(
                    px(PHONE_PAGE_CONTENT_INSET_DP),
                    px(PHONE_COMPACT_INSET_DP),
                    px(PHONE_PAGE_CONTENT_INSET_DP),
                    px(PHONE_SMALL_INSET_DP),
                )
            }
        container.addView(searchBand, LinearLayout.LayoutParams(-1, px(PHONE_SEARCH_BAND_HEIGHT_DP)))
        searchBand.addView(search, LinearLayout.LayoutParams(-1, px(PHONE_SEARCH_AND_ACTION_HEIGHT_DP)))
        return search
    }

    private fun installPhoneAlphabet(
        area: FrameLayout,
        scroll: ScrollView,
        sections: Map<String, View>,
    ) {
        val alphabet =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
        area.addView(
            alphabet,
            FrameLayout.LayoutParams(px(PHONE_MINI_ICON_EDGE_DP), -1, Gravity.END).apply {
                bottomMargin = px(PHONE_ALPHABET_SCROLL_MARGIN_DP)
            },
        )
        "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { c ->
            val item =
                clickLabel(c.toString(), "Jump to $c", PHONE_KEYPAD_RATIO_9_F) {
                    val anchor = sections[c.toString()]
                    if (anchor != null) scroll.post { scroll.smoothScrollTo(0, anchor.top) }
                }
            item.setTextColor(colors.muted)
            alphabet.addView(item, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun recents(frame: FrameLayout) {
        val root = column()
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        renderRecentFilters(root)
        val listArea = FrameLayout(this)
        root.addView(listArea, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        listArea.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val list =
            column().apply {
                setPadding(
                    0,
                    px(PHONE_EXTRA_TIGHT_INSET_DP),
                    0,
                    px(PHONE_RECENTS_BOTTOM_INSET_DP),
                )
            }
        scroll.addView(list)
        renderRecentHistory(list)
        renderVoicemailRow(list)
        floating(
            listArea,
            "⠿",
            "Open dialpad",
            FloatingPosition(Gravity.END or Gravity.BOTTOM, PHONE_RECENTS_VALUE_15, PHONE_RECENTS_VALUE_16),
        ) {
            screen = Screen.DIAL
            draw()
        }
    }

    private fun renderRecentFilters(root: LinearLayout) {
        val filters = row()
        root.addView(filters, LinearLayout.LayoutParams(-1, px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)))
        listOf("All", "Missed", "Incoming", "Outgoing").forEach { name ->
            val selected = callFilter == name
            val control =
                clickLabel(name, "Show $name calls", PHONE_CAPTION_TEXT_SP) {
                    callFilter = name
                    draw()
                }.apply { setTextColor(if (selected) Color.WHITE else colors.muted) }
            val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            group.addView(control, LinearLayout.LayoutParams(-1, 0, 1f))
            group.addView(
                View(this).apply {
                    setBackgroundColor(if (selected) colors.accent else colors.bg)
                },
                LinearLayout.LayoutParams(-1, px(PHONE_MICRO_INSET_DP)),
            )
            filters.addView(group, LinearLayout.LayoutParams(0, -1, 1f))
        }
        rule(root)
    }

    private fun renderRecentHistory(list: LinearLayout) {
        if (!isGranted(Manifest.permission.READ_CALL_LOG)) {
            notice(
                list,
                "Call history needs Android call-log access. Selecting TenSeven as default Phone may be required.",
                "Open default Phone setup",
            ) { defaultPhoneSetup() }
            notice(list, "Allow call history", "Request call-log permission") {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), PHONE_REQUEST_CALL_LOG)
            }
        } else {
            val people = if (isGranted(Manifest.permission.READ_CONTACTS)) data.people() else emptyList()
            val byNumber = people.associateBy { PhoneBlocklist.normalize(it.number) }
            val favorites = people.filter { it.favorite }.map { PhoneBlocklist.normalize(it.number) }.toSet()
            val entries =
                data.calls().filter {
                    when (callFilter) {
                        "Missed" -> it.kind == CallLog.Calls.MISSED_TYPE
                        "Incoming" -> it.kind == CallLog.Calls.INCOMING_TYPE
                        "Outgoing" -> it.kind == CallLog.Calls.OUTGOING_TYPE
                        "Favorites" -> PhoneBlocklist.normalize(it.number) in favorites
                        else -> true
                    }
                }
            if (entries.isEmpty()) notice(list, "No ${callFilter.lowercase()} calls")
            entries.forEach { entry ->
                val person = byNumber[PhoneBlocklist.normalize(entry.number)]
                renderRecentCallRow(list, entry, person)
            }
        }
    }

    private fun renderRecentCallRow(
        list: LinearLayout,
        entry: TenSevenPhoneData.Call,
        person: TenSevenPhoneData.Person?,
    ) {
        val kind = recentCallKind(entry.kind)
        val item =
            row().apply {
                setPadding(
                    px(PHONE_LIST_CONTROL_INSET_DP),
                    px(PHONE_SMALL_INSET_DP),
                    px(PHONE_SECTION_INSET_DP),
                    px(PHONE_SMALL_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "${person?.name ?: entry.number}, $kind, ${entry.whenText}"
                setOnClickListener {
                    if (person != null) {
                        openContact(person.id, Screen.RECENTS)
                    } else {
                        dialNumber = entry.number
                        screen = Screen.DIAL
                        draw()
                    }
                }
            }
        list.addView(item, LinearLayout.LayoutParams(-1, px(PHONE_RECENT_CALL_ROW_HEIGHT_DP)))
        val glyph = recentCallKindGlyph(kind)
        item.addView(glyph, LinearLayout.LayoutParams(px(PHONE_CALL_TYPE_ICON_WIDTH_DP), -1))
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        item.addView(summary, LinearLayout.LayoutParams(0, -PHONE_RECENTS_DP_2, 1f))
        summary.addView(
            label(
                person?.name ?: entry.number.ifBlank { "Private number" },
                PHONE_BODY_TEXT_SP,
                if (kind == "Missed") colors.danger else colors.fg,
                bold = true,
            ),
        )
        summary.addView(label(kind, PHONE_CAPTION_TEXT_SP, colors.muted))
        item.addView(
            label(entry.whenText, PHONE_RECENTS_SP_11_F, colors.muted).apply {
                gravity =
                    Gravity.CENTER
            },
        )
        item.addView(
            label("›", PHONE_RECENTS_SP_21_F, colors.muted).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(px(PHONE_CALLOUT_TOP_INSET_DP), -1),
        )
        rule(list)
    }

    private fun recentCallKind(kind: Int): String =
        when (kind) {
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            else -> "Call"
        }

    private fun recentCallKindGlyph(kind: String): TextView {
        val glyph =
            label(
                when (kind) {
                    "Missed" -> "✕"
                    "Outgoing" -> "↗"
                    "Blocked" -> "⊘"
                    else -> "↙"
                },
                PHONE_RECENTS_RATIO_25_F,
                if (kind == "Missed") colors.danger else colors.accent,
            ).apply { gravity = Gravity.CENTER }
        return glyph
    }

    private fun renderVoicemailRow(list: LinearLayout) {
        // Voicemail must be visible in Recents even if call history is empty.
        val vm =
            row().apply {
                setPadding(
                    px(PHONE_DETAIL_CONTENT_INSET_DP),
                    px(PHONE_LIST_CONTROL_INSET_DP),
                    px(PHONE_AUXILIARY_INSET_DP),
                    px(PHONE_LIST_CONTROL_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "Open voicemail"
                setOnClickListener { openVoicemail() }
            }
        list.addView(vm, LinearLayout.LayoutParams(-1, px(PHONE_VOICEMAIL_ACTION_HEIGHT_DP)))
        vm.addView(
            label("☏", PHONE_RECENTS_SP_24_F).apply { gravity = Gravity.CENTER },
            LinearLayout
                .LayoutParams(px(PHONE_CONTACT_AVATAR_EDGE_DP), -PHONE_RECENTS_DP_1),
        )
        val vtext = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        vm.addView(vtext, LinearLayout.LayoutParams(0, -PHONE_RECENTS_DP_2, 1f))
        vtext.addView(label("Voicemail", PHONE_BODY_TEXT_SP, bold = true))
        vtext.addView(label("Open carrier voicemail", PHONE_CAPTION_TEXT_SP, colors.muted))
        vm.addView(label("›", PHONE_SYMBOL_TEXT_SP, colors.muted))
        rule(list)
    }

    private fun contactDetails(frame: FrameLayout) {
        val id = selectedContactId
        when {
            id == null -> {
                screen = Screen.CONTACTS
                draw()
            }

            !isGranted(Manifest.permission.READ_CONTACTS) -> {
                val root = column()
                frame.addView(root)
                notice(root, "Contacts permission is required to view this contact")
            }

            else -> {
                val person = data.people().firstOrNull { it.id == id }
                if (person == null) {
                    screen = Screen.CONTACTS
                    selectedContactId = null
                    draw()
                } else {
                    renderSelectedContact(frame, id, person)
                }
            }
        }
    }

    private fun renderSelectedContact(
        frame: FrameLayout,
        id: Long,
        person: TenSevenPhoneData.Person,
    ) {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val body =
            column().apply {
                setPadding(
                    px(PHONE_LIST_CONTENT_INSET_DP),
                    px(PHONE_STANDARD_INSET_DP),
                    px(PHONE_LIST_CONTENT_INSET_DP),
                    px(PHONE_LIST_CONTENT_INSET_DP),
                )
            }
        scroll.addView(body)
        renderContactDetailHeader(body, id, person)
        renderContactBlocking(body, id, person)
        renderContactInformation(body, id, person)
    }

    private fun renderContactDetailHeader(
        body: LinearLayout,
        id: Long,
        person: TenSevenPhoneData.Person,
    ) {
        val photo = row().apply { gravity = Gravity.CENTER }
        body.addView(photo, LinearLayout.LayoutParams(-1, px(PHONE_CONTACT_PHOTO_AREA_HEIGHT_DP)))
        photo.addView(
            TenSevenContactPhotos.avatar(this, id, person.name, PHONE_CONTACT_DETAILS_VALUE_78, colors),
            LinearLayout.LayoutParams(px(PHONE_CONTACT_PHOTO_EDGE_DP), px(PHONE_CONTACT_PHOTO_EDGE_DP)),
        )
        body.addView(
            label(person.name, PHONE_CONTACT_DETAILS_SP_23_F, bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, px(PHONE_COMPACT_MATCH_HEIGHT_DP)),
        )
        val quick = row().apply { gravity = Gravity.CENTER }
        body.addView(quick, LinearLayout.LayoutParams(-1, px(PHONE_CONTACT_QUICK_ACTION_HEIGHT_DP)))
        quickAction(quick, "✆", "Call") { placeCall(person.number) }
        quickAction(quick, "▤", "Message") { message(person.number) }
        quickAction(quick, "✎", "Edit") { openEditor(id) }
    }

    private fun renderContactBlocking(
        body: LinearLayout,
        id: Long,
        person: TenSevenPhoneData.Person,
    ) {
        // Approved: Block is immediately under the contact actions, never buried at bottom.
        val isBlocked = PhoneBlocklist.contains(this, person.number)
        val block =
            clickLabel(
                if (isBlocked) "⊘  Unblock contact" else "⊘  Block contact",
                if (isBlocked) "Unblock contact" else "Block contact",
                PHONE_SECONDARY_TEXT_SP,
            ) {
                val title = if (isBlocked) "Unblock contact?" else "Block contact?"
                D5BbDialogs.confirm(
                    this,
                    title,
                    if (isBlocked) "Allow calls from ${person.name}?" else "Block calls from ${person.name}?",
                    if (isBlocked) "Unblock" else "Block",
                ) {
                    val numbers = data.numbers(id).map { it.second }.ifEmpty { listOf(person.number) }
                    numbers.forEach { number ->
                        if (isBlocked) PhoneBlocklist.unblock(this, number) else PhoneBlocklist.block(this, number)
                    }
                    draw()
                }
            }.apply {
                background = round(colors.panel, PHONE_CONTACT_DETAILS_VALUE_10, true)
                setTextColor(if (isBlocked) colors.muted else colors.fg)
            }
        body.addView(
            block,
            LinearLayout.LayoutParams(-1, px(PHONE_BLOCK_ACTION_HEIGHT_DP)).apply {
                bottomMargin =
                    px(PHONE_SECTION_INSET_DP)
            },
        )
        rule(body)
    }

    private fun renderContactInformation(
        body: LinearLayout,
        id: Long,
        person: TenSevenPhoneData.Person,
    ) {
        val nums = data.numbers(id).ifEmpty { listOf("Mobile" to person.number) }
        nums.forEach { (tag, number) ->
            infoRow(body, "✆", tag, number) { placeCall(number) }
        }
        data.emails(id).forEach { email ->
            infoRow(body, "✉", "Email", email) {
                safeStart(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)),
                        "Choose an email app",
                    ),
                )
            }
        }
        data.company(id)?.takeIf(String::isNotBlank)?.let { infoRow(body, "▥", "Company", it) { } }
    }

    private fun quickAction(
        parent: LinearLayout,
        symbol: String,
        caption: String,
        action: () -> Unit,
    ) {
        val holder =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
        val icon =
            clickLabel(symbol, caption, PHONE_QUICK_ACTION_RATIO_23_F, action).apply {
                background = circular(colors.accent)
                setTextColor(Color.WHITE)
            }
        holder.addView(
            icon,
            LinearLayout.LayoutParams(
                px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP),
                px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP),
            ),
        )
        holder.addView(
            label(caption, PHONE_CAPTION_TEXT_SP).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, px(PHONE_DETAIL_CAPTION_HEIGHT_DP)),
        )
        parent.addView(holder, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun infoRow(
        parent: LinearLayout,
        symbol: String,
        title: String,
        detail: String,
        action: () -> Unit,
    ) {
        val item =
            row().apply {
                setPadding(
                    px(PHONE_SMALL_INSET_DP),
                    px(PHONE_STANDARD_INSET_DP),
                    px(PHONE_SMALL_INSET_DP),
                    px(PHONE_STANDARD_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        parent.addView(item, LinearLayout.LayoutParams(-1, px(PHONE_EXPANDED_INPUT_HEIGHT_DP)))
        item.addView(
            label(symbol, PHONE_SYMBOL_TEXT_SP).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(px(PHONE_COMPACT_MATCH_HEIGHT_DP), -1),
        )
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        item.addView(texts, LinearLayout.LayoutParams(0, -PHONE_INFO_ROW_DP_2, 1f))
        texts.addView(label(title, PHONE_CAPTION_TEXT_SP, colors.muted))
        texts.addView(label(detail, PHONE_SECONDARY_TEXT_SP))
        rule(parent)
    }

    private fun blocklist(frame: FrameLayout) {
        val scroll = ScrollView(this)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val body =
            column().apply {
                setPadding(
                    px(PHONE_LIST_CONTENT_INSET_DP),
                    px(PHONE_SECTION_INSET_DP),
                    px(PHONE_LIST_CONTENT_INSET_DP),
                    px(PHONE_CALLOUT_CONTENT_INSET_DP),
                )
            }
        scroll.addView(body)
        notice(
            body,
            "Numbers you explicitly block are screened when TenSeven holds " +
                "the optional Android screening role.",
        )
        blockNumberEntry(body)
        renderBlockedNumberRows(body)
        renderCallScreeningSetting(body)
        allowNumberEntry(body)
        renderAllowedNumberRows(body)
        renderUnknownCallerSilencer(body)
        renderBlockedCallHistory(body)
        notice(body, "Unknown calls are only silenced when screening is enabled and Contacts access is allowed.")
    }

    private fun blockNumberEntry(body: LinearLayout) {
        val input =
            EditText(this).apply {
                hint = "Number with country code"
                inputType = InputType.TYPE_CLASS_PHONE
                setTextColor(colors.fg)
                setHintTextColor(colors.muted)
            }
        body.addView(input)
        body.addView(
            clickLabel("Block number", "Block number") {
                val value = PhoneBlocklist.normalize(input.text.toString())
                if (value.length < PHONE_BLOCKLIST_VALUE_7) {
                    toast("Enter a full number")
                } else {
                    PhoneBlocklist.block(this, value)
                    draw()
                }
            }.apply { setTextColor(colors.accent) },
            LinearLayout.LayoutParams(-1, px(PHONE_MENU_ROW_HEIGHT_DP)),
        )
    }

    private fun renderBlockedNumberRows(body: LinearLayout) {
        PhoneBlocklist.blocked(this).sorted().forEach { number ->
            body.addView(
                clickLabel("$number     Unblock", "Unblock $number") {
                    D5BbDialogs.confirm(this, "Unblock number", "Allow calls from $number?", "Unblock") {
                        PhoneBlocklist.unblock(this, number)
                        draw()
                    }
                },
                LinearLayout.LayoutParams(-1, px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP)),
            )
            rule(body)
        }
    }

    private fun renderCallScreeningSetting(body: LinearLayout) {
        body.addView(
            clickLabel("Allowed numbers / screening settings", "Screening settings") {
                val roles = getSystemService(RoleManager::class.java)
                if (roles?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true &&
                    !roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                ) {
                    safeStart(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                } else {
                    D5BbDialogs.message(this, "Call screening", "Review Android Default Apps to change call screening.")
                }
            }.apply { setTextColor(colors.accent) },
            LinearLayout.LayoutParams(-1, px(PHONE_CONTACT_OR_MENU_ROW_DP)),
        )
    }

    private fun allowNumberEntry(body: LinearLayout) {
        // Preserve existing explicit allowlist without auto-blocking unknown callers.
        val allow =
            EditText(this).apply {
                hint = "Number to allow"
                inputType = InputType.TYPE_CLASS_PHONE
                setTextColor(colors.fg)
                setHintTextColor(colors.muted)
            }
        body.addView(allow)
        body.addView(
            clickLabel("Allow number", "Allow number") {
                val number = PhoneBlocklist.normalize(allow.text.toString())
                if (number.length < PHONE_BLOCKLIST_VALUE_7) {
                    toast("Enter a full number")
                } else {
                    PhoneBlocklist.allow(this, number)
                    draw()
                }
            },
            LinearLayout.LayoutParams(-1, px(PHONE_MENU_ACTION_HEIGHT_DP)),
        )
    }

    private fun renderAllowedNumberRows(body: LinearLayout) {
        PhoneBlocklist.allowed(this).sorted().forEach { number ->
            body.addView(
                clickLabel("$number     Remove", "Remove allowed $number") {
                    D5BbDialogs.confirm(this, "Remove exception", "Remove allowed number $number?", "Remove") {
                        PhoneBlocklist.unallow(this, number)
                        draw()
                    }
                },
                LinearLayout.LayoutParams(-1, px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP)),
            )
        }
    }

    private fun renderUnknownCallerSilencer(body: LinearLayout) {
        body.addView(
            clickLabel("Silence unknown: ${if (PhoneBlocklist.silenceUnknown(this)) "ON" else "OFF"}") {
                PhoneBlocklist.setSilenceUnknown(this, !PhoneBlocklist.silenceUnknown(this))
                draw()
            },
            LinearLayout.LayoutParams(-1, px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)),
        )
    }

    private fun renderBlockedCallHistory(body: LinearLayout) {
        body.addView(
            clickLabel("Blocked call history", "View blocked calls") {
                if (!isGranted(Manifest.permission.READ_CALL_LOG)) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), PHONE_REQUEST_CALL_LOG)
                } else {
                    val recent =
                        data
                            .calls()
                            .filter { it.kind == CallLog.Calls.BLOCKED_TYPE }
                            .take(PHONE_BLOCKLIST_MAX_25)
                    D5BbDialogs.message(
                        this,
                        "Blocked calls",
                        if (recent.isEmpty()) {
                            "No blocked calls in Android call history"
                        } else {
                            recent.joinToString("\n") { "${it.number} · ${it.whenText}" }
                        },
                    )
                }
            },
            LinearLayout.LayoutParams(-1, px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)),
        )
    }

    private fun openMenu() {
        val items = listOf("Spam & Blocklist", "Favorite calls", "Default Phone setup", "Floating dialpad")
        D5BbDialogs.choices(this, "Phone options", items) { index ->
            when (index) {
                0 -> {
                    screen = Screen.BLOCKLIST
                    draw()
                }

                1 -> {
                    screen = Screen.RECENTS
                    callFilter = "Favorites"
                    draw()
                }

                2 -> {
                    defaultPhoneSetup()
                }

                PHONE_OPEN_MENU_VALUE_3 -> {
                    safeStart(Intent(this, TenSevenFloatingDialpadActivity::class.java))
                }
            }
        }
    }

    private fun openContact(
        id: Long,
        origin: Screen,
    ) {
        selectedContactId = id
        returnTo = origin
        screen = Screen.CONTACT_DETAIL
        draw()
    }

    private fun openEditor(id: Long) {
        val edit =
            Intent(
                Intent.ACTION_EDIT,
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
            )
        // Contact IDs are private: never expose them through a freely resolved implicit intent.
        val editor = edit.resolveActivity(packageManager)
        if (editor == null) {
            // Package visibility can hide handlers; Android's chooser requires user consent.
            safeStart(Intent.createChooser(edit, "Choose a contact editor"))
            return
        }
        safeStart(edit.setComponent(editor))
    }

    private fun addContact() = safeStart(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))

    private fun message(number: String) {
        // User-selected default SMS app, not any app that registers an smsto: filter.
        val smsPackage =
            android.provider.Telephony.Sms
                .getDefaultSmsPackage(this)
        if (smsPackage.isNullOrBlank()) {
            toast("Set a default messaging app to send a message")
            return
        }
        safeStart(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).setPackage(smsPackage))
    }

    private fun openTrustedDialer(uri: Uri) {
        val dialerPackage = getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        if (dialerPackage.isNullOrBlank()) {
            toast("Set a default Phone app to place a call")
            return
        }
        safeStart(Intent(Intent.ACTION_DIAL, uri).setPackage(dialerPackage))
    }

    private fun openVoicemail() = openTrustedDialer("voicemail:".toUri())

    private fun defaultPhoneSetup() = safeStart(Intent(this, D5PhoneRoleRequestActivity::class.java))

    private fun safeStart(intent: Intent) {
        val sensitive =
            intent.action == Intent.ACTION_EDIT || intent.action == Intent.ACTION_SENDTO ||
                intent.action == Intent.ACTION_DIAL
        if (sensitive && intent.component == null && intent.`package`.isNullOrBlank()) {
            toast("No trusted application was selected for this action")
            return
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("No compatible app is available for this action")
        }
    }

    private data class FloatingPosition(
        val alignment: Int,
        val right: Int,
        val bottom: Int,
    )

    private fun floating(
        parent: FrameLayout,
        glyph: String,
        description: String,
        position: FloatingPosition,
        action: () -> Unit,
    ) {
        val control =
            clickLabel(glyph, description, PHONE_FLOATING_RATIO_28_F, action).apply {
                background = circular(colors.accent)
                setTextColor(Color.WHITE)
                elevation = px(PHONE_TIGHT_INSET_DP).toFloat()
            }
        parent.addView(
            control,
            FrameLayout
                .LayoutParams(
                    px(PHONE_FLOATING_ACTION_EDGE_DP),
                    px(PHONE_FLOATING_ACTION_EDGE_DP),
                    position.alignment,
                ).apply {
                    rightMargin = px(position.right)
                    bottomMargin = px(position.bottom)
                },
        )
    }

    private fun placeCall(raw: String) {
        val number = PhoneNumberUtils.stripSeparators(raw).trim()
        if (number.isBlank()) {
            toast("Enter a number first")
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        if (roles?.isRoleHeld(RoleManager.ROLE_DIALER) != true) {
            // Do not silently claim dialer role. Hand off only to the chosen default Phone app.
            openTrustedDialer(Uri.fromParts("tel", number, null))
        } else if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            queuedNumber = number
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), PHONE_REQUEST_CALL)
        } else {
            try {
                getSystemService(TelecomManager::class.java)?.placeCall(Uri.fromParts("tel", number, null), Bundle())
                    ?: toast("Telecom is unavailable")
            } catch (_: Exception) {
                toast("Call could not start; check SIM and Phone access")
            }
        }
    }

    override fun onRequestPermissionsResult(
        code: Int,
        names: Array<String>,
        grants: IntArray,
    ) {
        super.onRequestPermissionsResult(code, names, grants)
        if (code == PHONE_REQUEST_CALL && isGranted(Manifest.permission.CALL_PHONE)) {
            queuedNumber?.let { number ->
                queuedNumber = null
                placeCall(number)
            }
        }
        draw()
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
