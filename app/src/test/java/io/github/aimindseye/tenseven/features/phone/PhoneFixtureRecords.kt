package io.github.aimindseye.tenseven.features.phone

import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.aimindseye.tenseven.features.AndroidViewFixtures

internal object PhoneFixtureRecords {
    const val FIRST_NUMBER = "+15550100001"
    const val SECOND_NUMBER = "+15550100002"
    const val UNKNOWN_NUMBER = "+15550100003"
    const val FIRST_ID = 1L

    fun contacts() =
        AndroidViewFixtures.provider(CONTACTS_AUTHORITY).apply {
            records["/data/phones"] =
                listOf(
                    person(FIRST_ID, "Alex Fixture", FIRST_NUMBER, true),
                    person(2L, "Blair Fixture", SECOND_NUMBER, false),
                )
        }

    private const val CONTACTS_AUTHORITY = "com.android.contacts"

    private fun person(
        id: Long,
        name: String,
        number: String,
        starred: Boolean,
    ): Map<String, Any> =
        mapOf(
            Phone.CONTACT_ID to id,
            Phone.DISPLAY_NAME to name,
            Phone.NUMBER to number,
            Phone.STARRED to if (starred) 1 else 0,
        )

    fun calls() =
        AndroidViewFixtures.provider(CallLog.AUTHORITY).apply {
            records["/calls"] =
                listOf(
                    call(FIRST_NUMBER, CallLog.Calls.MISSED_TYPE),
                    call(SECOND_NUMBER, CallLog.Calls.INCOMING_TYPE),
                    call(UNKNOWN_NUMBER, CallLog.Calls.OUTGOING_TYPE),
                    call("", CallLog.Calls.BLOCKED_TYPE),
                    call(UNKNOWN_NUMBER, CallLog.Calls.REJECTED_TYPE),
                )
        }

    private fun call(
        number: String,
        kind: Int,
    ): Map<String, Any> = mapOf(CallLog.Calls.NUMBER to number, CallLog.Calls.TYPE to kind, CallLog.Calls.DATE to 0L)
}
