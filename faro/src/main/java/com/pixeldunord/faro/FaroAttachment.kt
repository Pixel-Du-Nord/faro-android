package com.pixeldunord.faro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * An image ready to travel with a request: a JPEG of at most [MAX_BYTES], scaled so its long side is
 * at most [MAX_SIDE] pixels. A phone screenshot weighs 2 to 4 MB as PNG; reduced it is around
 * 300 KB and still perfectly readable.
 *
 * Make one with [fromUri] (the system photo picker), [fromBitmap] (a screenshot you took) or
 * [fromBytes] (an image you already have as PNG, JPEG, GIF or WebP).
 */
public class FaroAttachment internal constructor(
    /** The encoded image. */
    public val bytes: ByteArray,
    /** `image/jpeg`, `image/png`, `image/gif` or `image/webp`. */
    public val mimeType: String,
    /** The name it travels under. */
    public val filename: String,
    /** Width in pixels (0 if unknown). */
    public val width: Int,
    /** Height in pixels (0 if unknown). */
    public val height: Int,
) {
    /** A stable id for lists and diffing. */
    public val id: String = UUID.randomUUID().toString()

    override fun toString(): String = "FaroAttachment($filename, $mimeType, ${bytes.size} B, ${width}x$height)"

    public companion object {
        /** Longest side of a reduced image, in pixels. */
        public const val MAX_SIDE: Int = 2048

        /** Faro takes images of up to 3 MB. */
        public const val MAX_BYTES: Int = 3 * 1024 * 1024

        private val ALLOWED = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

        /**
         * Reads, rotates (EXIF), scales and re-encodes the image at [uri] (for example from
         * `PickVisualMedia`). Runs off the main thread. `null` if it cannot be read as an image.
         */
        public suspend fun fromUri(context: Context, uri: Uri, filename: String = "image.jpg"): FaroAttachment? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val side = maxOf(bounds.outWidth, bounds.outHeight)
                    if (side <= 0) return@runCatching null
                    var sample = 1
                    while (side / (sample * 2) >= MAX_SIDE) sample *= 2
                    val decoded = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching null
                    val orientation = runCatching {
                        resolver.openInputStream(uri)?.use {
                            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        }
                    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
                    encode(rotate(decoded, orientation), filename)
                }.getOrNull()
            }

        /**
         * Scales and encodes [bitmap] as JPEG (blocking: call it off the main thread for big
         * images). `null` only if it cannot fit in [MAX_BYTES].
         */
        public fun fromBitmap(bitmap: Bitmap, filename: String = "screenshot.jpg"): FaroAttachment? =
            runCatching { encode(bitmap, filename) }.getOrNull()

        /**
         * An image you already have encoded. It is sent as is if it is PNG, JPEG, GIF or WebP and
         * fits in [MAX_BYTES]; otherwise it is decoded and reduced like [fromBitmap].
         */
        public fun fromBytes(bytes: ByteArray, filename: String = "image"): FaroAttachment? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val mime = bounds.outMimeType
            if (mime in ALLOWED && bytes.size <= MAX_BYTES && maxOf(bounds.outWidth, bounds.outHeight) <= MAX_SIDE) {
                return FaroAttachment(bytes, mime!!, filename, bounds.outWidth, bounds.outHeight)
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            return fromBitmap(bitmap, filename.substringBeforeLast('.') + ".jpg")
        }

        private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        private fun encode(source: Bitmap, filename: String): FaroAttachment? {
            val scale = MAX_SIDE.toFloat() / maxOf(source.width, source.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
            } else {
                source
            }
            // JPEG has no alpha: paint transparent pixels white instead of black.
            val opaque = if (bitmap.hasAlpha()) {
                Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                }
            } else {
                bitmap
            }
            var quality = 85
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().also { opaque.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                quality -= 15
            } while (bytes.size > MAX_BYTES && quality > 20)
            if (bytes.size > MAX_BYTES) return null
            val name = filename.substringBeforeLast('.').ifBlank { "image" } + ".jpg"
            return FaroAttachment(bytes, "image/jpeg", name, opaque.width, opaque.height)
        }
    }
}
