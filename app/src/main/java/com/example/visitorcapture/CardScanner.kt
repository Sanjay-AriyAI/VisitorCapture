package com.example.visitorcapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

/**
 * Business-card capture through Google's on-device document scanner (delivered by Play services): live edge detection,
 * auto-capture once the card is steady, automatic crop and perspective correction. When the scanner is unavailable
 * (no Play services, low-RAM device) the caller falls back to the plain system camera.
 */
object CardScanner {
    private const val TAG = "CardScanner"

    private val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setPageLimit(1)
        .setGalleryImportAllowed(false)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build()

    /** Opens the scanner; calls [onUnavailable] (on the main thread) when it cannot be started on this device. */
    fun start(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>, onUnavailable: () -> Unit) {
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                try { launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                catch (e: Exception) { Log.w(TAG, "scanner launch failed", e); onUnavailable() }
            }
            .addOnFailureListener { e -> Log.w(TAG, "scanner unavailable", e); onUnavailable() }
    }

    /** Blocking (call off the main thread): copies the scanned, cropped card into [dest]. False if nothing was scanned. */
    fun copyResult(ctx: Context, data: Intent?, dest: File): Boolean = try {
        val uri = GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull()?.imageUri
        if (uri == null) false
        else {
            ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            dest.length() > 0
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not save scanned card", e); dest.delete(); false
    }
}
