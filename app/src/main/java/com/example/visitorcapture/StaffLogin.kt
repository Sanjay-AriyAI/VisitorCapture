package com.example.visitorcapture

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// ---- remembering who is signed in on this phone (survives app restarts until someone else logs in) ----

fun loadEmployee(p: SharedPreferences): EmployeeInfo? {
    val name = p.getString("emp_name", null)?.takeIf { it.isNotBlank() } ?: return null
    return EmployeeInfo(
        name = name,
        title = p.getString("emp_title", "").orEmpty(),
        email = p.getString("emp_email", "").orEmpty(),
        phone = p.getString("emp_phone", "").orEmpty(),
        company = p.getString("emp_company", "").orEmpty()
    )
}

fun saveEmployee(p: SharedPreferences, e: EmployeeInfo) {
    p.edit().putString("emp_name", e.name).putString("emp_title", e.title).putString("emp_email", e.email)
        .putString("emp_phone", e.phone).putString("emp_company", e.company).apply()
}

// ---- live QR scanner (CameraX preview + ML Kit barcode scanning, works offline) ----

private class ScanState {
    @Volatile var alive = true
    var provider: ProcessCameraProvider? = null
    var lastValue: String? = null
    var lastAt = 0L
}

/** Shows the back camera and calls [onScanned] with the text of each QR code it sees (the same code at most every 2.5 s). */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun QrScanner(modifier: Modifier = Modifier, onScanned: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onScanned)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val state = remember { ScanState() }

    // Leaving the screen must release the camera, because CameraX stays bound to the activity otherwise.
    DisposableEffect(Unit) {
        onDispose {
            state.alive = false
            state.provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(modifier = modifier, factory = { ctx ->
        val view = PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // so the rounded clip applies
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (!state.alive) return@addListener
            try {
                val provider = future.get()
                state.provider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    // vCard QR codes are dense, so ask for 720p rather than the 480p default
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || !state.alive) { proxy.close(); return@setAnalyzer }
                    try {
                        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val raw = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                                val now = System.currentTimeMillis()
                                if (raw != null && state.alive && (raw != state.lastValue || now - state.lastAt > 2500)) {
                                    state.lastValue = raw; state.lastAt = now
                                    latest.value(raw)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } catch (e: Exception) { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { /* no camera available: the staff can use manual entry */ }
        }, ContextCompat.getMainExecutor(ctx))
        view
    })
}

// ---- the login screen ----

/** Staff sign in by scanning the vCard QR code on their phone or badge. [onCancel] is null on the very first login. */
@Composable
fun LoginScreen(current: EmployeeInfo?, onLogin: (EmployeeInfo) -> Unit, onCancel: (() -> Unit)?) {
    val ctx = LocalContext.current
    var hasCam by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { if (!hasCam) askCam.launch(Manifest.permission.CAMERA) }
    var error by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }

    Centered {
        Text("Staff login", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
        Text(
            if (current == null) "Scan your vCard QR code to start. Your name is saved with every visitor you attend."
            else "Signed in as ${current.name}. Scan another staff QR code to switch user.",
            color = Steel, textAlign = TextAlign.Center)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(Panel).border(2.dp, LineDim, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (hasCam) QrScanner(Modifier.fillMaxSize()) { raw ->
                val info = VCard.parse(raw)
                if (info != null) { error = null; onLogin(info) }
                else error = "That QR code is not a contact (vCard). Scan your staff vCard QR code."
            } else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Camera access is needed to scan your QR code.", color = Steel, textAlign = TextAlign.Center)
                PrimaryButton("Allow camera") { askCam.launch(Manifest.permission.CAMERA) }
            }
        }
        error?.let {
            Text(it, color = Color_F0C3B8, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(Shape6)
                .background(Danger.copy(alpha = 0.15f)).border(1.dp, Danger, Shape6).padding(12.dp))
        }
        LinkButton("Can't scan? Enter my details manually") { manual = true }
        onCancel?.let { SecondaryButton("Cancel") { it() } }
    }

    if (manual) ManualLoginDialog(onDismiss = { manual = false }, onLogin = { manual = false; onLogin(it) })
}

/** Fallback for a damaged QR code. The entry is marked as manual in the audit text. */
@Composable
private fun ManualLoginDialog(onDismiss: () -> Unit, onLogin: (EmployeeInfo) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email (identifies you across phones)") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onLogin(EmployeeInfo(name = name.trim(), email = email.trim(), phone = phone.trim(), raw = "manual entry"))
            }) { Text("Sign in") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
