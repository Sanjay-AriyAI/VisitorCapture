package com.example.visitorcapture

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** Reads a business-card photo fully on the device (no network). */
object CardReader {
    private const val TAG = "CardOCR"

    /** Long edge above which the photo is halved before recognition (keeps memory sane; 12 MP photos are left alone). */
    private const val MAX_EDGE = 4600

    /**
     * Blocking: call from a background thread. Runs OCR on the photo as taken; if that does not find an e-mail, a phone
     * number and a name, retries the same photo turned 90/270/180 degrees (cards are often held sideways) and keeps the
     * pass that found the most. Returns null when the photo cannot be read at all.
     */
    fun read(ctx: Context, file: File): Parsed? {
        val (bitmap, exifDegrees) = load(file) ?: return null
        val debug = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            var best: Parsed? = null
            var bestScore = -1
            for (extra in intArrayOf(0, 90, 270, 180)) {
                val degrees = (exifDegrees + extra) % 360
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, degrees)))
                val parsed = parseLines(linesOf(text)).copy(raw = text.text)
                val score = parsed.score()
                if (debug) Log.d(TAG, "rotation=$degrees score=$score\n--- raw ---\n${text.text}\n--- parsed ---\n$parsed")
                if (score > bestScore) { best = parsed; bestScore = score }
                if (parsed.email.isNotEmpty() && parsed.phone.isNotEmpty() && parsed.name.isNotEmpty()) break
            }
            return best
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun linesOf(text: Text): List<OcrLine> =
        text.textBlocks.flatMap { block -> block.lines.map { OcrLine(it.text, it.boundingBox?.height() ?: 0) } }

    /** The photo plus the clockwise rotation (from its EXIF tag) needed to make it upright. */
    private fun load(file: File): Pair<Bitmap, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) { 0 }
        return bitmap to degrees
    }
}
