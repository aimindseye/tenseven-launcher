package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.OpenableColumns
import android.util.Xml
import io.github.aimindseye.tenseven.frames.*
import org.xmlpull.v1.XmlPullParser

/** Only URIs explicitly chosen through Android's picker. Excerpts are not written to disk. */
class OfficeFrameDocuments(private val context: Context) {
    private val preferences = FrameIntegrationPreferences(context)
    private val cached = mutableMapOf<String, Pair<Long, AppSummary>>()
    fun summaries(now: Long): List<AppSummary> = KINDS.mapNotNull { kind ->
        val raw = preferences.document(kind) ?: return@mapNotNull null
        val uri = raw.toUri()
        val key = kind + ":" + raw
        cached[key]?.takeIf { now - it.first in 0..30_000 }?.let { return@mapNotNull it.second }
        val result = runCatching {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Selected file"
            val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val expected = mapOf("word" to "docx", "excel" to "xlsx", "powerpoint" to "pptx")[kind]
            val excerpt = if (extension == expected) runCatching {
                context.contentResolver.openInputStream(uri)?.use { OfficeArchive.excerpt(it, kind, ::xmlText) }
                    ?: "Preview unavailable"
            }.getOrDefault("Preview unavailable; open the selected file") else "File selected · content preview unavailable for this format"
            AppSummary("office:" + kind, kind.replaceFirstChar(Char::uppercase), "$name · $excerpt",
                SummarySource.Document, PACKAGES[kind], now)
        }.getOrElse {
            AppSummary("office:" + kind, kind.replaceFirstChar(Char::uppercase), "File unavailable · choose it again in App integrations",
                SummarySource.Document, PACKAGES[kind], now)
        }
        cached.keys.removeAll { it.startsWith(kind + ":") }
        cached[key] = now to result
        result
    }

    fun open(kind: String) {
        val raw = preferences.document(kind) ?: return
        val uri = raw.toUri()
        if (uri.scheme != "content") return
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: MIME[kind])
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pkg = PACKAGES[kind]
        if (pkg != null && context.packageManager.resolveActivity(Intent(intent).setPackage(pkg), 0) != null) intent.setPackage(pkg)
        runCatching { context.startActivity(intent) }
    }

    internal fun xmlText(xml: String): String {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml.reader())
        val out = StringBuilder()
        var capture = false
        var openTags = 0
        var sawRoot = false
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.END_DOCUMENT -> { require(sawRoot && openTags == 0) { "Incomplete document" }; break }
                XmlPullParser.DOCDECL -> error("Document types are not supported")
                XmlPullParser.START_TAG -> { openTags++; sawRoot = true; capture = parser.name in setOf("t", "v") }
                XmlPullParser.END_TAG -> { openTags--; if (capture) out.append(' '); capture = false }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (capture && out.length < 1000) out.append(parser.text.take(1000 - out.length))
                XmlPullParser.ENTITY_REF -> if (capture) {
                    require(parser.name in setOf("amp", "lt", "gt", "apos", "quot"))
                    out.append(parser.text.orEmpty())
                }
            }
        }
        return AppSummaryPolicy.text(out.toString())
    }

    companion object {
        val KINDS = listOf("word", "excel", "powerpoint")
        val PACKAGES = mapOf("word" to "com.microsoft.office.word", "excel" to "com.microsoft.office.excel", "powerpoint" to "com.microsoft.office.powerpoint")
        val MIME = mapOf("word" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "excel" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "powerpoint" to "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    }
}
