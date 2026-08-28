package com.poodicraft.bookquest.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.poodicraft.bookquest.data.Assignment
import com.poodicraft.bookquest.data.BookFormat
import com.poodicraft.bookquest.data.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file the user picked, already read and identified. */
internal class Attachment(
    val name: String,
    val format: BookFormat,
    val bytes: ByteArray
) {
    val kilobytes: Int get() = (bytes.size / 1024).coerceAtLeast(1)

    /** True when it is small enough to travel inside a Firestore document. */
    val fitsInline: Boolean get() = bytes.size <= Assignment.MAX_INLINE_BYTES

    fun encode(): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/** Reads a picked file, working out what it actually is from its contents. */
internal suspend fun readAttachment(context: Context, uri: Uri): Attachment? =
    withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri)
            val bytes = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() } ?: return@withContext null
            val name = attachmentName(context, uri)
            val head = bytes.copyOf(minOf(bytes.size, FileType.HEAD_BYTES))
            Attachment(name, FileType.detect(name, mime, head), bytes)
        } catch (e: Exception) {
            null
        }
    }

private fun attachmentName(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
        }
    } catch (e: Exception) {
        name = null
    }
    if (name.isNullOrBlank()) name = uri.lastPathSegment
    return name ?: "file"
}

internal fun decodeAttachment(base64: String): ByteArray = try {
    Base64.decode(base64, Base64.NO_WRAP)
} catch (e: Exception) {
    ByteArray(0)
}
