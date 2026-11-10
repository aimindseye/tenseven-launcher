package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.Activity
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
import io.github.aimindseye.tenseven.D5BbDialogs
import io.github.aimindseye.tenseven.D5PhoneRoleRequestActivity
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/**
 * D5-R9.1: FULL REWRITE of the Phone activity's presentation.
 * Approved contract: one top Dial / Contacts / Recents navigation; fixed Dial call action;
 * inline number matches; A–Z contacts; filters, voicemail, photos and top-level block action.
 *
 * This file does not own Android call state. TenSevenInCallService remains in TenSevenTelecom.kt.
 * It does not change Android default dialer roles without an explicit user action.
 */
class TenSevenPhoneActivity : Activity() {
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

    @Deprecated("Use the screen-specific back hierarchy")
    override fun onBackPressed() {
        when (screen) {
            Screen.CONTACT_DETAIL -> {
                screen = returnTo
                selectedContactId = null
                draw()
            }

            Screen.BLOCKLIST -> {
                screen = Screen.RECENTS
                draw()
            }

            else -> {
                super.onBackPressed()
            }
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
        size: Float = 15f,
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
        parent.addView(View(this).apply { setBackgroundColor(colors.line) }, LinearLayout.LayoutParams(-1, px(1)))

    private fun round(
        color: Int,
        radius: Int = 12,
        stroke: Boolean = false,
    ) = GradientDrawable().apply {
        cornerRadius = px(radius).toFloat()
        setColor(color)
        if (stroke) setStroke(px(1), this@TenSevenPhoneActivity.colors.line)
    }

    private fun circular(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun clickLabel(
        text: String,
        description: String = text,
        size: Float = 15f,
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
        val note = label(message, 14f, colors.muted)
        note.setPadding(px(16), px(18), px(16), px(14))
        body.addView(note)
        if (action != null && onClick != null) {
            val go =
                clickLabel(action, action, 15f, onClick).apply {
                    setTextColor(colors.accent)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(16), px(6), px(16), px(12))
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
        shell.addView(navigation, LinearLayout.LayoutParams(-1, px(43)))
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
                label(title, 15f, if (selected) colors.fg else colors.muted).apply {
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
            tab.addView(
                View(this).apply { setBackgroundColor(if (selected) colors.accent else colors.bg) },
                LinearLayout.LayoutParams(-1, px(3)),
            )
            tabRegion.addView(tab, LinearLayout.LayoutParams(0, -1, 1f))
        }
        navigation.addView(
            clickLabel("⋮", "Phone options", 22f) { openMenu() },
            LinearLayout.LayoutParams(px(35), -1),
        )
        rule(shell)
    }

    private fun detailHeader(shell: LinearLayout) {
        val head = row().apply { setPadding(px(7), 0, px(8), 0) }
        shell.addView(head, LinearLayout.LayoutParams(-1, px(44)))
        head.addView(clickLabel("‹", "Back", 29f) { onBackPressed() }, LinearLayout.LayoutParams(px(43), -1))
        head.addView(
            label(if (screen == Screen.BLOCKLIST) "Spam & Blocklist" else "", 16f),
            LinearLayout.LayoutParams(0, -1, 1f),
        )
        if (screen == Screen.CONTACT_DETAIL) {
            val favorite = selectedContactId?.let { id -> data.people().firstOrNull { it.id == id }?.favorite } == true
            head.addView(
                clickLabel(if (favorite) "★" else "☆", "Edit favorite in Contacts", 24f) {
                    selectedContactId?.let(::openEditor)
                },
                LinearLayout.LayoutParams(px(44), -1),
            )
        }
        head.addView(
            clickLabel("⋮", "Phone options", 22f) { openMenu() },
            LinearLayout.LayoutParams(px(42), -1),
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
        val panel = column().apply { setPadding(px(10), px(3), px(10), px(5)) }
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))

        val numberRow = row()
        panel.addView(numberRow, LinearLayout.LayoutParams(-1, px(if (size.compact) 43 else 52)))
        val input =
            EditText(this).apply {
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_PHONE
                showSoftInputOnFocus = false // Hardware QWERTY and touch keys both remain available.
                textSize = if (size.compact) 24f else 28f
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
            clickLabel("⌫", "Delete last digit", 25f) {
                if (input.text.isNotEmpty()) input.text.delete(input.length() - 1, input.length())
            }.apply {
                setOnLongClickListener {
                    input.text.clear()
                    true
                }
            },
            LinearLayout.LayoutParams(px(44), -1),
        )
        rule(panel)

        val matches = column()
        panel.addView(matches, LinearLayout.LayoutParams(-1, 0))
        val people = if (isGranted(Manifest.permission.READ_CONTACTS)) data.people() else emptyList()

        fun updateMatches(text: String) {
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
            matches.layoutParams = LinearLayout.LayoutParams(-1, px(results.size * (if (size.compact) 37 else 44)))
            results.forEach { match ->
                val cell =
                    row().apply {
                        setPadding(px(6), px(2), px(7), px(2))
                        isClickable = true
                        isFocusable = true
                        contentDescription = "Dial ${match.name} ${match.number}"
                        setOnClickListener {
                            input.setText(match.number)
                            input.setSelection(input.length())
                        }
                    }
                matches.addView(cell, LinearLayout.LayoutParams(-1, px(if (size.compact) 36 else 43)))
                cell.addView(
                    TenSevenContactPhotos.avatar(this, match.contactId, match.name, 32, colors),
                    LinearLayout.LayoutParams(px(32), px(32)),
                )
                val names =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(px(9), 0, 0, 0)
                    }
                cell.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
                names.addView(label(match.name, 13f, bold = true))
                names.addView(label(match.number, 11f, colors.muted))
                cell.addView(label("›", 22f, colors.muted))
                rule(matches)
            }
        }
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
                    updateMatches(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        updateMatches(dialNumber)

        val keypad = column()
        panel.addView(keypad, LinearLayout.LayoutParams(-1, 0, 1f))
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
        digits.chunked(3).forEach { trio ->
            val keyRow = row()
            keypad.addView(keyRow, LinearLayout.LayoutParams(-1, 0, 1f))
            trio.forEachIndexed { index, (digit, letters) ->
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
                    label(digit, if (size.compact) 23f else 29f).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, 0, 2f),
                )
                key.addView(
                    label(letters, if (size.compact) 9f else 10f, colors.muted).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    },
                    LinearLayout.LayoutParams(-1, 0, 1f),
                )
                if (index < 2) {
                    keyRow.addView(
                        View(this).apply { setBackgroundColor(colors.line) },
                        LinearLayout.LayoutParams(px(1), -1),
                    )
                }
            }
            rule(keypad)
        }
        val callRow = row().apply { gravity = Gravity.CENTER }
        panel.addView(callRow, LinearLayout.LayoutParams(-1, px(size.primaryCallHeightDp)))
        val call =
            clickLabel("✆", "Place call", 29f) {
                dialNumber = input.text.toString()
                placeCall(dialNumber)
            }.apply {
                background = round(colors.success, 24)
                setTextColor(Color.WHITE)
            }
        callRow.addView(
            call,
            LinearLayout.LayoutParams(
                px(if (size.compact) 126 else 145),
                px(size.primaryCallHeightDp - 8),
            ),
        )
    }

    private fun contacts(frame: FrameLayout) {
        val container = column()
        frame.addView(container, FrameLayout.LayoutParams(-1, -1))
        if (!isGranted(Manifest.permission.READ_CONTACTS)) {
            notice(
                container,
                "Allow Contacts to show people stored on this device. No contact information is uploaded.",
                "Allow Contacts",
            ) { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 201) }
            return
        }
        val people = data.people().distinctBy { it.id }
        val search =
            EditText(this).apply {
                setSingleLine(true)
                textSize = 15f
                hint = "⌕  Search contacts"
                setTextColor(colors.fg)
                setHintTextColor(colors.muted)
                background = round(colors.panel, 14, true)
                setPadding(px(13), 0, px(13), 0)
                setText(contactSearch)
            }
        val searchBand = row().apply { setPadding(px(12), px(5), px(12), px(6)) }
        container.addView(searchBand, LinearLayout.LayoutParams(-1, px(54)))
        searchBand.addView(search, LinearLayout.LayoutParams(-1, px(42)))
        val area = FrameLayout(this)
        container.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
        area.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val list = column().apply { setPadding(0, 0, px(29), px(70)) }
        scroll.addView(list)
        val sections = mutableMapOf<String, View>()

        fun populate(query: String) {
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
                        label(letter, 13f, bold = true).apply {
                            setPadding(px(13), px(3), px(9), px(3))
                            setBackgroundColor(colors.panel)
                        }
                    list.addView(heading, LinearLayout.LayoutParams(-1, px(26)))
                    sections[letter] = heading
                    previous = letter
                }
                val entry =
                    row().apply {
                        setPadding(px(12), px(4), px(8), px(4))
                        isClickable = true
                        isFocusable = true
                        contentDescription = "Open ${person.name}"
                        setOnClickListener { openContact(person.id, Screen.CONTACTS) }
                    }
                list.addView(entry, LinearLayout.LayoutParams(-1, px(49)))
                entry.addView(
                    TenSevenContactPhotos.avatar(this, person.id, person.name, 36, colors),
                    LinearLayout.LayoutParams(px(36), px(36)),
                )
                entry.addView(
                    label(person.name, 15f).apply { setPadding(px(12), 0, 0, 0) },
                    LinearLayout.LayoutParams(0, -1, 1f),
                )
                rule(list)
            }
        }
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
                    populate(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        populate(contactSearch)
        val alphabet =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
        area.addView(
            alphabet,
            FrameLayout.LayoutParams(px(24), -1, Gravity.END).apply {
                bottomMargin = px(64)
            },
        )
        "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { c ->
            val item =
                clickLabel(c.toString(), "Jump to $c", 9f) {
                    val anchor = sections[c.toString()]
                    if (anchor != null) scroll.post { scroll.smoothScrollTo(0, anchor.top) }
                }
            item.setTextColor(colors.muted)
            alphabet.addView(item, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        floating(area, "+", "Add contact", Gravity.END or Gravity.BOTTOM, 15, 16) { addContact() }
    }

    private fun recents(frame: FrameLayout) {
        val root = column()
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        val filters = row()
        root.addView(filters, LinearLayout.LayoutParams(-1, px(44)))
        listOf("All", "Missed", "Incoming", "Outgoing").forEach { name ->
            val selected = callFilter == name
            val control =
                clickLabel(name, "Show $name calls", 12f) {
                    callFilter = name
                    draw()
                }.apply { setTextColor(if (selected) Color.WHITE else colors.muted) }
            val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            group.addView(control, LinearLayout.LayoutParams(-1, 0, 1f))
            group.addView(
                View(this).apply {
                    setBackgroundColor(if (selected) colors.accent else colors.bg)
                },
                LinearLayout.LayoutParams(-1, px(2)),
            )
            filters.addView(group, LinearLayout.LayoutParams(0, -1, 1f))
        }
        rule(root)
        val listArea = FrameLayout(this)
        root.addView(listArea, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        listArea.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val list = column().apply { setPadding(0, px(3), 0, px(75)) }
        scroll.addView(list)
        if (!isGranted(Manifest.permission.READ_CALL_LOG)) {
            notice(
                list,
                "Call history needs Android call-log access. Selecting TenSeven as default Phone may be required.",
                "Open default Phone setup",
            ) { defaultPhoneSetup() }
            notice(list, "Allow call history", "Request call-log permission") {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 202)
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
                val kind =
                    when (entry.kind) {
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                        else -> "Call"
                    }
                val item =
                    row().apply {
                        setPadding(px(11), px(6), px(10), px(6))
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
                list.addView(item, LinearLayout.LayoutParams(-1, px(62)))
                val glyph =
                    label(
                        when (kind) {
                            "Missed" -> "✕"
                            "Outgoing" -> "↗"
                            "Blocked" -> "⊘"
                            else -> "↙"
                        },
                        25f,
                        if (kind == "Missed") colors.danger else colors.accent,
                    ).apply { gravity = Gravity.CENTER }
                item.addView(glyph, LinearLayout.LayoutParams(px(39), -1))
                val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                item.addView(summary, LinearLayout.LayoutParams(0, -2, 1f))
                summary.addView(
                    label(
                        person?.name ?: entry.number.ifBlank { "Private number" },
                        15f,
                        if (kind == "Missed") colors.danger else colors.fg,
                        bold = true,
                    ),
                )
                summary.addView(label(kind, 12f, colors.muted))
                item.addView(label(entry.whenText, 11f, colors.muted).apply { gravity = Gravity.CENTER })
                item.addView(
                    label("›", 21f, colors.muted).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(px(18), -1),
                )
                rule(list)
            }
        }
        // Voicemail must be visible in Recents even if call history is empty.
        val vm =
            row().apply {
                setPadding(px(14), px(11), px(15), px(11))
                isClickable = true
                isFocusable = true
                contentDescription = "Open voicemail"
                setOnClickListener { openVoicemail() }
            }
        list.addView(vm, LinearLayout.LayoutParams(-1, px(60)))
        vm.addView(label("☏", 24f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(px(36), -1))
        val vtext = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        vm.addView(vtext, LinearLayout.LayoutParams(0, -2, 1f))
        vtext.addView(label("Voicemail", 15f, bold = true))
        vtext.addView(label("Open carrier voicemail", 12f, colors.muted))
        vm.addView(label("›", 22f, colors.muted))
        rule(list)
        floating(listArea, "⠿", "Open dialpad", Gravity.END or Gravity.BOTTOM, 15, 16) {
            screen = Screen.DIAL
            draw()
        }
    }

    private fun contactDetails(frame: FrameLayout) {
        val id =
            selectedContactId ?: run {
                screen = Screen.CONTACTS
                draw()
                return
            }
        if (!isGranted(Manifest.permission.READ_CONTACTS)) {
            val root = column()
            frame.addView(root)
            notice(root, "Contacts permission is required to view this contact")
            return
        }
        val person =
            data.people().firstOrNull { it.id == id } ?: run {
                screen = Screen.CONTACTS
                selectedContactId = null
                draw()
                return
            }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val body = column().apply { setPadding(px(13), px(8), px(13), px(13)) }
        scroll.addView(body)
        val photo = row().apply { gravity = Gravity.CENTER }
        body.addView(photo, LinearLayout.LayoutParams(-1, px(86)))
        photo.addView(
            TenSevenContactPhotos.avatar(this, id, person.name, 78, colors),
            LinearLayout.LayoutParams(px(78), px(78)),
        )
        body.addView(
            label(person.name, 23f, bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, px(37)),
        )
        val quick = row().apply { gravity = Gravity.CENTER }
        body.addView(quick, LinearLayout.LayoutParams(-1, px(74)))
        quickAction(quick, "✆", "Call") { placeCall(person.number) }
        quickAction(quick, "▤", "Message") { message(person.number) }
        quickAction(quick, "✎", "Edit") { openEditor(id) }
        // Approved: Block is immediately under the contact actions, never buried at bottom.
        val isBlocked = PhoneBlocklist.contains(this, person.number)
        val block =
            clickLabel(
                if (isBlocked) "⊘  Unblock contact" else "⊘  Block contact",
                if (isBlocked) "Unblock contact" else "Block contact",
                14f,
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
                background = round(colors.panel, 10, true)
                setTextColor(if (isBlocked) colors.muted else colors.fg)
            }
        body.addView(block, LinearLayout.LayoutParams(-1, px(41)).apply { bottomMargin = px(10) })
        rule(body)
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
            clickLabel(symbol, caption, 23f, action).apply {
                background = circular(colors.accent)
                setTextColor(Color.WHITE)
            }
        holder.addView(icon, LinearLayout.LayoutParams(px(43), px(43)))
        holder.addView(
            label(caption, 12f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, px(21)),
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
                setPadding(px(6), px(8), px(6), px(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        parent.addView(item, LinearLayout.LayoutParams(-1, px(52)))
        item.addView(label(symbol, 22f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(px(37), -1))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        item.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        texts.addView(label(title, 12f, colors.muted))
        texts.addView(label(detail, 14f))
        rule(parent)
    }

    private fun blocklist(frame: FrameLayout) {
        val scroll = ScrollView(this)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val body = column().apply { setPadding(px(13), px(10), px(13), px(16)) }
        scroll.addView(body)
        notice(body, "Numbers you explicitly block are screened when TenSeven holds the optional Android screening role.")
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
                if (value.length < 7) {
                    toast("Enter a full number")
                } else {
                    PhoneBlocklist.block(this, value)
                    draw()
                }
            }.apply { setTextColor(colors.accent) },
            LinearLayout.LayoutParams(-1, px(47)),
        )
        PhoneBlocklist.blocked(this).sorted().forEach { number ->
            body.addView(
                clickLabel("$number     Unblock", "Unblock $number") {
                    D5BbDialogs.confirm(this, "Unblock number", "Allow calls from $number?", "Unblock") {
                        PhoneBlocklist.unblock(this, number)
                        draw()
                    }
                },
                LinearLayout.LayoutParams(-1, px(43)),
            )
            rule(body)
        }
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
            LinearLayout.LayoutParams(-1, px(49)),
        )
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
                if (number.length < 7) {
                    toast("Enter a full number")
                } else {
                    PhoneBlocklist.allow(this, number)
                    draw()
                }
            },
            LinearLayout.LayoutParams(-1, px(46)),
        )
        PhoneBlocklist.allowed(this).sorted().forEach { number ->
            body.addView(
                clickLabel("$number     Remove", "Remove allowed $number") {
                    D5BbDialogs.confirm(this, "Remove exception", "Remove allowed number $number?", "Remove") {
                        PhoneBlocklist.unallow(this, number)
                        draw()
                    }
                },
                LinearLayout.LayoutParams(-1, px(43)),
            )
        }
        body.addView(
            clickLabel("Silence unknown: ${if (PhoneBlocklist.silenceUnknown(this)) "ON" else "OFF"}") {
                PhoneBlocklist.setSilenceUnknown(this, !PhoneBlocklist.silenceUnknown(this))
                draw()
            },
            LinearLayout.LayoutParams(-1, px(44)),
        )
        body.addView(
            clickLabel("Blocked call history", "View blocked calls") {
                if (!isGranted(Manifest.permission.READ_CALL_LOG)) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 202)
                } else {
                    val recent = data.calls().filter { it.kind == CallLog.Calls.BLOCKED_TYPE }.take(25)
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
            LinearLayout.LayoutParams(-1, px(44)),
        )
        notice(body, "Unknown calls are only silenced when screening is enabled and Contacts access is allowed.")
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

                3 -> {
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

    private fun openVoicemail() = openTrustedDialer(Uri.parse("voicemail:"))

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

    private fun floating(
        parent: FrameLayout,
        glyph: String,
        description: String,
        alignment: Int,
        right: Int,
        bottom: Int,
        action: () -> Unit,
    ) {
        val control =
            clickLabel(glyph, description, 28f, action).apply {
                background = circular(colors.accent)
                setTextColor(Color.WHITE)
                elevation = px(4).toFloat()
            }
        parent.addView(
            control,
            FrameLayout.LayoutParams(px(51), px(51), alignment).apply {
                rightMargin = px(right)
                bottomMargin = px(bottom)
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
            return
        }
        // Inline platform permission check: Android Lint can verify this guard.
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            queuedNumber = number
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 203)
            return
        }
        try {
            getSystemService(TelecomManager::class.java)?.placeCall(Uri.fromParts("tel", number, null), Bundle())
                ?: toast("Telecom is unavailable")
        } catch (_: Exception) {
            toast("Call could not start; check SIM and Phone access")
        }
    }

    override fun onRequestPermissionsResult(
        code: Int,
        names: Array<out String>,
        grants: IntArray,
    ) {
        super.onRequestPermissionsResult(code, names, grants)
        if (code == 203 && isGranted(Manifest.permission.CALL_PHONE)) {
            queuedNumber?.let { number ->
                queuedNumber = null
                placeCall(number)
            }
        }
        draw()
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
