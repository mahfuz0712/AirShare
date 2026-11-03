package com.mux.android.airshare.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object Files {
    fun fmt(b: Long): String = when {
        b >= 1_073_741_824L -> "%.2f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1024L -> "%d KB".format(b / 1024)
        else -> "$b B"
    }

    /** name, size, mime of a content uri picked by the user. */
    fun describe(ctx: Context, uri: Uri): FileItem {
        var name = ""
        var size = -1L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n) ?: ""
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } catch (e: Exception) {
        }
        if (size < 0) {
            size = try {
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        if (name.isEmpty()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        return FileItem(name, size.coerceAtLeast(0L), mime, uri)
    }

    fun safeName(n: String): String {
        val clean = n.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return clean.take(120).ifEmpty { "file" }
    }

    /** Copies [input] into Downloads/AirShare. Returns a uri string, or null on failure. */
    fun saveReceived(ctx: Context, name: String, mime: String, input: InputStream): String? {
        val clean = safeName(name)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, clean)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AirShare")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val cr = ctx.contentResolver
                val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return null
                cr.openOutputStream(uri)?.use { out -> input.copyTo(out, 64 * 1024) }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                cr.update(uri, done, null, null)
                uri.toString()
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AirShare")
                dir.mkdirs()
                val base = clean.substringBeforeLast('.', clean)
                val ext = if (clean.contains('.')) "." + clean.substringAfterLast('.') else ""
                var f = File(dir, clean)
                var i = 1
                while (f.exists()) {
                    f = File(dir, "$base ($i)$ext")
                    i++
                }
                FileOutputStream(f).use { out -> input.copyTo(out, 64 * 1024) }
                MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(mime), null)
                Uri.fromFile(f).toString()
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                input.close()
            } catch (e: Exception) {
            }
        }
    }

    /** Android 10+: moves a file that Nearby already wrote into Downloads/AirShare (no second copy). */
    fun moveIntoAirShare(ctx: Context, uri: Uri, name: String, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name))
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AirShare")
            }
            ctx.contentResolver.update(uri, v, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }
}
