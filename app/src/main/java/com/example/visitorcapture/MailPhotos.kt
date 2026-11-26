package com.example.visitorcapture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** One image attached to the thank-you mail. */
class MailPhoto(val name: String, val bytes: ByteArray, val type: String = "image/jpeg")

/** The visitor's saved images (card front and back, handwritten enquiries), shrunk so the mail stays small. */
object MailPhotos {
    private const val MAX_SIDE = 1600

    /** File name in the mail to file on the phone; images that no longer exist are left out. */
    fun files(v: Visitor): List<Pair<String, File>> {
        val card = listOf("business-card" to v.cardPhotoPath, "business-card-back" to v.cardBackPhotoPath)
            .mapNotNull { (name, path) -> path?.let { name to File(it) } }
        val notes = Sync.notePhotos(v).mapIndexed { i, f -> "handwritten-note-${i + 1}" to f }
        return (card + notes).filter { it.second.exists() && it.second.length() > 0 }
    }

    /** Blocking. An image that can't be decoded is attached as it is. */
    fun load(v: Visitor): List<MailPhoto> = files(v).map { (name, file) ->
        shrink(file)?.let { MailPhoto("$name.jpg", it) } ?: file.extension.lowercase().ifBlank { "jpg" }.let { ext ->
            MailPhoto("$name.$ext", file.readBytes(), if (ext == "jpg") "image/jpeg" else "image/$ext")
        }
    }

    private fun shrink(file: File): ByteArray? {
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, size)
        if (size.outWidth <= 0 || size.outHeight <= 0) return null
        var sample = 1
        while (maxOf(size.outWidth, size.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        // re-encoding drops the camera's orientation tag, so the rotation is applied to the pixels
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(degrees) }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
}
