package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.live.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppSummaryTest {
    private fun summary(source: SummarySource, key: String = "one") = AppSummary(key, "Title", "Text", source, "app", 10)
    @Test fun permissionsAndOptOutGateEveryExternalSourceButNotLocalDocuments() {
        for (source in SummarySource.entries) {
            val value = summary(source)
            assertTrue(AppSummaryPolicy.allowed(value, true))
            assertEquals(source in setOf(SummarySource.Local, SummarySource.Document), AppSummaryPolicy.allowed(value, false))
            val datum = LiveDatum("", "", LiveAvailability.EMPTY)
            val snapshot = LiveSurfaceSnapshot("Day", "Time", datum, datum, datum, datum, datum, datum, 0, appSummaries = listOf(value))
            val item = FrameItem(FrameApp("app", 0))
            assertEquals(value, FrameLiveSummary.summary(FramesState(notificationsAllowed = true), item, snapshot, 0))
            assertEquals("Text", FrameLiveSummary.detail(FramesState(notificationsAllowed = true), item, snapshot, 0))
            assertNull(FrameLiveSummary.summary(FramesState(notificationsAllowed = true), FrameItem(FrameApp("app", 10)), snapshot, 0))
            assertNull(FrameLiveSummary.summary(FramesState(settings = FrameSettings(liveSummaries = false)), item, snapshot, 0))
            assertNull(FrameLiveSummary.summary(FramesState(), FrameItem(FrameApp("different", 0)), snapshot, 0))
        }
        assertEquals("hello world", AppSummaryPolicy.text(" hello\n\tworld "))
        assertEquals(240, AppSummaryPolicy.text("a".repeat(300)).length)
        val values = listOf(summary(SummarySource.Local), summary(SummarySource.Local), summary(SummarySource.Media, "two"),
            summary(SummarySource.Local, "blank").copy(detail = " "))
        assertEquals(listOf("one"), AppSummaryPolicy.select(values, false).map { it.key })
        assertEquals(24, AppSummaryPolicy.select((1..30).map { summary(SummarySource.Local, "$it").copy(updatedAt = it.toLong()) }, true).size)
    }
    private fun archive(vararg parts: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> parts.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
        } }
        return output.toByteArray()
    }
    private fun preview(bytes: ByteArray, kind: String) = OfficeArchive.excerpt(ByteArrayInputStream(bytes), kind) { it }
    @Test fun readsOnlyRelevantOfficePartsCountsSheetsSlidesAndNeverExtractsPaths() {
        val doc = archive("word/document.xml" to "Document", "xl/sharedStrings.xml" to "Ignore", "../outside" to "Ignore")
        assertEquals("Document text · Document", preview(doc, "word"))
        val workbook = archive("xl/worksheets/sheet1.xml" to "123", "xl/worksheets/sheet2.xml" to "456", "xl/sharedStrings.xml" to "Budget")
        assertEquals("2 sheets · 123 · Budget", preview(workbook, "excel"))
        assertEquals("2 slides · Intro", preview(archive("ppt/slides/slide1.xml" to "Intro", "ppt/slides/slide2.xml" to "Next"), "powerpoint"))
        assertEquals("Document text", preview(archive("word/document.xml" to ""), "word"))
    }
    @Test fun rejectsMalformedOversizedAndTooManyParts() {
        assertThrows(IllegalArgumentException::class.java) { preview(byteArrayOf(1, 2), "word") }
        assertThrows(IllegalArgumentException::class.java) { preview(archive("x" to "x"), "invalid") }
        assertThrows(IllegalArgumentException::class.java) { preview(archive("word/document.xml" to "a".repeat(256 * 1024 + 1)), "word") }
        assertThrows(IllegalArgumentException::class.java) { preview(archive("x" to "a".repeat(OfficeArchive.MAX_BYTES + 1)), "word") }
        assertThrows(IllegalArgumentException::class.java) { preview(archive(*(1..257).map { "part$it" to "" }.toTypedArray()), "word") }
    }
}
