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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
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
val LEAD_OUTCOMES = listOf("Product Enquiry", "Sample Request", "General Enquiry", "Order", "Factory Visit", "Follow-up")
val REMARK_OPTIONS = listOf("Call Back", "Factory Visit", "Need Follow-up", "Others")
val FOLLOW_UP_ACTIONS = listOf("Send Quotation", "Schedule Factory Visit", "Automatic E-mail Reminder", "Remarks")
const val MAX_NOTE_PHOTOS = 5   // handwritten-enquiry images per visit
val STEP_LABELS = listOf("Scan", "Details", "Customer", "Products", "Outcome", "Done")

enum class Screen(val step: Int) {
    Login(-1), Welcome(0), Capture(0), Processing(0), Details(1), Customer(2), Products(3), Catalog(3), LeadOutcome(4), Done(5)
}

data class Form(
    val name: String = "", val title: String = "", val company: String = "",
    val phone: String = "", val email: String = "",
    val address: String = "", val state: String = "", val country: String = "",
    val customerType: String? = null, val marketExposure: String? = null,
    val products: Set<String> = emptySet(), val otherDetail: String = "",
    val outcomes: Set<String> = emptySet(), val notes: String = "",
    val remark: String? = null, val remarkOther: String = "",
    val followUpAction: String? = null, val followUpAt: Long = 0L, val followUpNote: String = ""
) {
    /** The follow-up section is only shown (and saved) for these outcomes. */
    val needsFollowUp get() = "Factory Visit" in outcomes || "Follow-up" in outcomes
}

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
    var section by remember { mutableStateOf(AppPage.Visit) }                 // page picked from the side menu / gear
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var appSettings by remember { mutableStateOf(AppSettings.load(prefs)) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var catalogProduct by remember { mutableStateOf("Mechanical Spares") }
    var form by remember { mutableStateOf(Form()) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoTaken by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf(false) }
    // Per-field "please check" notes from the card reader; a note disappears as soon as the visitor edits that field.
    var fieldHints by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // What the scanner actually read, shown only in debug builds so a wrong field can be traced to its cause.
    var ocrRaw by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }
    val isDebugBuild = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

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

    // Photos / uploads of handwritten enquiries attached on the Done screen. Saved as image files on the visitor.
    var notePhotos by remember { mutableStateOf(listOf<File>()) }
    var pendingNote by remember { mutableStateOf<File?>(null) }
    var noteBusy by remember { mutableStateOf(false) }      // camera / picker open or copying: holds the auto-reset
    var noteError by remember { mutableStateOf<String?>(null) }
    fun persistNotes() {
        val uid = lastUid ?: return
        val paths = notePhotos.joinToString("|") { it.absolutePath }
        scope.launch { db.dao().setNotePhotos(uid, paths) }
    }

    fun reset() {
        persistExtra()
        lastUid = null; extraNote = ""; extraSaved = ""
        notePhotos = emptyList(); pendingNote = null; noteBusy = false; noteError = null
        form = Form(); photoFile = null; photoTaken = false; ocrError = false; fieldHints = emptyMap(); visitStart = 0L; screen = Screen.Welcome
    }

    fun runOcr(file: File) {
        screen = Screen.Processing
        scope.launch {
            val p = try { withContext(Dispatchers.Default) { CardReader.read(ctx, file) } } catch (e: Exception) { null }
            if (p == null) { ocrError = true; fieldHints = emptyMap() }
            else {
                form = form.copy(name = p.name, title = p.title, company = p.company, phone = p.phone, email = p.email,
                    address = p.address, state = p.state, country = p.country.ifBlank { appSettings.defaultCountry.trim() })
                fieldHints = p.flags; ocrError = false; ocrRaw = p.raw
            }
            screen = Screen.Details
        }
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

    // Note images live next to the card photos (cards/ is already shared with the camera via the FileProvider).
    fun newNoteFile(ext: String): File? {
        val uid = lastUid ?: return null
        val dir = File(ctx.filesDir, "cards").apply { mkdirs() }
        return File(dir, "${uid}_note_${System.currentTimeMillis()}_${notePhotos.size}.$ext")
    }
    val takeNotePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingNote
        if (ok && f != null && f.length() > 0) { notePhotos = notePhotos + f; persistNotes() } else f?.delete()
        pendingNote = null; noteBusy = false
    }
    fun launchNoteCamera() {
        val f = newNoteFile("jpg") ?: run { noteBusy = false; return }
        pendingNote = f; noteBusy = true; noteError = null
        takeNotePicture.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
    }
    val noteCamPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) launchNoteCamera() else { noteBusy = false; noteError = "Camera permission is needed to take a photo." }
    }
    fun openNoteCamera() {
        if (notePhotos.size >= MAX_NOTE_PHOTOS) { noteError = "You can attach up to $MAX_NOTE_PHOTOS images."; return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchNoteCamera()
        else { noteBusy = true; noteCamPerm.launch(Manifest.permission.CAMERA) }
    }
    val uploadNotes = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) { noteBusy = false; return@rememberLauncherForActivityResult }
        scope.launch {
            val room = MAX_NOTE_PHOTOS - notePhotos.size
            val copied = withContext(Dispatchers.IO) {
                uris.take(room).mapIndexedNotNull { i, u ->
                    val ext = when (ctx.contentResolver.getType(u)) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                    val f = newNoteFile(ext)?.let { File(it.parentFile, it.nameWithoutExtension + "_$i.$ext") } ?: return@mapIndexedNotNull null
                    try {
                        ctx.contentResolver.openInputStream(u)?.use { inp -> f.outputStream().use { inp.copyTo(it) } }
                        f.takeIf { it.length() > 0 } ?: run { f.delete(); null }
                    } catch (e: Exception) { f.delete(); null }
                }
            }
            if (copied.isNotEmpty()) { notePhotos = notePhotos + copied; persistNotes() }
            noteError = when {
                copied.size < uris.take(room).size -> "Some images couldn't be added."
                uris.size > room -> "Only $MAX_NOTE_PHOTOS images can be attached; the rest were skipped."
                else -> null
            }
            noteBusy = false
        }
    }
    fun openNoteUpload() {
        if (notePhotos.size >= MAX_NOTE_PHOTOS) { noteError = "You can attach up to $MAX_NOTE_PHOTOS images."; return }
        noteBusy = true; noteError = null
        uploadNotes.launch("image/*")
    }
    fun removeNote(f: File) {
        notePhotos = notePhotos - f; f.delete(); persistNotes()
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
        // Fields of hidden sections are dropped, so a changed mind never leaves stale values behind.
        val follow = if (form.needsFollowUp) form.followUpAction else null
        val uidPrefix = UidGen.prefix(e?.name.orEmpty(), deviceName, at)
        val v = Visitor(uid = uidPrefix, deviceName = deviceName,
            name = form.name, title = form.title, company = form.company, email = form.email, phone = form.phone,
            address = form.address.trim(), state = form.state.trim(), country = form.country.trim(),
            customerType = form.customerType ?: "", marketExposure = form.marketExposure ?: "",
            products = form.products.joinToString("; "),
            otherDetail = form.otherDetail, cardPhotoPath = if (photoTaken) photoFile?.absolutePath else null,
            createdAt = at,
            attendedByKey = e?.key.orEmpty(), attendedByName = e?.name.orEmpty(), attendedByTitle = e?.title.orEmpty(),
            attendedByEmail = e?.email.orEmpty(), attendedByPhone = e?.phone.orEmpty(),
            leadOutcomes = LEAD_OUTCOMES.filter { it in form.outcomes }.joinToString("; "),
            discussionNotes = form.notes.trim(), remarks = form.remark.orEmpty(),
            remarksOther = if (form.remark == "Others") form.remarkOther.trim() else "",
            followUpAction = follow.orEmpty(),
            followUpAt = if (follow != null && follow != "Remarks") form.followUpAt else 0L,
            followUpNote = if (follow == "Remarks") form.followUpNote.trim() else "",
            thankYouStatus = ThankYou.initialStatus(form.email))
        // "Send automatically" (Settings) queues the mail straight away; otherwise it waits (PENDING) for the Mail settings button.
        val autoSend = appSettings.autoThankYou && v.thankYouStatus == ThankYou.PENDING && SmtpSettings.load(ctx).complete
        extraNote = ""; extraSaved = ""
        scope.launch {
            val uid = UidGen.withSeq(uidPrefix, db.dao().countUidPrefix(uidPrefix))
            lastUid = uid
            db.dao().insert(v.copy(uid = uid, thankYouStatus = if (autoSend) ThankYou.QUEUED else v.thankYouStatus))
            if (autoSend) ThankYouWorker.enqueue(ctx)
        }
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

    /** Profile edited: remember it on this phone, then update the staff record and the visits they attended here. */
    fun editProfile(updated: EmployeeInfo) {
        val old = employee ?: return
        saveEmployee(prefs, updated)
        employee = updated
        scope.launch {
            try {
                db.employeeDao().edit(old.key, updated.key, updated.name, updated.title, updated.company, updated.email, updated.phone)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // the new e-mail already belongs to another saved staff record: refresh that one instead
                db.employeeDao().touch(updated.key, updated.name, updated.title, updated.company, updated.email,
                    updated.phone, updated.raw, System.currentTimeMillis())
            }
            db.dao().renameStaff(old.key, updated.key, updated.name, updated.title, updated.email, updated.phone)
        }
        android.widget.Toast.makeText(ctx, "Profile updated", android.widget.Toast.LENGTH_SHORT).show()
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

    // The Done screen stays open until staff press "Log next visitor now" (no auto-reset).
    // Save the note shortly after the last keystroke (reset() also saves it, so nothing is lost).
    LaunchedEffect(extraNote) { delay(700); persistExtra() }

    // ---- side menu, settings and page switching ----
    val menuAvailable = employee != null && screen != Screen.Login
    val midVisit = screen !in setOf(Screen.Login, Screen.Welcome, Screen.Done)
    val mailConfigured = remember(section, visitors) { SmtpSettings.load(ctx).complete }
    fun go(s: AppPage) { section = s; scope.launch { drawerState.close() } }
    fun startNewVisit() {
        scope.launch { drawerState.close() }
        if (midVisit && appSettings.confirmAbandon) confirmDiscard = true else { reset(); section = AppPage.Visit }
    }
    fun switchUser() { section = AppPage.Visit; screen = Screen.Login; scope.launch { drawerState.close() } }
    fun startVisit(manual: Boolean) {
        visitStart = System.currentTimeMillis()
        if (form.country.isBlank()) form = form.copy(country = appSettings.defaultCountry.trim())
        if (manual) { photoFile = null; photoTaken = false; ocrError = false; fieldHints = emptyMap(); screen = Screen.Details }
        else screen = Screen.Capture
    }
    // Back from Details returns to the camera, unless the visit started with manual entry ("Skip card scan").
    val detailsBack = if (appSettings.skipScan && !photoTaken) Screen.Welcome else Screen.Capture

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(appSettings.keepAwake) {
        view.keepScreenOn = appSettings.keepAwake
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = drawerState.isOpen || section != AppPage.Visit ||
        (screen != Screen.Welcome && !(screen == Screen.Login && employee == null))) {
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        else if (section != AppPage.Visit) section = AppPage.Visit
        else when (screen) {
            Screen.Login -> screen = Screen.Welcome
            Screen.Capture -> screen = Screen.Welcome
            Screen.Details -> screen = detailsBack
            Screen.Customer -> screen = Screen.Details
            Screen.Products -> screen = Screen.Customer
            Screen.Catalog -> screen = Screen.Products
            Screen.LeadOutcome -> screen = Screen.Products
            Screen.Done -> reset()
            else -> {}
        }
    }

    CompositionLocalProvider(LocalContentColor provides LineC) {
      ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,   // swipe closes it; an edge swipe never opens it by accident mid-visit
        scrimColor = NavyDeep.copy(alpha = 0.6f),
        drawerContent = {
            AppDrawer(section, employee, deviceName, visitorCount = visitors.size,
                mailPending = visitors.count { it.thankYouStatus == ThankYou.PENDING }, mailConfigured = mailConfigured,
                visitInProgress = midVisit || screen == Screen.Done,
                onSelect = { go(it) }, onNewVisit = { startNewVisit() }, onSwitchUser = { switchUser() })
        }) {
        Box(Modifier.fillMaxSize().background(Navy).systemBarsPadding()) {
          AnimatedContent(section, label = "section", transitionSpec = {
              val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
              (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 8 }) togetherWith fadeOut(tween(140))
          }) { page -> when (page) {
            AppPage.Profile -> ProfileScreen(employee, visitors, onSwitchUser = { switchUser() },
                onEdit = { updated -> editProfile(updated) }, onBack = { section = AppPage.Visit })
            AppPage.Logs -> VisitorLogScreen(visitors, onBack = { section = AppPage.Visit },
                onExport = { exportData() }, onImport = { importPicker.launch(arrayOf("*/*")) },
                onEdit = { v -> scope.launch { db.dao().update(v) } },
                onDelete = { v ->
                    scope.launch {
                        db.dao().delete(v)
                        withContext(Dispatchers.IO) { v.cardPhotoPath?.let { File(it).delete() }; Sync.notePhotos(v).forEach { it.delete() } }
                    }
                },
                onClear = { scope.launch { db.dao().clear(); File(ctx.filesDir, "cards").deleteRecursively() } },
                onRetryThankYou = { v ->
                    scope.launch {
                        if (ThankYou.isValidEmail(v.email)) { db.dao().markThankYou(v.uid, ThankYou.QUEUED, 0); ThankYouWorker.enqueue(ctx) }
                    }
                })
            AppPage.Mail -> MailSettingsScreen(visitors, onBack = { section = AppPage.Visit })
            AppPage.Settings -> SettingsScreen(deviceName,
                onDeviceName = { deviceName = it; prefs.edit().putString("device", it).apply() },
                settings = appSettings, onChange = { appSettings = it; it.save(prefs) },
                mailConfigured = mailConfigured, onOpen = { section = it }, onBack = { section = AppPage.Visit })
            AppPage.About -> AboutScreen(deviceName, visitors.size, onBack = { section = AppPage.Visit })
            AppPage.Visit ->
            Column(Modifier.fillMaxSize()) {
                ProgressRail(screen.step, STEP_LABELS, employee?.name,
                    leading = {
                        if (menuAvailable) IconButton({ scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Open menu", tint = LineC)
                        }
                    }) {
                    if (menuAvailable) IconButton({ section = AppPage.Settings }) { Icon(Icons.Default.Settings, "Settings", tint = Steel) }
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
                            if (appSettings.skipScan) {
                                PrimaryButton("Enter visitor details") { startVisit(manual = true) }
                                LinkButton("Scan a business card instead") { startVisit(manual = false) }
                            } else PrimaryButton("Scan business card") { startVisit(manual = false) }
                            employee?.let { e ->
                                Text("Attended by ${e.name}" + if (e.title.isNotBlank()) ", ${e.title}" else "",
                                    color = Amber, fontSize = 14.sp, textAlign = TextAlign.Center)
                                LinkButton("Not you? Switch user") { screen = Screen.Login }
                            }
                            // Thank-you mails are sent from Mail settings (side menu); this is just a quiet reminder.
                            val unsent = visitors.count { it.thankYouStatus == ThankYou.PENDING }
                            val failed = visitors.count { it.thankYouStatus == ThankYou.FAILED }
                            if (unsent > 0 || failed > 0) LinkButton(listOfNotNull(
                                if (unsent > 0) "$unsent thank-you mail(s) to send" else null,
                                if (failed > 0) "$failed failed" else null).joinToString(" · ") + "  ›") { section = AppPage.Mail }
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
                            LinkButton("Skip scan, enter details manually") { photoFile = null; photoTaken = false; ocrError = false; fieldHints = emptyMap(); screen = Screen.Details }
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
                                    // Debug-only "show scanned text" toggle, disabled for now.
                                    // if (isDebugBuild && ocrRaw.isNotEmpty()) {
                                    //     TextButton(onClick = { showRaw = !showRaw }) {
                                    //         Text(if (showRaw) "Hide scanned text (debug)" else "Show scanned text (debug)", color = PaperHint, fontSize = 12.sp)
                                    //     }
                                    //     if (showRaw) Text(ocrRaw, fontSize = 11.sp, color = PaperInk.copy(alpha = 0.8f),
                                    //         modifier = Modifier.fillMaxWidth().background(PaperLine.copy(alpha = 0.3f)).padding(8.dp))
                                    // }
                                    if (fieldHints.isNotEmpty()) Text("Fields marked in amber need a quick check against the card.",
                                        fontSize = 12.sp, color = PaperHint)
                                    PaperField("Full name", form.name, hint = fieldHints["name"]) { form = form.copy(name = it); fieldHints = fieldHints - "name" }
                                    PaperField("Job title", form.title, hint = fieldHints["title"]) { form = form.copy(title = it); fieldHints = fieldHints - "title" }
                                    PaperField("Company", form.company, hint = fieldHints["company"]) { form = form.copy(company = it); fieldHints = fieldHints - "company" }
                                    PaperField("Phone", form.phone, hint = fieldHints["phone"]) { form = form.copy(phone = it); fieldHints = fieldHints - "phone" }
                                    PaperField("Email", form.email, hint = fieldHints["email"]) { form = form.copy(email = it); fieldHints = fieldHints - "email" }
                                    PaperField("Address", form.address, singleLine = false, hint = fieldHints["address"]) { form = form.copy(address = it); fieldHints = fieldHints - "address" }
                                    PaperField("State / Province", form.state, hint = fieldHints["state"]) { form = form.copy(state = it); fieldHints = fieldHints - "state" }
                                    PaperField("Country (if applicable)", form.country) { form = form.copy(country = it) }
                                }
                            }
                            NavRow(
                                { SecondaryButton("Back") { screen = detailsBack } },
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
                                val c = form.products.count { it.startsWith("$p > ") }
                                TileItem(p,
                                    when (p) {
                                        "Mechanical Spares" -> Icons.Default.Build; "Electronics Spares" -> Icons.Default.Memory
                                        "Retrofits" -> Icons.Default.Autorenew; else -> Icons.Default.Inventory2
                                    },
                                    c > 0, if (c > 0) "$c selected  ›" else "Choose  ›")
                            }) { k -> catalogProduct = k; screen = Screen.Catalog }
                            if (form.products.any { it.startsWith("Other Products > ") })
                                OutlinedTextField(form.otherDetail, { form = form.copy(otherDetail = it) },
                                    placeholder = { Text("Tell us more about what you're after") },
                                    modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
                            NavRow(
                                { SecondaryButton("Back") { screen = Screen.Customer } },
                                { PrimaryButton("Continue") { screen = Screen.LeadOutcome } })
                        }

                        Screen.Catalog -> CatalogScreen(catalogProduct, form.products,
                            onToggle = { p -> form = form.copy(products = if (p in form.products) form.products - p else form.products + p) },
                            onSetMany = { ps, on -> form = form.copy(products = if (on) form.products + ps else form.products - ps.toSet()) },
                            onDone = { screen = Screen.Products })

                        Screen.LeadOutcome -> LeadOutcomeScreen(form, { form = it }, employee,
                            onBack = { screen = Screen.Products }, onFinish = { save(); screen = Screen.Done })

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
                                Text("Have a handwritten enquiry? Attach a photo of it.", color = Steel, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SecondaryButton("Take photo") { openNoteCamera() }
                                    SecondaryButton("Upload image") { openNoteUpload() }
                                }
                                if (notePhotos.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    notePhotos.forEach { f ->
                                        Box(Modifier.size(84.dp).clip(Shape6).border(1.dp, LineDim, Shape6)) {
                                            AsyncImage(f, "Handwritten enquiry", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            IconButton({ removeNote(f) }, Modifier.align(Alignment.TopEnd).size(28.dp)
                                                .background(Navy.copy(alpha = 0.7f), Shape6)) {
                                                Icon(Icons.Default.Close, "Remove image", tint = LineC, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                                if (notePhotos.isNotEmpty()) Text("${notePhotos.size} image(s) attached. Saved with this visit.", color = Steel, fontSize = 12.sp)
                                noteError?.let { Text(it, color = Color_F0C3B8, fontSize = 12.sp) }
                                Text(when {
                                    extraNote.isBlank() -> "Optional. Saved automatically."
                                    extraNote.trim() == extraSaved -> "Saved"
                                    else -> "Saving…"
                                }, color = Steel, fontSize = 12.sp)
                            }
                            Text("Thanks for stopping by. Someone from our team will follow up. Tap \"Log next visitor now\" when you're done.",
                                color = Steel, textAlign = TextAlign.Center)
                            SecondaryButton("Log next visitor now") { reset() }
                        }
                    }
                }
            }
          } }   // when (page) / AnimatedContent

            importMsg?.let { msg ->
                AlertDialog(onDismissRequest = { importMsg = null }, title = { Text("Import finished") }, text = { Text(msg) },
                    confirmButton = { TextButton({ importMsg = null }) { Text("OK") } })
            }
            if (confirmDiscard) AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                icon = { Icon(Icons.Default.Warning, null, tint = Amber) },
                title = { Text("Discard this visit?") },
                text = { Text("The visit in progress hasn't been saved. Start a new visit anyway?") },
                confirmButton = { TextButton({ confirmDiscard = false; reset(); section = AppPage.Visit }) { Text("Discard", color = Danger) } },
                dismissButton = { TextButton({ confirmDiscard = false }) { Text("Keep editing") } })
        }
      }
    }
}

val Color_F0C3B8 =androidx.compose.ui.graphics.Color(0xFFF0C3B8)

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
fun PaperField(label: String, value: String, singleLine: Boolean = true, hint: String? = null, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 2, modifier = Modifier.fillMaxWidth(),
        // A hint means the card reader was unsure: amber border plus a short note until the visitor edits the field.
        supportingText = hint?.let { { Text(it, color = PaperHint) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PaperInk, unfocusedTextColor = PaperInk,
            focusedBorderColor = Amber, unfocusedBorderColor = if (hint != null) Amber else PaperLine,
            focusedLabelColor = PaperInk.copy(alpha = 0.7f), unfocusedLabelColor = PaperInk.copy(alpha = 0.6f),
            cursorColor = Amber))

