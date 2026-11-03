package io.github.aimindseye.tenseven;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Test APK only. Java keeps this independent of the target APK's Kotlin runtime. */
public final class FrameDocumentFixtureProvider extends ContentProvider {
    private File fixture;
    @Override public boolean onCreate() {
        if (getContext() == null) return false;
        fixture = new File(getContext().getCacheDir(), "frame-word-fixture.docx");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(fixture))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w='urn:word'><w:t>Verified saved Word content</w:t></w:document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException error) { throw new IllegalStateException("Could not create test document", error); }
        return true;
    }
    private void check(Uri uri) {
        if (!"/fixture.docx".equals(uri.getPath())) throw new IllegalArgumentException("Unknown test document");
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        check(uri);
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{"fixture.docx"});
        return cursor;
    }
    @Override public String getType(Uri uri) { check(uri); return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        check(uri);
        if (!"r".equals(mode)) throw new IllegalArgumentException("Read-only test document");
        return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
