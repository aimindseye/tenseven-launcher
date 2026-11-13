package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.D5BbDialogs
import io.github.aimindseye.tenseven.D5PhoneRoleRequestActivity
import io.github.aimindseye.tenseven.platform.TenSevenSafeViewport

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
internal const val PHONE_BLOCKLIST_MAX_25 = 25
internal const val PHONE_BLOCKLIST_VALUE_7 = 7
internal const val PHONE_CONTACTS_VALUE_14 = 14
private const val PHONE_CONTACT_DETAILS_SP_23_F = 23f
private const val PHONE_CONTACT_DETAILS_VALUE_10 = 10
private const val PHONE_CONTACT_DETAILS_VALUE_78 = 78
private const val PHONE_DETAIL_HEADER_RATIO_24_F = 24f
private const val PHONE_DETAIL_HEADER_SP_16_F = 16f
internal const val PHONE_DIAL_DP_1 = 1
internal const val PHONE_DIAL_DP_43 = 43
internal const val PHONE_DIAL_DP_52 = 52
internal const val PHONE_DIAL_RATIO_25_F = 25f
internal const val PHONE_DIAL_SP_24_F = 24f
internal const val PHONE_DIAL_SP_28_F = 28f
internal const val PHONE_FLOATING_RATIO_28_F = 28f
private const val PHONE_INFO_ROW_DP_2 = 2
internal const val PHONE_KEYPAD_DP_126 = 126
internal const val PHONE_KEYPAD_DP_145 = 145
internal const val PHONE_KEYPAD_DP_8 = 8
internal const val PHONE_KEYPAD_RATIO_9_F = 9f
internal const val PHONE_KEYPAD_SP_10_F = 10f
internal const val PHONE_KEYPAD_SP_23_F = 23f
internal const val PHONE_KEYPAD_SP_9_F = 9f
internal const val PHONE_KEYPAD_VALUE_15 = 15
internal const val PHONE_KEYPAD_VALUE_16 = 16
internal const val PHONE_KEYPAD_VALUE_24 = 24
internal const val PHONE_KEYPAD_VALUE_3 = 3
internal const val PHONE_MATCHES_DP_1 = 1
internal const val PHONE_MATCHES_DP_2 = 2
internal const val PHONE_MATCHES_DP_36 = 36
internal const val PHONE_MATCHES_DP_37 = 37
internal const val PHONE_MATCHES_DP_43 = 43
internal const val PHONE_MATCHES_DP_44 = 44
internal const val PHONE_MATCHES_SP_11_F = 11f
internal const val PHONE_MATCHES_VALUE_32 = 32
private const val PHONE_OPEN_MENU_VALUE_3 = 3
internal const val PHONE_POPULATE_VALUE_36 = 36
private const val PHONE_QUICK_ACTION_RATIO_23_F = 23f
internal const val PHONE_RECENTS_DP_1 = 1
internal const val PHONE_RECENTS_DP_2 = 2
internal const val PHONE_RECENTS_RATIO_25_F = 25f
internal const val PHONE_RECENTS_SP_11_F = 11f
internal const val PHONE_RECENTS_SP_21_F = 21f
internal const val PHONE_RECENTS_SP_24_F = 24f
internal const val PHONE_RECENTS_VALUE_15 = 15
internal const val PHONE_RECENTS_VALUE_16 = 16

internal const val PHONE_AUXILIARY_INSET_DP = 15
private const val PHONE_DETAIL_CAPTION_HEIGHT_DP = 21
internal const val PHONE_MINI_ICON_EDGE_DP = 24
internal const val PHONE_CONTACT_LETTER_HEADING_DP = 26
internal const val PHONE_LIST_FOOTER_INSET_DP = 29
internal const val PHONE_COMPACT_AVATAR_EDGE_DP = 32
private const val PHONE_OPTIONS_ICON_EDGE_DP = 35
internal const val PHONE_CONTACT_AVATAR_EDGE_DP = 36
private const val PHONE_COMPACT_MATCH_HEIGHT_DP = 37
internal const val PHONE_CALL_TYPE_ICON_WIDTH_DP = 39
private const val PHONE_BLOCK_ACTION_HEIGHT_DP = 41
internal const val PHONE_SEARCH_AND_ACTION_HEIGHT_DP = 42
internal const val PHONE_COMPACT_NAVIGATION_HEIGHT_DP = 43
internal const val PHONE_STANDARD_NAVIGATION_HEIGHT_DP = 44
internal const val PHONE_MENU_ACTION_HEIGHT_DP = 46
internal const val PHONE_MENU_ROW_HEIGHT_DP = 47
internal const val PHONE_CONTACT_OR_MENU_ROW_DP = 49
internal const val PHONE_FLOATING_ACTION_EDGE_DP = 51
private const val PHONE_EXPANDED_INPUT_HEIGHT_DP = 52
internal const val PHONE_SEARCH_BAND_HEIGHT_DP = 54
internal const val PHONE_VOICEMAIL_ACTION_HEIGHT_DP = 60
internal const val PHONE_RECENT_CALL_ROW_HEIGHT_DP = 62
internal const val PHONE_ALPHABET_SCROLL_MARGIN_DP = 64
internal const val PHONE_CONTACT_LIST_BOTTOM_INSET_DP = 70
private const val PHONE_CONTACT_QUICK_ACTION_HEIGHT_DP = 74
internal const val PHONE_RECENTS_BOTTOM_INSET_DP = 75
private const val PHONE_CONTACT_PHOTO_EDGE_DP = 78
private const val PHONE_CONTACT_PHOTO_AREA_HEIGHT_DP = 86
internal const val PHONE_HAIRLINE_DP = 1
internal const val PHONE_MICRO_INSET_DP = 2
internal const val PHONE_EXTRA_TIGHT_INSET_DP = 3
internal const val PHONE_TIGHT_INSET_DP = 4
internal const val PHONE_COMPACT_INSET_DP = 5
internal const val PHONE_SMALL_INSET_DP = 6
internal const val PHONE_NARROW_GUTTER_DP = 7
internal const val PHONE_STANDARD_INSET_DP = 8
internal const val PHONE_ROW_CONTENT_GUTTER_DP = 9
internal const val PHONE_SECTION_INSET_DP = 10
internal const val PHONE_LIST_CONTROL_INSET_DP = 11
internal const val PHONE_PAGE_CONTENT_INSET_DP = 12
internal const val PHONE_LIST_CONTENT_INSET_DP = 13
internal const val PHONE_DETAIL_CONTENT_INSET_DP = 14
internal const val PHONE_CALLOUT_CONTENT_INSET_DP = 16
internal const val PHONE_CALLOUT_TOP_INSET_DP = 18
internal const val PHONE_BODY_TEXT_SP = 15f
internal const val PHONE_CAPTION_TEXT_SP = 12f
internal const val PHONE_SECONDARY_TEXT_SP = 14f
internal const val PHONE_COMPACT_TEXT_SP = 13f
internal const val PHONE_SYMBOL_TEXT_SP = 22f
internal const val PHONE_LARGE_SYMBOL_TEXT_SP = 29f
internal const val PHONE_REQUEST_CONTACTS = 201
internal const val PHONE_REQUEST_CALL_LOG = 202
private const val PHONE_REQUEST_CALL = 203

/**
 * D5-R9.1: FULL REWRITE of the Phone activity's presentation.
 * Approved contract: one top Dial / Contacts / Recents navigation; fixed Dial call action;
 * inline number matches; A–Z contacts; filters, voicemail, photos and top-level block action.
 *
 * This file does not own Android call state. TenSevenInCallService remains in TenSevenTelecom.kt.
 * It does not change Android default dialer roles without an explicit user action.
 */
class TenSevenPhoneActivity : ComponentActivity() {
    private val blocklistContent by lazy { PhoneBlocklistContent(this) }
    private val recentsContent by lazy {
        PhoneRecentsContent(this, { id -> openContact(id, Screen.RECENTS) }, ::openVoicemail, ::defaultPhoneSetup)
    }
    private val contactsContent by lazy {
        PhoneContactsContent(this, { id -> openContact(id, Screen.CONTACTS) }, ::addContact)
    }
    private val dialContent by lazy { PhoneDialContent(this) }

    internal enum class Screen { DIAL, CONTACTS, RECENTS, CONTACT_DETAIL, BLOCKLIST }

    internal var screen = Screen.DIAL
    private var returnTo = Screen.CONTACTS
    private var selectedContactId: Long? = null
    internal var dialNumber = ""
    internal var contactSearch = ""
    internal var callFilter = "All"
    private var queuedNumber: String? = null
    internal val views by lazy { PhoneViews(this) }
    private val colors get() = views.colors
    internal val data by lazy { TenSevenPhoneData(this) }

    internal fun isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

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

    internal fun draw() {
        TenSevenSafeViewport.darkSystemBars(this)
        val shell = views.column()
        if (screen == Screen.CONTACT_DETAIL || screen == Screen.BLOCKLIST) {
            detailHeader(shell)
        } else {
            tabs(shell)
        }
        val body = FrameLayout(this)
        shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        when (screen) {
            Screen.DIAL -> dialContent.dial(body)
            Screen.CONTACTS -> contactsContent.contacts(body)
            Screen.RECENTS -> recentsContent.recents(body)
            Screen.CONTACT_DETAIL -> contactDetails(body)
            Screen.BLOCKLIST -> blocklistContent.blocklist(body)
        }
        TenSevenSafeViewport.show(this, shell)
        Log.i("TenSevenD5R9R1", "Phone rebuild active: ${screen.name}")
    }

    /** The only main navigation bar; no redundant 'TenSeven Phone' heading. */
    private fun tabs(shell: LinearLayout) {
        val navigation = views.row()
        shell.addView(navigation, LinearLayout.LayoutParams(-1, views.px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP)))
        val tabRegion = views.row()
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
                views.label(title, PHONE_BODY_TEXT_SP, if (selected) colors.fg else colors.muted).apply {
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
            tab.addView(
                View(this).apply { setBackgroundColor(if (selected) colors.accent else colors.bg) },
                LinearLayout.LayoutParams(-1, views.px(PHONE_EXTRA_TIGHT_INSET_DP)),
            )
            tabRegion.addView(tab, LinearLayout.LayoutParams(0, -1, 1f))
        }
        navigation.addView(
            views.clickLabel("⋮", "Phone options", PHONE_SYMBOL_TEXT_SP) { openMenu() },
            LinearLayout.LayoutParams(views.px(PHONE_OPTIONS_ICON_EDGE_DP), -1),
        )
        views.rule(shell)
    }

    private fun detailHeader(shell: LinearLayout) {
        val head =
            views.row().apply {
                setPadding(
                    views.px(PHONE_NARROW_GUTTER_DP),
                    0,
                    views.px(PHONE_STANDARD_INSET_DP),
                    0,
                )
            }
        shell.addView(head, LinearLayout.LayoutParams(-1, views.px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP)))
        head.addView(
            views.clickLabel("‹", "Back", PHONE_LARGE_SYMBOL_TEXT_SP) {
                onBackPressedDispatcher.onBackPressed()
            },
            LinearLayout.LayoutParams(views.px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP), -1),
        )
        head.addView(
            views.label(if (screen == Screen.BLOCKLIST) "Spam & Blocklist" else "", PHONE_DETAIL_HEADER_SP_16_F),
            LinearLayout.LayoutParams(0, -1, 1f),
        )
        if (screen == Screen.CONTACT_DETAIL) {
            val favorite = selectedContactId?.let { id -> data.people().firstOrNull { it.id == id }?.favorite } == true
            head.addView(
                views.clickLabel(
                    if (favorite) "★" else "☆",
                    "Edit favorite in Contacts",
                    PHONE_DETAIL_HEADER_RATIO_24_F,
                ) {
                    selectedContactId?.let(::openEditor)
                },
                LinearLayout.LayoutParams(views.px(PHONE_STANDARD_NAVIGATION_HEIGHT_DP), -1),
            )
        }
        head.addView(
            views.clickLabel("⋮", "Phone options", PHONE_SYMBOL_TEXT_SP) { openMenu() },
            LinearLayout.LayoutParams(views.px(PHONE_SEARCH_AND_ACTION_HEIGHT_DP), -1),
        )
        views.rule(shell)
    }

    private fun contactDetails(frame: FrameLayout) {
        val id = selectedContactId
        when {
            id == null -> {
                screen = Screen.CONTACTS
                draw()
            }

            !isGranted(Manifest.permission.READ_CONTACTS) -> {
                val root = views.column()
                frame.addView(root)
                views.notice(root, "Contacts permission is required to view this contact")
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
            views.column().apply {
                setPadding(
                    views.px(PHONE_LIST_CONTENT_INSET_DP),
                    views.px(PHONE_STANDARD_INSET_DP),
                    views.px(PHONE_LIST_CONTENT_INSET_DP),
                    views.px(PHONE_LIST_CONTENT_INSET_DP),
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
        val photo = views.row().apply { gravity = Gravity.CENTER }
        body.addView(photo, LinearLayout.LayoutParams(-1, views.px(PHONE_CONTACT_PHOTO_AREA_HEIGHT_DP)))
        photo.addView(
            TenSevenContactPhotos.avatar(this, id, person.name, PHONE_CONTACT_DETAILS_VALUE_78, colors),
            LinearLayout.LayoutParams(views.px(PHONE_CONTACT_PHOTO_EDGE_DP), views.px(PHONE_CONTACT_PHOTO_EDGE_DP)),
        )
        body.addView(
            views.label(person.name, PHONE_CONTACT_DETAILS_SP_23_F, bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, views.px(PHONE_COMPACT_MATCH_HEIGHT_DP)),
        )
        val quick = views.row().apply { gravity = Gravity.CENTER }
        body.addView(quick, LinearLayout.LayoutParams(-1, views.px(PHONE_CONTACT_QUICK_ACTION_HEIGHT_DP)))
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
            views
                .clickLabel(
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
                    background = views.round(colors.panel, PHONE_CONTACT_DETAILS_VALUE_10, true)
                    setTextColor(if (isBlocked) colors.muted else colors.fg)
                }
        body.addView(
            block,
            LinearLayout.LayoutParams(-1, views.px(PHONE_BLOCK_ACTION_HEIGHT_DP)).apply {
                bottomMargin =
                    views.px(PHONE_SECTION_INSET_DP)
            },
        )
        views.rule(body)
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
            views.clickLabel(symbol, caption, PHONE_QUICK_ACTION_RATIO_23_F, action).apply {
                background = views.circular(colors.accent)
                setTextColor(Color.WHITE)
            }
        holder.addView(
            icon,
            LinearLayout.LayoutParams(
                views.px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP),
                views.px(PHONE_COMPACT_NAVIGATION_HEIGHT_DP),
            ),
        )
        holder.addView(
            views.label(caption, PHONE_CAPTION_TEXT_SP).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, views.px(PHONE_DETAIL_CAPTION_HEIGHT_DP)),
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
            views.row().apply {
                setPadding(
                    views.px(PHONE_SMALL_INSET_DP),
                    views.px(PHONE_STANDARD_INSET_DP),
                    views.px(PHONE_SMALL_INSET_DP),
                    views.px(PHONE_STANDARD_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        parent.addView(item, LinearLayout.LayoutParams(-1, views.px(PHONE_EXPANDED_INPUT_HEIGHT_DP)))
        item.addView(
            views.label(symbol, PHONE_SYMBOL_TEXT_SP).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(views.px(PHONE_COMPACT_MATCH_HEIGHT_DP), -1),
        )
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        item.addView(texts, LinearLayout.LayoutParams(0, -PHONE_INFO_ROW_DP_2, 1f))
        texts.addView(views.label(title, PHONE_CAPTION_TEXT_SP, colors.muted))
        texts.addView(views.label(detail, PHONE_SECONDARY_TEXT_SP))
        views.rule(parent)
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

    internal fun safeStart(intent: Intent) {
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

    internal fun placeCall(raw: String) {
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

    internal fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
