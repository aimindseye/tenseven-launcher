package io.github.aimindseye.tenseven

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Test APK only; serves one generated Word fixture, never arbitrary paths or user data. */
class FrameDocumentFixtureProvider : ContentProvider() {
    private lateinit var fixture: File
    override fun onCreate(): Boolean {
        fixture = File(context!!.cacheDir, "frame-word-fixture.docx")
        ZipOutputStream(fixture.outputStream()).use {
            it.putNextEntry(ZipEntry("word/document.xml"))
            it.write("<w:document xmlns:w='urn:word'><w:t>Verified saved Word content</w:t></w:document>".toByteArray())
            it.closeEntry()
        }
        return true
    }
    private fun check(uri: Uri) { require(uri.path == "/fixture.docx") }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        check(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("fixture.docx")) }
    }
    override fun getType(uri: Uri): String { check(uri); return "application/vnd.openxmlformats-officedocument.wordprocessingml.document" }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(uri); require(mode == "r")
        return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
