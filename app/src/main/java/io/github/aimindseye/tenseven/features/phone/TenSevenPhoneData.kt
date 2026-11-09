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

/** Shared by Phone and TenSevenTelecom. Never modify its public contract casually. */
internal class PhonePalette(@Suppress("UNUSED_PARAMETER") activity: Activity) {
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
    private val cache = LruCache<String, Bitmap>(48)
    private val executor = Executors.newFixedThreadPool(2)
    private val mainThread = Handler(Looper.getMainLooper())

    fun lookupContactId(context: Context, number: String): Long? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (_: Exception) { null }
    }

    fun avatar(context: Context, id: Long?, displayName: String, sizeDp: Int, palette: PhonePalette): FrameLayout {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val frame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.panel)
                setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1), palette.line)
            }
            clipToOutline = true
            contentDescription = displayName
        }
        val fallback = TextView(context).apply {
            text = PhoneUiRules.initials(displayName)
            textSize = if (sizeDp >= 75) 28f else 16f
            setTextColor(palette.fg)
            gravity = Gravity.CENTER
        }
        frame.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        if (id == null || id < 1L ||
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return frame
        val key = "$id:${sizeDp >= 75}"
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = key; visibility = View.GONE }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        fun applyBitmap(bitmap: Bitmap) {
            if (image.tag == key) {
                image.setImageBitmap(bitmap)
                image.visibility = View.VISIBLE
                fallback.visibility = View.GONE
            }
        }
        synchronized(cache) { cache.get(key) }?.let { applyBitmap(it); return frame }
        val imageRef = WeakReference(image)
        val fallbackRef = WeakReference(fallback)
        val resolver = context.applicationContext.contentResolver
        executor.execute {
            val bmp = try {
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
                ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri, sizeDp >= 75)?.use { stream -> BitmapFactory.decodeStream(stream) }
            } catch (_: Exception) { null }
            if (bmp != null) {
                synchronized(cache) { cache.put(key, bmp) }
                mainThread.post {
                    imageRef.get()?.let { current ->
                        if (current.tag == key) {
                            current.setImageBitmap(bmp)
                            current.visibility = View.VISIBLE
                            fallbackRef.get()?.visibility = View.GONE
                        }
                    }
                }
            }
        }
        return frame
    }
}

/** Queries Android providers only when permissions are granted. No network or local contact copy. */
internal class TenSevenPhoneData(private val activity: Activity) {
    data class Person(val id: Long, val name: String, val number: String, val favorite: Boolean)
    data class Call(val number: String, val kind: Int, val timestampMs: Long, val whenText: String)
    private fun allowed(permission: String) =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun people(): List<Person> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = mutableListOf<Person>()
        try {
            activity.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.STARRED),
                null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC")?.use { c ->
                while (c.moveToNext() && result.size < 1500) {
                    result.add(Person(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3) != 0))
                }
            }
        } catch (_: Exception) { /* Permission can be revoked while activity is visible. */ }
        return result
    }

    fun calls(): List<Call> {
        if (!allowed(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val rows = mutableListOf<Call>()
        try {
            activity.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                while (cursor.moveToNext() && rows.size < 100) {
                    val timestamp = cursor.getLong(2)
                    val day = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val today = Calendar.getInstance()
                    val label = when {
                        day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
                        today.timeInMillis - timestamp < 2L * 24 * 60 * 60 * 1000 -> "Yesterday"
                        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
                    }
                    rows.add(Call(cursor.getString(0).orEmpty(), cursor.getInt(1), timestamp, label))
                }
            }
        } catch (_: Exception) { }
        return rows
    }

    fun numbers(contactId: Long): List<Pair<String, String>> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        try {
            activity.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(contactId.toString()), null)?.use { c ->
                while (c.moveToNext() && result.size < 15) {
                    val number = c.getString(0).orEmpty()
                    if (number.isNotBlank() && result.none { it.second == number }) {
                        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(activity.resources, c.getInt(1), c.getString(2)).toString()
                        result.add(label to number)
                    }
                }
            }
        } catch (_: Exception) { }
        return result
    }

    fun emails(contactId: Long): List<String> {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = mutableListOf<String>()
        try {
            activity.contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?", arrayOf(contactId.toString()), null)?.use { c ->
                while (c.moveToNext() && result.size < 10) {
                    c.getString(0)?.takeIf(String::isNotBlank)?.let(result::add)
                }
            }
        } catch (_: Exception) { }
        return result.distinct()
    }

    fun company(contactId: Long): String? {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return null
        return try {
            activity.contentResolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE), null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }
}
