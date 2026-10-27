package com.example.visitorcapture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val CUSTOMER_TYPES = listOf("OEM", "End User", "Agent", "Distributor")
val MARKET_EXPOSURE = listOf("Domestic Visitor", "International Visitor")
val PRODUCTS = listOf("Mechanical Spares", "Electronics Spares", "Retrofits", "Other Products")
val STEP_LABELS = listOf("Scan", "Details", "Customer", "Products", "Done")

enum class Screen(val step: Int) {
    Login(-1), Welcome(0), Capture(0), Processing(0), Details(1), Customer(2), Products(3), Catalog(3), Done(4)
}

data class Form(
    val name: String = "", val title: String = "", val company: String = "",
    val phone: String = "", val email: String = "",
    val address: String = "", val state: String = "", val country: String = "",
    val customerType: String? = null, val marketExposure: String? = null,
    val products: Set<String> = emptySet(), val otherDetail: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val scope = rememberCoroutineScope()
    val visitors by db.dao().all().collectAsState(initial = emptyList())

    val prefs = remember { ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var deviceName by remember { mutableStateOf(prefs.getString("device", null) ?: Build.MODEL ?: "device") }
    var importMsg by remember { mutableStateOf<String?>(null) }

    var employee by remember { mutableStateOf(loadEmployee(prefs)) }          // staff member signed in on this phone
    var screen by remember { mutableStateOf(if (employee == null) Screen.Login else Screen.Welcome) }
    var visitStart by remember { mutableLongStateOf(0L) }                     // date/time the current visit began
    var loggedAt by remember { mutableLongStateOf(0L) }                       // visit time shown on the Done screen
    var showAdmin by remember { mutableStateOf(false) }
    var catalogProduct by remember { mutableStateOf("Mechanical Spares") }
    var form by remember { mutableStateOf(Form()) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoTaken by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf(false) }

    // Notes typed on the Done screen are written to the visitor saved a moment earlier.
    var lastUid by remember { mutableStateOf<String?>(null) }
    var extraNote by remember { mutableStateOf("") }
    var extraSaved by remember { mutableStateOf("") }
    fun persistExtra() {
        val uid = lastUid ?: return
        val t = extraNote.trim()
        if (t == extraSaved) return
        extraSaved = t
        scope.launch { db.dao().setAdditionalInterest(uid, t) }
    }

    fun reset() {
        persistExtra()
        lastUid = null; extraNote = ""; extraSaved = ""
        form = Form(); photoFile = null; photoTaken = false; ocrError = false; visitStart = 0L; screen = Screen.Welcome
    }

    fun runOcr(file: File) {
        screen = Screen.Processing
        try {
            val img = InputImage.fromFilePath(ctx, Uri.fromFile(file))
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                .addOnSuccessListener {
                    val p = parseCard(it.text)
                    form = form.copy(name = p.name, title = p.title, company = p.company, phone = p.phone, email = p.email,
                        address = p.address, state = p.state, country = p.country)
                    ocrError = false; screen = Screen.Details
                }
                .addOnFailureListener { ocrError = true; screen = Screen.Details }
        } catch (e: Exception) { ocrError = true; screen = Screen.Details }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoTaken = true else { photoFile = null; photoTaken = false }
    }
    fun launchCamera() {
        val dir = File(ctx.filesDir, "cards").apply { mkdirs() }
        val f = File(dir, "card_${System.currentTimeMillis()}.jpg")
        photoFile = f; photoTaken = false
        takePicture.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else camPerm.launch(Manifest.permission.CAMERA)
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            var added = 0; var skipped = 0
            withContext(Dispatchers.IO) {
                uris.forEach { u ->
                    try { val (a, sk) = Sync.import(ctx, db.dao(), u); added += a; skipped += sk }
                    catch (e: Exception) { skipped++ }
                }
            }
            importMsg = "Added $added new visitor(s). Skipped $skipped (already present or invalid)."
        }
    }

    fun save() {
        val at = if (visitStart > 0L) visitStart else System.currentTimeMillis()   // date and time of the visit
        loggedAt = at
        val e = employee
        val v = Visitor(uid = UUID.randomUUID().toString(), deviceName = deviceName,
            name = form.name, title = form.title, company = form.company, email = form.email, phone = form.phone,
            address = form.address.trim(), state = form.state.trim(), country = form.country.trim(),
            customerType = form.customerType ?: "", marketExposure = form.marketExposure ?: "",
            products = form.products.joinToString("; "),
            otherDetail = form.otherDetail, cardPhotoPath = if (photoTaken) photoFile?.absolutePath else null,
            createdAt = at,
            attendedByKey = e?.key.orEmpty(), attendedByName = e?.name.orEmpty(), attendedByTitle = e?.title.orEmpty(),
            attendedByEmail = e?.email.orEmpty(), attendedByPhone = e?.phone.orEmpty())
        lastUid = v.uid; extraNote = ""; extraSaved = ""
        scope.launch { db.dao().insert(v) }
    }

    fun signIn(info: EmployeeInfo) {
        val now = System.currentTimeMillis()
        scope.launch {
            db.employeeDao().login(Employee(empKey = info.key, name = info.name, title = info.title, company = info.company,
                email = info.email, phone = info.phone, vcard = info.raw, firstLoginAt = now, lastLoginAt = now))
        }
        saveEmployee(prefs, info)
        employee = info
        if (screen == Screen.Login) screen = Screen.Welcome
    }

    fun exportData() {
        scope.launch {
            val f = withContext(Dispatchers.IO) { Sync.exportZip(ctx, visitors, deviceName) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Export visitor log"))
        }
    }

    // Auto-reset the done screen after 15 s of inactivity, like the kiosk. Typing a note restarts the clock
    // and allows 30 s, so the visitor is never cut off mid-sentence.
    LaunchedEffect(screen, extraNote) {
        if (screen == Screen.Done) { delay(if (extraNote.isBlank()) 15_000 else 30_000); reset() }
    }
    // Save the note shortly after the last keystroke (reset() also saves it, so nothing is lost).
    LaunchedEffect(extraNote) { delay(700); persistExtra() }

    BackHandler(enabled = showAdmin || (screen != Screen.Welcome && !(screen == Screen.Login && employee == null))) {
        if (showAdmin) showAdmin = false
        else when (screen) {
            Screen.Login -> screen = Screen.Welcome
            Screen.Capture -> screen = Screen.Welcome
            Screen.Details -> screen = Screen.Capture
            Screen.Customer -> screen = Screen.Details
            Screen.Products -> screen = Screen.Customer
            Screen.Catalog -> screen = Screen.Products
            Screen.Done -> reset()
            else -> {}
        }
    }

    CompositionLocalProvider(LocalContentColor provides LineC) {
        Box(Modifier.fillMaxSize().background(Navy).systemBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                ProgressRail(screen.step, STEP_LABELS, employee?.name) {
                    IconButton({ showAdmin = true }) { Icon(Icons.Default.Settings, "Staff view", tint = Steel) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        Screen.Login -> LoginScreen(employee, onLogin = { signIn(it) },
                            onCancel = if (employee != null) ({ screen = Screen.Welcome }) else null)

                        Screen.Welcome -> Centered {
                            NarenLogo(Modifier.width(260.dp))
                            Text("Welcome to\nour stand", fontSize = 44.sp, fontWeight = FontWeight.Bold,
                                fontFamily = Display, textAlign = TextAlign.Center, lineHeight = 46.sp)
                            Text("Scan your business card and we'll log your visit & interests in under a minute.",
                                color = Steel, textAlign = TextAlign.Center)
                            PrimaryButton("Scan business card") { visitStart = System.currentTimeMillis(); screen = Screen.Capture }
                            employee?.let { e ->
                                Text("Attended by ${e.name}" + if (e.title.isNotBlank()) ", ${e.title}" else "",
                                    color = Amber, fontSize = 14.sp, textAlign = TextAlign.Center)
                                LinkButton("Not you? Switch user") { screen = Screen.Login }
                            }
                        }

                        Screen.Capture -> Centered {
                            Text("Photograph the card", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
                            Text("Lay it flat, good light, fill the frame.", color = Steel)
                            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(10.dp))
                                .background(Panel).border(2.dp, LineDim, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center) {
                                if (photoTaken && photoFile != null)
                                    AsyncImage(photoFile, "Card", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text("No photo yet", color = Steel)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SecondaryButton("Back") { screen = Screen.Welcome }
                                if (photoTaken) PrimaryButton("Use this photo") { photoFile?.let { runOcr(it) } }
                                else PrimaryButton("Open camera") { openCamera() }
                            }
                            if (photoTaken) LinkButton("Retake") { openCamera() }
                            LinkButton("Skip scan, enter details manually") { photoFile = null; photoTaken = false; ocrError = false; screen = Screen.Details }
                        }

                        Screen.Processing -> Centered {
                            CircularProgressIndicator(color = Amber)
                            Text("Reading the card…", color = Steel)
                        }

                        Screen.Details -> Scrolling {
                            if (ocrError) Text("Couldn't auto-read the card. Fields left blank, just fill them in.",
                                color = Color_F0C3B8, modifier = Modifier.fillMaxWidth().clip(Shape6)
                                    .background(Danger.copy(alpha = 0.15f)).border(1.dp, Danger, Shape6).padding(12.dp))
                            CompositionLocalProvider(LocalContentColor provides PaperInk) {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Paper).padding(22.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Confirm details", fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
                                    Text("We prefilled what we could read. Fix anything that's off.", fontSize = 13.sp, color = PaperInk.copy(alpha = 0.6f))
                                    if (photoTaken && photoFile != null)
                                        AsyncImage(photoFile, "Card", Modifier.width(140.dp).aspectRatio(1.6f).clip(RoundedCornerShape(5.dp)),
                                            contentScale = ContentScale.Crop)
                                    PaperField("Full name", form.name) { form = form.copy(name = it) }
                                    PaperField("Job title", form.title) { form = form.copy(title = it) }
                                    PaperField("Company", form.company) { form = form.copy(company = it) }
                                    PaperField("Phone", form.phone) { form = form.copy(phone = it) }
                                    PaperField("Email", form.email) { form = form.copy(email = it) }
                                    PaperField("Address", form.address, singleLine = false) { form = form.copy(address = it) }
                                    PaperField("State / Province", form.state) { form = form.copy(state = it) }
                                    PaperField("Country (if applicable)", form.country) { form = form.copy(country = it) }
                                }
                            }
                            NavRow(
                                { SecondaryButton("Back") { screen = Screen.Capture } },
                                { PrimaryButton("Continue") { screen = Screen.Customer } })
                        }

                        Screen.Customer -> Scrolling {
                            Text("Customer profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
                            Text("CUSTOMER TYPE", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
                            Text("Tap one.", color = Steel)
                            TileGrid(listOf(
                                TileItem("OEM", Icons.Default.Factory, form.customerType == "OEM"),
                                TileItem("End User", Icons.Default.Person, form.customerType == "End User"),
                                TileItem("Agent", Icons.Default.SupportAgent, form.customerType == "Agent"),
                                TileItem("Distributor", Icons.Default.Store, form.customerType == "Distributor"))
                            ) { form = form.copy(customerType = it) }
                            Spacer(Modifier.height(6.dp))
                            Text("MARKET EXPOSURE", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
                            Text("Tap one.", color = Steel)
                            TileGrid(listOf(
                                TileItem("Domestic Visitor", Icons.Default.Flag, form.marketExposure == "Domestic Visitor"),
                                TileItem("International Visitor", Icons.Default.Public, form.marketExposure == "International Visitor"))
                            ) { form = form.copy(marketExposure = it) }
                            NavRow(
                                { SecondaryButton("Back") { screen = Screen.Details } },
                                { PrimaryButton("Continue", enabled = form.customerType != null && form.marketExposure != null) { screen = Screen.Products } })
                        }

                        Screen.Products -> Scrolling {
                            Text("Product profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
                            Text("Tap a product, then choose machines, makes and models. You can pick several.", color = Steel)
                            TileGrid(PRODUCTS.map { p ->
                                if (p == "Other Products")
                                    TileItem(p, Icons.Default.Inventory2, p in form.products, if (p in form.products) "Selected" else "Tap to add")
                                else {
                                    val c = form.products.count { it.startsWith("$p > ") }
                                    TileItem(p,
                                        when (p) { "Mechanical Spares" -> Icons.Default.Build; "Electronics Spares" -> Icons.Default.Memory; else -> Icons.Default.Autorenew },
                                        c > 0, if (c > 0) "$c selected  ›" else "Choose  ›")
                                }
                            }) { k ->
                                if (k == "Other Products")
                                    form = form.copy(products = if (k in form.products) form.products - k else form.products + k)
                                else { catalogProduct = k; screen = Screen.Catalog }
                            }
                            if ("Other Products" in form.products)
                                OutlinedTextField(form.otherDetail, { form = form.copy(otherDetail = it) },
                                    placeholder = { Text("Tell us more about what you're after") },
                                    modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
                            NavRow(
                                { SecondaryButton("Back") { screen = Screen.Customer } },
                                { PrimaryButton("Finish") { save(); screen = Screen.Done } })
                        }

                        Screen.Catalog -> CatalogScreen(catalogProduct, form.products,
                            onToggle = { p -> form = form.copy(products = if (p in form.products) form.products - p else form.products + p) },
                            onSetMany = { ps, on -> form = form.copy(products = if (on) form.products + ps else form.products - ps.toSet()) },
                            onDone = { screen = Screen.Products })

                        Screen.Done -> Centered {
                            val first = form.name.split(" ").firstOrNull().orEmpty()
                            Text(if (first.isNotBlank()) "Welcome, $first!" else "Welcome!",
                                fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
                            if (form.company.isNotBlank()) Text(form.company, color = Amber, fontSize = 18.sp)
                            val place = listOf(form.state, form.country).filter { it.isNotBlank() }.joinToString(", ")
                            if (place.isNotBlank()) Text(place, color = LineDim, fontSize = 14.sp)
                            val summary = listOfNotNull(form.customerType, form.marketExposure) + form.products.groupBy { it.substringBefore(" > ") }
                                .map { (p, l) -> if (l.any { " > " in it }) "$p (${l.size})" else p }
                            Text(summary.joinToString("  •  "), color = Amber, textAlign = TextAlign.Center)
                            Text("Visit logged " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(loggedAt)) +
                                (employee?.let { " · attended by ${it.name}" } ?: ""), color = Steel, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Anything else you're interested in?", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(extraNote, { extraNote = it.take(1000) },
                                    placeholder = { Text("Additional products, models, makes or quantities") },
                                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
                                Text(when {
                                    extraNote.isBlank() -> "Optional. Saved automatically."
                                    extraNote.trim() == extraSaved -> "Saved"
                                    else -> "Saving…"
                                }, color = Steel, fontSize = 12.sp)
                            }
                            Text("Thanks for stopping by. Someone from our team will follow up. This screen resets shortly for the next visitor.",
                                color = Steel, textAlign = TextAlign.Center)
                            SecondaryButton("Log next visitor now") { reset() }
                        }
                    }
                }
            }

            importMsg?.let { msg ->
                AlertDialog(onDismissRequest = { importMsg = null }, title = { Text("Import finished") }, text = { Text(msg) },
                    confirmButton = { TextButton({ importMsg = null }) { Text("OK") } })
            }
            if (showAdmin) AdminPanel(visitors, deviceName,
                onDeviceName = { deviceName = it; prefs.edit().putString("device", it).apply() },
                signedIn = employee?.name, onSwitchUser = { showAdmin = false; screen = Screen.Login },
                onClose = { showAdmin = false }, onExport = { exportData() },
                onImport = { importPicker.launch(arrayOf("*/*")) },
                onDelete = { v -> scope.launch { db.dao().delete(v) } },
                onClear = { scope.launch { db.dao().clear(); File(ctx.filesDir, "cards").deleteRecursively() } })
        }
    }
}

val Color_F0C3B8 = androidx.compose.ui.graphics.Color(0xFFF0C3B8)

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally, content = content)

@Composable
fun Scrolling(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)

@Composable
fun NavRow(left: @Composable () -> Unit, right: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) { left(); right() }

@Composable
fun PaperField(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 2, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PaperInk, unfocusedTextColor = PaperInk,
            focusedBorderColor = Amber, unfocusedBorderColor = PaperLine,
            focusedLabelColor = PaperInk.copy(alpha = 0.7f), unfocusedLabelColor = PaperInk.copy(alpha = 0.6f),
            cursorColor = Amber))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanel(visitors: List<Visitor>, deviceName: String, onDeviceName: (String) -> Unit,
               signedIn: String?, onSwitchUser: () -> Unit,
               onClose: () -> Unit, onExport: () -> Unit, onImport: () -> Unit,
               onDelete: (Visitor) -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().background(NavyDeep).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Visitor log (${visitors.size})", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                fontFamily = Display, modifier = Modifier.weight(1f))
            PrimaryButton("Close", modifier = Modifier.height(44.dp)) { onClose() }
        }
        OutlinedTextField(deviceName, onDeviceName, label = { Text("This device's name (e.g. Stand-A, Priya)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = darkFieldColors())
        Text(if (signedIn != null) "Signed in: $signedIn" else "Not signed in", color = Amber, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp))
        FlowRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Switch user") { onSwitchUser() }
            SecondaryButton("Export (zip)") { onExport() }
            SecondaryButton("Import / merge") { onImport() }
            SecondaryButton("Clear all") { confirmClear = true }
        }
        if (visitors.isEmpty()) Text("No visitors logged yet.", color = Steel, modifier = Modifier.padding(top = 40.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visitors, key = { it.id }) { v ->
                Row(Modifier.fillMaxWidth().clip(Shape6).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (v.cardPhotoPath != null)
                        AsyncImage(File(v.cardPhotoPath), null, Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(listOf(v.name.ifBlank { "Unnamed visitor" }, v.title).filter { it.isNotBlank() }.joinToString(" · "),
                            fontWeight = FontWeight.SemiBold)
                        Text(listOf(v.company, v.email, v.phone).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 12.sp, color = Steel)
                        val place = listOf(v.address, v.state, v.country).filter { it.isNotBlank() }.joinToString(", ")
                        if (place.isNotBlank()) Text(place, fontSize = 12.sp, color = LineDim)
                        Text("Visited " + fmt.format(Date(v.createdAt)) +
                            (if (v.attendedByName.isNotBlank()) " · Attended by ${v.attendedByName}" else ""),
                            fontSize = 12.sp, color = Steel)
                        Text(listOf(v.customerType, v.marketExposure).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = Amber)
                        Text(Catalog.summarize(v.products.split("; ").filter { it.isNotBlank() }), fontSize = 12.sp, color = Amber)
                        if (v.otherDetail.isNotBlank()) Text(v.otherDetail, fontSize = 12.sp, color = LineDim)
                        if (v.additionalInterest.isNotBlank()) Text("Also interested in: " + v.additionalInterest, fontSize = 12.sp, color = LineDim)
                    }
                    IconButton({ onDelete(v) }) { Icon(Icons.Default.Delete, "Delete", tint = Steel) }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all logged visitors?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton({ onClear(); confirmClear = false }) { Text("Delete all") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
}
