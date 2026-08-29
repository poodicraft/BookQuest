package com.poodicraft.bookquest.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import com.poodicraft.bookquest.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Profile pictures travel inside the account document in Firestore, so they are
 * squared off, shrunk to a thumbnail and re-encoded as JPEG before being stored.
 * A photo straight off a phone camera is several megabytes; a document may hold
 * one, and that budget is shared with everything else on the account.
 */
object ProfilePicture {

    /** The stored picture is this many pixels on a side. */
    private const val SIDE = 256

    /** Comfortably inside the Firestore document limit once base64 encoded. */
    const val MAX_BYTES = 120_000

    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // Let the decoder throw away most of the pixels rather than loading a
            // full sized photo into memory only to shrink it afterwards.
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val square = cropToSquare(decoded)
            val scaled = Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            val bytes = stream.toByteArray()
            if (bytes.size > MAX_BYTES) return@withContext null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun decode(base64: String): ImageBitmap? = try {
        if (base64.isBlank()) {
            null
        } else {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        val shortest = minOf(width, height)
        while (shortest / (sample * 2) >= SIDE) sample *= 2
        return sample
    }

    private fun cropToSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        if (source.width == source.height) return source
        return Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side
        )
    }
}

/**
 * The account's picture, or the first letter of their name on a coloured disc
 * when there is no picture — the same shape either way so nothing shifts when
 * one is added.
 */
@Composable
fun ProfileAvatar(
    photo: String,
    name: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val image = remember(photo) { ProfilePicture.decode(photo) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Brand.Violet, Brand.Sky))),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = name.trim().take(1).uppercase().ifBlank { "🙂" },
                fontSize = (size.value / 2.2f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
