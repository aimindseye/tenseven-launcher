package io.github.aimindseye.tenseven.features.phone

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val PHONE_DATA_PEOPLE_COL_2 = 2
private const val PHONE_DATA_PEOPLE_COL_3 = 3

private object PhoneDataLimits {
    const val PHOTO_CACHE_ENTRIES = 48
    const val PHOTO_WORKERS = 2
    const val LARGE_AVATAR_MIN_DP = 75
    const val LARGE_AVATAR_TEXT_SP = 28f
    const val SMALL_AVATAR_TEXT_SP = 16f
    const val MAX_CONTACTS = 1500
    const val MAX_CALL_HISTORY = 100
    const val MAX_PHONE_NUMBERS = 15
    const val MAX_EMAILS = 10
    const val RECENT_YESTERDAY_WINDOW_MS = 2L * 24L * 60L * 60L * 1000L
}

/** Shared by Phone and TenSevenTelecom. Never modify its public contract casually. */
internal class PhonePalette(
    @Suppress("UNUSED_PARAMETER") activity: Activity,
) {
    val bg: Int = Color.rgb(0, 0, 0)
    val panel: Int = Color.rgb(20, 23, 27)
    val fg: Int = Color.rgb(246, 247, 250)
    val muted: Int = Color.rgb(168, 174, 182)
    val line: Int = Color.rgb(53, 58, 65)
    val accent: Int = Color.rgb(0, 140, 225)
    val success: Int = Color.rgb(15, 165, 76)
    val danger: Int = Color.rgb(211, 62, 70)
}

/** Contacts photos remain on device. Decoded off UI thread with a bounded image cache. */
internal object TenSevenContactPhotos {
    private val cache = LruCache<String, Bitmap>(PhoneDataLimits.PHOTO_CACHE_ENTRIES)
    private val executor = Executors.newFixedThreadPool(PhoneDataLimits.PHOTO_WORKERS)
    private val mainThread = Handler(Looper.getMainLooper())

    fun lookupContactId(
        context: Context,
        number: String,
    ): Long? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager
                .PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class AvatarTarget(
        val key: String,
        val sizeDp: Int,
        val image: ImageView,
        val fallback: TextView,
    )

    private fun showAvatarBitmap(
        target: AvatarTarget,
        bitmap: Bitmap,
    ) {
        if (target.image.tag == target.key) {
            target.image.setImageBitmap(bitmap)
            target.image.visibility = View.VISIBLE
            target.fallback.visibility = View.GONE
        }
    }

    private fun showPhotoIfCurrent(
        imageRef: WeakReference<ImageView>,
        fallbackRef: WeakReference<TextView>,
        key: String,
        bitmap: Bitmap,
    ) {
        imageRef.get()?.let { current ->
            if (current.tag == key) {
                current.setImageBitmap(bitmap)
                current.visibility = View.VISIBLE
                fallbackRef.get()?.visibility = View.GONE
            }
        }
    }

    private fun decodeContactPhoto(
        resolver: android.content.ContentResolver,
        id: Long,
        large: Boolean,
    ): Bitmap? =
        try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri, large)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }

    private fun enqueueAvatarPhoto(
        context: Context,
        id: Long,
        target: AvatarTarget,
    ) {
        // Keep view references weak: a photo request must not retain a dismissed Phone screen.
        val key = target.key
        val large = target.sizeDp >= PhoneDataLimits.LARGE_AVATAR_MIN_DP
        val imageRef = WeakReference(target.image)
        val fallbackRef = WeakReference(target.fallback)
        val resolver = context.applicationContext.contentResolver
        executor.execute {
            val bmp = decodeContactPhoto(resolver, id, large)
            if (bmp != null) {
                synchronized(cache) { cache.put(key, bmp) }
                mainThread.post { showPhotoIfCurrent(imageRef, fallbackRef, key, bmp) }
            }
        }
    }

    fun avatar(
        context: Context,
        id: Long?,
        displayName: String,
        sizeDp: Int,
        palette: PhonePalette,
    ): FrameLayout {
        val frame =
            FrameLayout(context).apply {
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(palette.panel)
                        setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1), palette.line)
                    }
                clipToOutline = true
                contentDescription = displayName
            }
        val fallback =
            TextView(context).apply {
                text = PhoneUiRules.initials(displayName)
                textSize =
                    if (sizeDp >= PhoneDataLimits.LARGE_AVATAR_MIN_DP) {
                        PhoneDataLimits.LARGE_AVATAR_TEXT_SP
                    } else {
                        PhoneDataLimits.SMALL_AVATAR_TEXT_SP
                    }
                setTextColor(palette.fg)
                gravity = Gravity.CENTER
            }
        frame.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        if (id != null && id >= 1L &&
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        ) {
            val key = "$id:${sizeDp >= PhoneDataLimits.LARGE_AVATAR_MIN_DP}"
            val image =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    tag = key
                    visibility = View.GONE
                }
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val target = AvatarTarget(key, sizeDp, image, fallback)
            val cached = synchronized(cache) { cache.get(key) }
            if (cached != null) {
                showAvatarBitmap(target, cached)
            } else {
                enqueueAvatarPhoto(context, id, target)
            }
        }
        return frame
    }
}

/** Queries Android providers only when permissions are granted. No network or local contact copy. */
internal class TenSevenPhoneData(
    private val activity: Activity,
) {
    data class Person(
        val id: Long,
        val name: String,
        val number: String,
        val favorite: Boolean,
    )

    data class Call(
        val number: String,
        val kind: Int,
        val timestampMs: Long,
        val whenText: String,
    )

    private fun allowed(permission: String) =
        activity.checkSelfPermission(permission) ==
            PackageManager
                .PERMISSION_GRANTED

    fun people(): List<Person> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = mutableListOf<Person>()
        try {
            activity.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.STARRED,
                    ),
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
                )?.use { c ->
                    while (c.moveToNext() && result.size < PhoneDataLimits.MAX_CONTACTS) {
                        result.add(
                            Person(
                                c.getLong(0),
                                c.getString(1).orEmpty(),
                                c.getString(PHONE_DATA_PEOPLE_COL_2).orEmpty(),
                                c.getInt(PHONE_DATA_PEOPLE_COL_3) != 0,
                            ),
                        )
                    }
                }
        } catch (_: Exception) {
            // Permission can be revoked while activity is visible.
        }
        return result
    }

    private fun callTimeLabel(timestamp: Long): String {
        val day = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        return when {
            day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
            }

            today.timeInMillis - timestamp < PhoneDataLimits.RECENT_YESTERDAY_WINDOW_MS -> {
                "Yesterday"
            }

            else -> {
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
            }
        }
    }

    fun calls(): List<Call> {
        if (!allowed(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val rows = mutableListOf<Call>()
        try {
            activity.contentResolver
                .query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC",
                )?.use { cursor ->
                    while (cursor.moveToNext() && rows.size < PhoneDataLimits.MAX_CALL_HISTORY) {
                        val timestamp = cursor.getLong(2)
                        rows.add(
                            Call(
                                cursor.getString(0).orEmpty(),
                                cursor.getInt(1),
                                timestamp,
                                callTimeLabel(timestamp),
                            ),
                        )
                    }
                }
        } catch (_: Exception) {
        }
        return rows
    }

    fun numbers(contactId: Long): List<Pair<String, String>> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        try {
            activity.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL,
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                    arrayOf(contactId.toString()),
                    null,
                )?.use { c ->
                    while (c.moveToNext() && result.size < PhoneDataLimits.MAX_PHONE_NUMBERS) {
                        appendNumberFromCursor(c, result)
                    }
                }
        } catch (_: Exception) {
        }
        return result
    }

    private fun appendNumberFromCursor(
        cursor: android.database.Cursor,
        result: MutableList<Pair<String, String>>,
    ) {
        val number = cursor.getString(0).orEmpty()
        if (number.isNotBlank() && result.none { it.second == number }) {
            val label =
                ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(activity.resources, cursor.getInt(1), cursor.getString(2))
                    .toString()
            result.add(label to number)
        }
    }

    fun emails(contactId: Long): List<String> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = mutableListOf<String>()
        try {
            activity.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
                    arrayOf(contactId.toString()),
                    null,
                )?.use { c ->
                    while (c.moveToNext() && result.size < PhoneDataLimits.MAX_EMAILS) {
                        c.getString(0)?.takeIf(String::isNotBlank)?.let(result::add)
                    }
                }
        } catch (_: Exception) {
        }
        return result.distinct()
    }

    fun company(contactId: Long): String? {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return null
        return try {
            activity.contentResolver
                .query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                    "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                    arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }
}
