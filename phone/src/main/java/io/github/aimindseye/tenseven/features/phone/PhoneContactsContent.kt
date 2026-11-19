package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Activity-bound PhoneContactsContent renderer; lifecycle and mutable state remain with the activity. */
internal class PhoneContactsContent(
    private val activity: TenSevenPhoneActivity,
    private val onOpenContact: (Long) -> Unit,
    private val onAddContact: () -> Unit,
) {
    private fun appendPhoneContactRow(
        list: LinearLayout,
        person: TenSevenPhoneData.Person,
    ) {
        val entry =
            activity.views.row().apply {
                setPadding(
                    activity.views.px(PHONE_PAGE_CONTENT_INSET_DP),
                    activity.views.px(PHONE_TIGHT_INSET_DP),
                    activity.views.px(PHONE_STANDARD_INSET_DP),
                    activity.views.px(PHONE_TIGHT_INSET_DP),
                )
                isClickable = true
                isFocusable = true
                contentDescription = "Open ${person.name}"
                setOnClickListener { onOpenContact(person.id) }
            }
        list.addView(entry, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_CONTACT_OR_MENU_ROW_DP)))
        entry.addView(
            TenSevenContactPhotos.avatar(
                activity,
                person.id,
                person.name,
                PHONE_POPULATE_VALUE_36,
                activity.views.colors,
            ),
            LinearLayout.LayoutParams(
                activity.views.px(PHONE_CONTACT_AVATAR_EDGE_DP),
                activity.views.px(PHONE_CONTACT_AVATAR_EDGE_DP),
            ),
        )
        entry.addView(
            activity.views.label(person.name, PHONE_BODY_TEXT_SP).apply {
                setPadding(
                    activity.views.px(PHONE_PAGE_CONTENT_INSET_DP),
                    0,
                    0,
                    0,
                )
            },
            LinearLayout.LayoutParams(0, -1, 1f),
        )
        activity.views.rule(list)
    }

    fun contacts(frame: FrameLayout) {
        val container = activity.views.column()
        frame.addView(container, FrameLayout.LayoutParams(-1, -1))
        if (!activity.isGranted(Manifest.permission.READ_CONTACTS)) {
            activity.views.notice(
                container,
                "Allow Contacts to show people stored on this device. No contact information is uploaded.",
                "Allow Contacts",
            ) { activity.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), PHONE_REQUEST_CONTACTS) }
            return
        }
        val people = activity.data.people().distinctBy { it.id }
        val search = phoneContactsSearch(container)
        val area = FrameLayout(activity)
        container.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        val scroll =
            ScrollView(activity).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
        area.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val list =
            activity.views.column().apply {
                setPadding(
                    0,
                    0,
                    activity.views.px(PHONE_LIST_FOOTER_INSET_DP),
                    activity.views.px(PHONE_CONTACT_LIST_BOTTOM_INSET_DP),
                )
            }
        scroll.addView(list)
        val sections = mutableMapOf<String, View>()

        attachPhoneContactSearch(search, people, list, sections)
        populatePhoneContacts(activity.contactSearch, people, list, sections)
        installPhoneAlphabet(area, scroll, sections)
        activity.views.floating(
            area,
            "+",
            "Add contact",
            FloatingPosition(Gravity.END or Gravity.BOTTOM, PHONE_KEYPAD_VALUE_15, PHONE_KEYPAD_VALUE_16),
        ) { onAddContact() }
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
        activity.contactSearch = query
        list.removeAllViews()
        sections.clear()
        val filtered = matchingPhoneContacts(query, people)
        if (filtered.isEmpty()) {
            activity.views.notice(list, "No matching contacts")
            return
        }
        var previous = ""
        filtered.forEach { person ->
            val letter = PhoneUiRules.firstLetter(person.name)
            if (letter != previous) {
                val heading =
                    activity.views.label(letter, PHONE_COMPACT_TEXT_SP, bold = true).apply {
                        setPadding(
                            activity.views.px(PHONE_LIST_CONTENT_INSET_DP),
                            activity.views.px(PHONE_EXTRA_TIGHT_INSET_DP),
                            activity.views.px(PHONE_ROW_CONTENT_GUTTER_DP),
                            activity.views.px(PHONE_EXTRA_TIGHT_INSET_DP),
                        )
                        setBackgroundColor(activity.views.colors.panel)
                    }
                list.addView(heading, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_CONTACT_LETTER_HEADING_DP)))
                sections[letter] = heading
                previous = letter
            }
            appendPhoneContactRow(list, person)
        }
    }

    private fun phoneContactsSearch(container: LinearLayout): EditText {
        val search =
            EditText(activity).apply {
                setSingleLine(true)
                textSize = PHONE_BODY_TEXT_SP
                hint = "⌕  Search contacts"
                setTextColor(activity.views.colors.fg)
                setHintTextColor(activity.views.colors.muted)
                background = activity.views.round(activity.views.colors.panel, PHONE_CONTACTS_VALUE_14, true)
                setPadding(
                    activity.views.px(PHONE_LIST_CONTENT_INSET_DP),
                    0,
                    activity.views.px(PHONE_LIST_CONTENT_INSET_DP),
                    0,
                )
                setText(activity.contactSearch)
            }
        val searchBand =
            activity.views.row().apply {
                setPadding(
                    activity.views.px(PHONE_PAGE_CONTENT_INSET_DP),
                    activity.views.px(PHONE_COMPACT_INSET_DP),
                    activity.views.px(PHONE_PAGE_CONTENT_INSET_DP),
                    activity.views.px(PHONE_SMALL_INSET_DP),
                )
            }
        container.addView(searchBand, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_SEARCH_BAND_HEIGHT_DP)))
        searchBand.addView(search, LinearLayout.LayoutParams(-1, activity.views.px(PHONE_SEARCH_AND_ACTION_HEIGHT_DP)))
        return search
    }

    private fun installPhoneAlphabet(
        area: FrameLayout,
        scroll: ScrollView,
        sections: Map<String, View>,
    ) {
        val alphabet =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
        area.addView(
            alphabet,
            FrameLayout.LayoutParams(activity.views.px(PHONE_MINI_ICON_EDGE_DP), -1, Gravity.END).apply {
                bottomMargin = activity.views.px(PHONE_ALPHABET_SCROLL_MARGIN_DP)
            },
        )
        "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { c ->
            val item =
                activity.views.clickLabel(c.toString(), "Jump to $c", PHONE_KEYPAD_RATIO_9_F) {
                    val anchor = sections[c.toString()]
                    if (anchor != null) scroll.post { scroll.smoothScrollTo(0, anchor.top) }
                }
            item.setTextColor(activity.views.colors.muted)
            alphabet.addView(item, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }
}

/** Name and digit-substring matching, preserving order and duplicate provider entries. */
internal fun matchingPhoneContacts(
    query: String,
    people: List<TenSevenPhoneData.Person>,
): List<TenSevenPhoneData.Person> {
    val normalized = query.filter(Char::isDigit)
    return people.filter {
        it.name.contains(query, ignoreCase = true) ||
            (normalized.isNotEmpty() && it.number.filter(Char::isDigit).contains(normalized))
    }
}
