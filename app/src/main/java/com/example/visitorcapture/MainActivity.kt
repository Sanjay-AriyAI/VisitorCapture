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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
const val MAX_NOTE_PHOTOS = 5   // handwritten-enquiry images per visit
val EXTRA_EMAIL_LABELS = listOf("Secondary e-mail", "Third e-mail")   // one per extra field, MAX_EMAILS - 1 of them
val STEP_LABELS = listOf("Scan", "Details", "Customer", "Products", "Outcome", "Done")

enum class Screen(val step: Int) {
    Login(-1), Welcome(0), Capture(0), Processing(0), Details(1), Customer(2), Products(3), Catalog(3), LeadOutcome(4), Done(5)
}

data class Form(
    val name: String = "", val title: String = "", val company: String = "",
    val phone: String = "", val email: String = "",
    val extraEmails: List<String> = emptyList(),   // secondary / third e-mail fields, at most MAX_EMAILS - 1
    val website: String = "",
    val address: String = "", val state: String = "", val country: String = "",
    val customerType: String? = null, val marketExposure: String? = null,
    val products: Set<String> = emptySet(), val otherDetail: String = "",
    val outcomes: Set<String> = emptySet(), val notes: String = "",
    val remark: String? = null, val remarkOther: String = "",
    // times picked in the Factory Visit / Follow-up popup (epoch ms, 0 if none); a reminder mail goes 24 hours before
    val factoryVisitAt: Long = 0L, val followUpAt: Long = 0L
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
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
    val snackbar = remember { SnackbarHostState() }                          // replaces Toasts
    var appSettings by remember { mutableStateOf(AppSettings.load(prefs)) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var mailSuccess by remember { mutableStateOf<String?>(null) }                // "test mail sent" pop-up on the home screen
    var catalogProduct by remember { mutableStateOf("Mechanical Spares") }
    var form by remember { mutableStateOf(Form()) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoTaken by remember { mutableStateOf(false) }
    // Optional photo of the back of the card: read together with the front, but never required.
    var backFile by remember { mutableStateOf<File?>(null) }
    var backTaken by remember { mutableStateOf(false) }
    var shootingBack by remember { mutableStateOf(false) }                    // which side the open camera is for
    var ocrError by remember { mutableStateOf(false) }
    // Per-field "please check" notes from the card reader; a note disappears as soon as the visitor edits that field.
    var fieldHints by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var nameError by remember { mutableStateOf(false) }                       // Continue tapped with no name: show the error
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
        form = Form(); photoFile = null; photoTaken = false; backFile = null; backTaken = false
        ocrError = false; fieldHints = emptyMap(); nameError = false; visitStart = 0L; screen = Screen.Welcome
    }

    /** Manual entry: forget any card photos taken for this visit. */
    fun dropPhotos() {
        photoFile = null; photoTaken = false; backFile = null; backTaken = false; ocrError = false; fieldHints = emptyMap()
    }

    fun runOcr(file: File) {
        screen = Screen.Processing
        val back = if (backTaken) backFile else null
        scope.launch {
            val p = try { withContext(Dispatchers.Default) { CardReader.read(ctx, file, back) } } catch (e: Exception) { null }
            if (p == null) { ocrError = true; fieldHints = emptyMap() }
            else {
                form = form.copy(name = p.name, title = p.title, company = p.company, phone = p.phone, email = p.email, extraEmails = p.extraEmails, website = p.website,
                    address = p.address, state = p.state, country = p.country.ifBlank { appSettings.defaultCountry.trim() })
                fieldHints = p.flags; ocrError = false; ocrRaw = p.raw
            }
            screen = Screen.Details
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (shootingBack) { if (ok) backTaken = true else { backFile = null; backTaken = false } }
        else if (ok) photoTaken = true else { photoFile = null; photoTaken = false }
    }
    fun launchCamera() {
        val dir = File(ctx.filesDir, "cards").apply { mkdirs() }
        val f = File(dir, "card_${System.currentTimeMillis()}${if (shootingBack) "_back" else ""}.jpg")
        if (shootingBack) { backFile = f; backTaken = false } else { photoFile = f; photoTaken = false }
        takePicture.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }
    /** Opens the camera for the front of the card, or for the optional back when [back] is true. */
    fun openCamera(back: Boolean = false) {
        shootingBack = back
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
        // A time only counts while its tile is ticked, so a changed mind never leaves a stale reminder behind.
        val factoryAt = if (Reminder.FACTORY.outcome in form.outcomes) form.factoryVisitAt else 0L
        val followAt = if (Reminder.FOLLOW_UP.outcome in form.outcomes) form.followUpAt else 0L
        val uidPrefix = UidGen.prefix(e?.name.orEmpty(), deviceName, at)
        val (mainEmail, extraEmails) = splitEmails(form.email, form.extraEmails)
        val v = Visitor(uid = uidPrefix, deviceName = deviceName,
            name = form.name, title = form.title, company = form.company, email = mainEmail, extraEmails = extraEmails, website = form.website.trim(), phone = form.phone,
            address = form.address.trim(), state = form.state.trim(), country = form.country.trim(),
            customerType = form.customerType ?: "", marketExposure = form.marketExposure ?: "",
            products = form.products.joinToString("; "),
            otherDetail = form.otherDetail, cardPhotoPath = if (photoTaken) photoFile?.absolutePath else null,
            cardBackPhotoPath = if (photoTaken && backTaken) backFile?.absolutePath else null,
            createdAt = at,
            attendedByKey = e?.key.orEmpty(), attendedByName = e?.name.orEmpty(), attendedByTitle = e?.title.orEmpty(),
            attendedByEmail = e?.email.orEmpty(), attendedByPhone = e?.phone.orEmpty(),
            leadOutcomes = LEAD_OUTCOMES.filter { it in form.outcomes }.joinToString("; "),
            discussionNotes = form.notes.trim(), remarks = form.remark.orEmpty(),
            remarksOther = if (form.remark == "Others") form.remarkOther.trim() else "",
            factoryVisitAt = factoryAt, followUpAt = followAt,
            thankYouStatus = ThankYou.initialStatus(mainEmail)).let {
                it.copy(factoryReminderStatus = Reminder.initialStatus(factoryAt, it),
                    followUpReminderStatus = Reminder.initialStatus(followAt, it))
            }
        // "Send automatically" (Settings) queues the mail straight away; otherwise it waits (PENDING) for the Mail settings button.
        val autoSend = appSettings.autoThankYou && v.thankYouStatus == ThankYou.PENDING && SmtpSettings.load(ctx).complete
        extraNote = ""; extraSaved = ""
        scope.launch {
            val uid = UidGen.withSeq(uidPrefix, db.dao().countUidPrefix(uidPrefix))
            lastUid = uid
            val saved = v.copy(uid = uid, thankYouStatus = if (autoSend) ThankYou.QUEUED else v.thankYouStatus)
            db.dao().insert(saved)
            if (autoSend) ThankYouWorker.enqueue(ctx)
            Reminders.schedule(ctx, saved)   // 24-hour reminder mails, sent in the background even if the app is closed
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
        scope.launch { snackbar.showSnackbar("Profile updated") }
    }

    /** Zip [list] (all visitors, or those in [range]) and open the share sheet. */
    fun exportData(list: List<Visitor> = visitors, range: DateRange? = null) {
        if (list.isEmpty()) { scope.launch { snackbar.showSnackbar("No visitors to export") }; return }
        scope.launch {
            val f = withContext(Dispatchers.IO) { Sync.exportZip(ctx, list, employee?.name.orEmpty(), deviceName, range) }
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
        if (manual) { dropPhotos(); screen = Screen.Details }
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

    // ---- appearance: theme + status/navigation bar icons that match it ----
    val dark = resolveDark(appSettings.theme, isSystemInDarkTheme())
    SideEffect {
        val window = (ctx as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
        }
    }

    VisitorTheme(dark) {
    CompositionLocalProvider(LocalContentColor provides LineC, LocalSnackbar provides snackbar) {
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
        Box(Modifier.fillMaxSize().background(Navy).systemBarsPadding().imePadding()) {   // content shrinks above the keyboard
         Column(Modifier.fillMaxSize()) {
          AnimatedContent(section, Modifier.weight(1f).fillMaxWidth(), label = "section", transitionSpec = {
              val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
              (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 8 }) togetherWith fadeOut(tween(140))
          }) { page -> when (page) {
            AppPage.Profile -> ProfileScreen(employee, visitors, onSwitchUser = { switchUser() },
                onEdit = { updated -> editProfile(updated) }, onBack = { section = AppPage.Visit })
            AppPage.Logs -> VisitorLogScreen(visitors, onBack = { section = AppPage.Visit },
                onExport = { list, range -> exportData(list, range) }, onImport = { importPicker.launch(arrayOf("*/*")) },
                onEdit = { edited ->
                    // Already mailed and the visitor-facing details changed: send the "updated, final version" mail.
                    val old = visitors.find { it.uid == edited.uid }
                    val resend = old != null && ThankYou.needsUpdateMail(old, edited)
                    val auto = resend && appSettings.autoThankYou && SmtpSettings.load(ctx).complete
                    val v = if (resend) edited.copy(thankYouStatus = if (auto) ThankYou.QUEUED else ThankYou.PENDING) else edited
                    scope.launch {
                        db.dao().update(v)
                        if (auto) ThankYouWorker.enqueue(ctx)
                        Reminders.schedule(ctx, v)   // re-arms a changed factory visit / follow-up time, disarms a removed one
                        if (resend) snackbar.showSnackbar(if (auto) "Saved. Updated mail will be sent to ${v.mailableEmails().joinToString(", ")}"
                            else "Saved. Updated mail is waiting in Mail settings")
                    }
                },
                onDelete = { v ->
                    // Removed at once with an Undo; photos are only deleted once the snackbar goes away without Undo.
                    scope.launch {
                        db.dao().delete(v)
                        snackbar.currentSnackbarData?.dismiss()
                        val r = snackbar.showSnackbar("${v.name.ifBlank { "Visitor" }} deleted", actionLabel = "Undo",
                            duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) db.dao().insert(v)
                        else withContext(Dispatchers.IO) { v.cardPhotoPath?.let { File(it).delete() }; v.cardBackPhotoPath?.let { File(it).delete() }; Sync.notePhotos(v).forEach { it.delete() } }
                    }
                },
                onClear = { scope.launch { db.dao().clear(); File(ctx.filesDir, "cards").deleteRecursively() } },
                onRetryThankYou = { v ->
                    scope.launch {
                        if (ThankYou.isValidEmail(v.email)) { db.dao().setThankYouStatus(v.uid, ThankYou.QUEUED); ThankYouWorker.enqueue(ctx) }
                    }
                })
            AppPage.Mail -> MailSettingsScreen(visitors, onBack = { section = AppPage.Visit }, onSaved = { msg, tested ->
                section = AppPage.Visit                                   // back to the home screen
                if (tested) mailSuccess = msg else scope.launch { snackbar.showSnackbar(msg) }
            })
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
                  // Each step slides in from the side it comes from: forward from the right, back from the left.
                  AnimatedContent(screen, label = "visit", transitionSpec = {
                      val fwd = targetState.ordinal >= initialState.ordinal
                      (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { if (fwd) it / 4 else -it / 4 } +
                          fadeIn(tween(260, 60))) togetherWith
                          (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (fwd) -it / 6 else it / 6 } +
                              fadeOut(tween(160)))
                  }) { current ->
                    when (current) {
                        Screen.Login -> LoginScreen(employee, onLogin = { signIn(it) },
                            onCancel = if (employee != null) ({ screen = Screen.Welcome }) else null)

                        Screen.Welcome -> Box(Modifier.fillMaxSize()) {
                            // soft amber glow behind the hero
                            Box(Modifier.matchParentSize().background(Brush.radialGradient(
                                listOf(Amber.copy(alpha = if (IsDark) 0.18f else 0.14f), androidx.compose.ui.graphics.Color.Transparent),
                                radius = 720f)))
                            Centered {
                                NarenLogo(Modifier.width(220.dp))
                                Text("Welcome to\nour stand", style = MaterialTheme.typography.displayMedium,
                                    textAlign = TextAlign.Center, color = LineC)
                                Text("Scan your business card and we'll log your visit & interests in under a minute.",
                                    color = Steel, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                                if (appSettings.skipScan) {
                                    PrimaryButton("Enter visitor details", icon = Icons.Default.EditNote) { startVisit(manual = true) }
                                    LinkButton("Scan a business card instead") { startVisit(manual = false) }
                                } else PrimaryButton("Scan business card", icon = Icons.Default.CameraAlt) { startVisit(manual = false) }
                                // at-a-glance numbers for staff; the mail tile opens Mail settings
                                val today = remember(visitors) { startOfToday() }
                                val unsent = visitors.count { it.thankYouStatus == ThankYou.PENDING }
                                val failed = visitors.count { it.thankYouStatus == ThankYou.FAILED }
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatChip("Today", visitors.count { it.createdAt >= today }, Modifier.weight(1f)) {
                                        section = AppPage.Logs
                                    }
                                    StatChip("All visitors", visitors.size, Modifier.weight(1f)) { section = AppPage.Logs }
                                    StatChip(if (failed > 0) "$failed mail failed" else "Mails to send", unsent + failed,
                                        Modifier.weight(1f), highlight = unsent + failed > 0) {
                                        section = AppPage.Mail
                                    }
                                }
                            }
                        }

                        Screen.Capture -> Centered {
                            Text(if (photoTaken) "Front side captured" else "Photograph the front", style = MaterialTheme.typography.headlineMedium, color = LineC)
                            Text(if (photoTaken) "Add the back too if it has more details. It's optional."
                                else "Lay it flat, good light, fill the frame.", color = Steel, textAlign = TextAlign.Center)
                            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(CardShape)
                                .background(if (IsDark) NavyDeep else androidx.compose.ui.graphics.Color(0xFFE6EBF1))
                                .viewfinder(Amber), contentAlignment = Alignment.Center) {
                                if (photoTaken && photoFile != null)
                                    AsyncImage(photoFile, "Card", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CreditCard, null, tint = Steel, modifier = Modifier.size(44.dp))
                                    Text("No photo yet", color = Steel)
                                }
                            }
                            // Optional back side: only offered once the front is in.
                            if (photoTaken) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Steel.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable { openCamera(back = true) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(88.dp).aspectRatio(1.6f).clip(RoundedCornerShape(8.dp))
                                    .background(if (IsDark) NavyDeep else androidx.compose.ui.graphics.Color(0xFFE6EBF1)),
                                    contentAlignment = Alignment.Center) {
                                    if (backTaken && backFile != null)
                                        AsyncImage(backFile, "Back of card", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    else Icon(Icons.Default.AddAPhoto, null, tint = Steel, modifier = Modifier.size(26.dp))
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(if (backTaken) "Back side added" else "Add back side", color = LineC, fontWeight = FontWeight.SemiBold)
                                    Text(if (backTaken) "Tap to retake" else "Optional", color = Steel, fontSize = 13.sp)
                                }
                                if (backTaken) IconButton({ backFile?.delete(); backFile = null; backTaken = false }) {
                                    Icon(Icons.Default.Close, "Remove back side", tint = Steel)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SecondaryButton("Back", icon = Icons.AutoMirrored.Filled.ArrowBack) { screen = Screen.Welcome }
                                if (photoTaken) PrimaryButton(if (backTaken) "Use photos" else "Use photo", icon = Icons.Default.Check) { photoFile?.let { runOcr(it) } }
                                else PrimaryButton("Open camera", icon = Icons.Default.CameraAlt) { openCamera() }
                            }
                            if (photoTaken) LinkButton("Retake front") { openCamera() }
                            LinkButton("Skip scan, enter details manually") { dropPhotos(); screen = Screen.Details }
                        }

                        Screen.Processing -> Centered {
                            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(CardShape).background(NavyDeep)
                                .scanSweep(Amber).viewfinder(Amber), contentAlignment = Alignment.Center) {
                                photoFile?.let { AsyncImage(it, "Card", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.85f) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Amber, strokeWidth = 2.5.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(if (backTaken) "Reading both sides…" else "Reading the card…", style = MaterialTheme.typography.titleMedium, color = LineC)
                            }
                            Text("Name, company, phone and e-mail are picked up automatically.", color = Steel, textAlign = TextAlign.Center)
                        }

                        Screen.Details -> Scrolling {
                            // header: title + the scanned card, if there is one
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Confirm details", style = MaterialTheme.typography.headlineMedium, color = LineC)
                                    Text(if (photoTaken) "We prefilled what we could read. Fix anything that's off."
                                        else "Type in the visitor's details.", fontSize = 14.sp, color = Steel)
                                }
                                if (photoTaken && photoFile != null) {
                                    Spacer(Modifier.width(12.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AsyncImage(photoFile, "Scanned card, front", Modifier.width(96.dp).aspectRatio(1.6f).clip(RoundedCornerShape(10.dp))
                                            .border(1.5.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                        if (backTaken && backFile != null)
                                            AsyncImage(backFile, "Scanned card, back", Modifier.width(96.dp).aspectRatio(1.6f).clip(RoundedCornerShape(10.dp))
                                                .border(1.5.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                            if (ocrError) Notice(Icons.Default.ErrorOutline, "Couldn't auto-read the card. Fields are blank, just fill them in.", Danger, Color_F0C3B8)
                            if (fieldHints.isNotEmpty()) Notice(Icons.Default.Info, "Fields outlined in amber need a quick check against the card.", Amber, AmberText)

                            FormSection("Person", Icons.Default.Person) {
                                PaperField("Full name *", form.name, Icons.Default.Badge, hint = fieldHints["name"], caps = KeyboardCapitalization.Words,
                                    error = if (nameError && form.name.isBlank()) "Name is required" else null) { form = form.copy(name = it); fieldHints = fieldHints - "name" }
                                PaperField("Job title", form.title, Icons.Default.Work, hint = fieldHints["title"], caps = KeyboardCapitalization.Words) { form = form.copy(title = it); fieldHints = fieldHints - "title" }
                            }
                            FormSection("Company & contact", Icons.Default.Business) {
                                PaperField("Company", form.company, Icons.Default.Apartment, hint = fieldHints["company"], caps = KeyboardCapitalization.Words) { form = form.copy(company = it); fieldHints = fieldHints - "company" }
                                PaperField("Phone", form.phone, Icons.Default.Phone, hint = fieldHints["phone"], keyboard = KeyboardType.Phone) { form = form.copy(phone = it); fieldHints = fieldHints - "phone" }
                                PaperField("E-mail", form.email, Icons.Default.Email, hint = fieldHints["email"], keyboard = KeyboardType.Email) { form = form.copy(email = it); fieldHints = fieldHints - "email" }
                                // Extra addresses: filled when the card shows more than one, or added by hand (max MAX_EMAILS in all).
                                form.extraEmails.forEachIndexed { i, mail ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PaperField(EXTRA_EMAIL_LABELS[i], mail, Icons.Default.AlternateEmail, Modifier.weight(1f), keyboard = KeyboardType.Email) { v ->
                                            form = form.copy(extraEmails = form.extraEmails.toMutableList().also { it[i] = v })
                                        }
                                        IconButton({ form = form.copy(extraEmails = form.extraEmails.filterIndexed { j, _ -> j != i }) }) {
                                            Icon(Icons.Default.Close, "Remove ${EXTRA_EMAIL_LABELS[i].lowercase()}", tint = Steel)
                                        }
                                    }
                                }
                                if (form.extraEmails.size < MAX_EMAILS - 1)
                                    LinkButton(if (form.extraEmails.isEmpty()) "+ Add secondary e-mail" else "+ Add another e-mail") {
                                        form = form.copy(extraEmails = form.extraEmails + "")
                                    }
                                PaperField("Website", form.website, Icons.Default.Language, hint = fieldHints["website"], keyboard = KeyboardType.Uri) {
                                    form = form.copy(website = it); fieldHints = fieldHints - "website"
                                }
                            }
                            FormSection("Location", Icons.Default.Place) {
                                PaperField("Address", form.address, Icons.Default.Home, singleLine = false, hint = fieldHints["address"], caps = KeyboardCapitalization.Words) { form = form.copy(address = it); fieldHints = fieldHints - "address" }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PaperField("State", form.state, null, Modifier.weight(1f), hint = fieldHints["state"], caps = KeyboardCapitalization.Words) { form = form.copy(state = it); fieldHints = fieldHints - "state" }
                                    PaperField("Country", form.country, null, Modifier.weight(1f), caps = KeyboardCapitalization.Words, last = true) { form = form.copy(country = it) }
                                }
                            }
                            NavRow(
                                { SecondaryButton("Back", icon = Icons.AutoMirrored.Filled.ArrowBack) { screen = detailsBack } },
                                { PrimaryButton("Continue", trailingIcon = Icons.AutoMirrored.Filled.ArrowForward) {
                                    // the visitor's name is mandatory
                                    if (form.name.isBlank()) { nameError = true; scope.launch { snackbar.showSnackbar("Enter the visitor's full name to continue") } }
                                    else { nameError = false; screen = Screen.Customer }
                                } })
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
                            SuccessCheck(Amber, OnAmber, 92.dp)
                            Text(if (first.isNotBlank()) "Welcome, $first!" else "Welcome!",
                                style = MaterialTheme.typography.displaySmall, color = LineC, textAlign = TextAlign.Center)
                            if (form.company.isNotBlank()) Text(form.company, color = AmberText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            val place = listOf(form.state, form.country).filter { it.isNotBlank() }.joinToString(", ")
                            if (place.isNotBlank()) Text(place, color = LineDim, fontSize = 14.sp)
                            val summary = listOfNotNull(form.customerType, form.marketExposure) + form.products.groupBy { it.substringBefore(" > ") }
                                .map { (p, l) -> if (l.any { " > " in it }) "$p (${l.size})" else p }
                            Text(summary.joinToString("  •  "), color = AmberText, textAlign = TextAlign.Center)
                            Text("Visit logged " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(loggedAt)) +
                                (employee?.let { " · attended by ${it.name}" } ?: ""), color = Steel, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Anything else you're interested in?", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(extraNote, { extraNote = it.take(1000) },
                                    placeholder = { Text("Additional products, models, makes or quantities") },
                                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
                                Text("Have a handwritten enquiry? Attach a photo of it.", color = Steel, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SecondaryButton("Take photo", Modifier.weight(1f), icon = Icons.Default.PhotoCamera) { openNoteCamera() }
                                    SecondaryButton("Upload", Modifier.weight(1f), icon = Icons.Default.Image) { openNoteUpload() }
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
                            PrimaryButton("Log next visitor", icon = Icons.Default.PersonAdd) { reset() }
                        }
                    }
                  }   // AnimatedContent(screen)
                }
            }
          } }   // when (page) / AnimatedContent
          // developer credit on every page; hidden while typing so the keyboard keeps the full height
          @OptIn(ExperimentalLayoutApi::class)
          val imeOpen = WindowInsets.isImeVisible
          if (!imeOpen) BrandFooter()
         }   // Column

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
            mailSuccess?.let { msg ->
                AlertDialog(onDismissRequest = { mailSuccess = null },
                    icon = { Icon(Icons.Default.CheckCircle, null, tint = Amber, modifier = Modifier.size(40.dp)) },
                    title = { Text("Mail settings saved", textAlign = TextAlign.Center) },
                    text = { Text(msg, textAlign = TextAlign.Center) },
                    confirmButton = { TextButton({ mailSuccess = null }) { Text("OK", color = AmberText, fontWeight = FontWeight.Bold) } })
            }
            AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
      }
    }
    }   // VisitorTheme
}

val Color_F0C3B8: androidx.compose.ui.graphics.Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.dangerText

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
fun PaperField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, modifier: Modifier = Modifier.fillMaxWidth(),
               singleLine: Boolean = true, hint: String? = null, keyboard: KeyboardType = KeyboardType.Text,
               caps: KeyboardCapitalization = KeyboardCapitalization.None, last: Boolean = false, error: String? = null,
               onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        minLines = if (singleLine) 1 else 2,
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(20.dp)) } },
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = keyboard,
            imeAction = if (last) ImeAction.Done else ImeAction.Next),
        // A hint means the card reader was unsure: amber border plus a short note until the visitor edits the field.
        isError = error != null,
        supportingText = (error ?: hint)?.let { msg -> { Text(msg, color = if (error != null) Color_F0C3B8 else AmberText) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LineC, unfocusedTextColor = LineC, cursorColor = Amber,
            focusedContainerColor = if (IsDark) NavyDeep.copy(alpha = 0.35f) else Navy,
            unfocusedContainerColor = if (IsDark) NavyDeep.copy(alpha = 0.25f) else Navy,
            focusedBorderColor = Amber, unfocusedBorderColor = if (hint != null) Amber else if (IsDark) LineC.copy(alpha = 0.16f) else Outline,
            focusedLabelColor = AmberText, unfocusedLabelColor = Steel,
            focusedLeadingIconColor = AmberText, unfocusedLeadingIconColor = Steel,
            errorContainerColor = if (IsDark) NavyDeep.copy(alpha = 0.25f) else Navy, errorTextColor = LineC,
            errorBorderColor = Danger, errorLabelColor = Color_F0C3B8, errorLeadingIconColor = Color_F0C3B8, errorCursorColor = Amber))

/** Titled card grouping related form fields (Details screen). */
@Composable
fun FormSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().cardBorder().clip(CardShape)
        .background(if (IsDark) androidx.compose.ui.graphics.Color(0xFF244D70) else Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Amber.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AmberText, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = LineDim)
        }
        content()
    }

/** Tinted banner with an icon, e.g. "Fields outlined in amber need a quick check". */
@Composable
fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color,
           textColor: androidx.compose.ui.graphics.Color) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = textColor, fontWeight = FontWeight.Medium)
    }

