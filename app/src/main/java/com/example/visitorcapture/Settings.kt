package com.example.visitorcapture

import android.content.SharedPreferences

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** App preferences shown on the Settings page; stored next to the device name in the "settings" SharedPreferences. */
data class AppSettings(
    val keepAwake: Boolean = true,        // screen stays on while the app is open at the stand
    val skipScan: Boolean = false,        // start each visit with manual entry instead of the card camera
    val defaultCountry: String = "",      // filled in when the card does not show a country
    val confirmAbandon: Boolean = true,   // ask before "Start new visit" throws away a visit in progress
    val autoThankYou: Boolean = false     // queue the thank-you mail as soon as a visit is saved
) {
    fun save(p: SharedPreferences) = p.edit()
        .putBoolean("keep_awake", keepAwake).putBoolean("skip_scan", skipScan)
        .putString("default_country", defaultCountry).putBoolean("confirm_abandon", confirmAbandon)
        .putBoolean("auto_thankyou", autoThankYou).remove("theme").apply()   // "theme": old Appearance setting

    companion object {
        fun load(p: SharedPreferences) = AppSettings(
            keepAwake = p.getBoolean("keep_awake", true),
            skipScan = p.getBoolean("skip_scan", false),
            defaultCountry = p.getString("default_country", "").orEmpty(),
            confirmAbandon = p.getBoolean("confirm_abandon", true),
            autoThankYou = p.getBoolean("auto_thankyou", false))
    }
}

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

// ---------------------------------------------------------------- Settings (gear)

@Composable
fun SettingsScreen(
    deviceName: String, onDeviceName: (String) -> Unit,
    settings: AppSettings, onChange: (AppSettings) -> Unit,
    mailConfigured: Boolean, onOpen: (AppPage) -> Unit, onBack: () -> Unit
) = SectionPage("Settings", onBack) {
    SectionHeader("Device")
    SettingsGroup {
        Box(Modifier.padding(16.dp)) {
            OutlinedTextField(deviceName, onDeviceName, label = { Text("Device name") },
                supportingText = { Text("Used in exports and visitor IDs, e.g. Stand-A") },
                leadingIcon = { Icon(Icons.Default.Smartphone, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
        }
        GroupDivider()
        SettingSwitch(Icons.Default.LightMode, "Keep screen awake", "The screen stays on while the app is open",
            settings.keepAwake) { onChange(settings.copy(keepAwake = it)) }
    }

    SectionHeader("Visit flow")
    SettingsGroup {
        SettingSwitch(Icons.Default.Keyboard, "Skip card scan", "Start each visit with manual entry",
            settings.skipScan) { onChange(settings.copy(skipScan = it)) }
        GroupDivider()
        Box(Modifier.padding(16.dp)) {
            OutlinedTextField(settings.defaultCountry, { onChange(settings.copy(defaultCountry = it.take(60))) },
                label = { Text("Default country") },
                supportingText = { Text("Filled in when the card doesn't show a country. Leave blank for none.") },
                leadingIcon = { Icon(Icons.Default.Public, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
        }
        GroupDivider()
        SettingSwitch(Icons.Default.Shield, "Confirm before discarding", "Ask before \"Start new visit\" drops a visit in progress",
            settings.confirmAbandon) { onChange(settings.copy(confirmAbandon = it)) }
    }

    SectionHeader("Thank-you mail")
    SettingsGroup {
        SettingSwitch(Icons.Default.MarkEmailRead, "Send automatically", "Queue the thank-you mail as soon as a visit is saved",
            settings.autoThankYou) { onChange(settings.copy(autoThankYou = it)) }
        GroupDivider()
        SettingRow(Icons.Default.Email, "Mail account", if (mailConfigured) "Configured" else "Not set up",
            onClick = { onOpen(AppPage.Mail) }) { Chevron() }
    }
    if (settings.autoThankYou && !mailConfigured)
        Text("Set up the mail account, otherwise nothing can be sent.", color = Color_F0C3B8, fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))

    SectionHeader("Data & backup")
    SettingsGroup {
        SettingRow(Icons.Default.Storage, "Export, import & clear", "Open Visitor logs, then the ⋮ menu",
            onClick = { onOpen(AppPage.Logs) }) { Chevron() }
    }

    SectionHeader("About")
    SettingsGroup {
        SettingRow(Icons.Default.Info, "Visitor Capture", "Version ${BuildConfig.VERSION_NAME}",
            onClick = { onOpen(AppPage.About) }) { Chevron() }
    }
}

// ---------------------------------------------------------------- Profile

@Composable
fun ProfileScreen(employee: EmployeeInfo?, visitors: List<Visitor>, onSwitchUser: () -> Unit,
                  onEdit: (EmployeeInfo) -> Unit, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    SectionPage("Profile", onBack, actions = {
        if (employee != null) IconButton({ editing = true }) { Icon(Icons.Default.Edit, "Edit profile", tint = LineC) }
    }) {
        if (employee == null) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar("", size = 72.dp, container = Panel, content = Steel)
                Text("Nobody is signed in on this phone.", color = Steel)
                PrimaryButton("Sign in") { onSwitchUser() }
            }
            return@SectionPage
        }
        val ctx = LocalContext.current
        val staff by remember { AppDb.get(ctx).employeeDao().all() }.collectAsState(initial = emptyList())
        val record = staff.firstOrNull { it.empKey == employee.key }
        val mine = visitors.filter { it.attendedByKey == employee.key || (it.attendedByKey.isBlank() && it.attendedByName == employee.name) }
        val today = remember { startOfToday() }
        val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(employee.name, size = 84.dp)
            Text(employee.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = LineC,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            if (employee.title.isNotBlank()) Text(employee.title, color = LineDim, textAlign = TextAlign.Center)
            if (employee.company.isNotBlank()) Text(employee.company, color = AmberText, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Visits today", mine.count { it.createdAt >= today }, Modifier.weight(1f))
            StatTile("Visits total", mine.size, Modifier.weight(1f))
        }

        SectionHeader("Contact")
        SettingsGroup {
            SettingRow(Icons.Default.Email, employee.email.ifBlank { "No e-mail" }, "E-mail")
            GroupDivider()
            SettingRow(Icons.Default.Phone, employee.phone.ifBlank { "No phone" }, "Phone")
        }
        if (record != null) {
            SectionHeader("Sign-in")
            SettingsGroup {
                SettingRow(Icons.Default.Login, fmt.format(Date(record.firstLoginAt)), "First signed in on this phone")
                GroupDivider()
                SettingRow(Icons.Default.History, fmt.format(Date(record.lastLoginAt)), "Last signed in")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Edit profile", Modifier.weight(1f)) { editing = true }
            SecondaryButton("Switch user", Modifier.weight(1f)) { onSwitchUser() }
        }
    }
    if (editing && employee != null) EditProfileDialog(employee, onDismiss = { editing = false },
        onSave = { onEdit(it); editing = false })
}

/** Correct the signed-in staff member's details (name, title, company, e-mail, phone). */
@Composable
fun EditProfileDialog(e: EmployeeInfo, onDismiss: () -> Unit, onSave: (EmployeeInfo) -> Unit) {
    var name by remember { mutableStateOf(e.name) }
    var title by remember { mutableStateOf(e.title) }
    var company by remember { mutableStateOf(e.company) }
    var email by remember { mutableStateOf(e.email) }
    var phone by remember { mutableStateOf(e.phone) }
    val emailOk = email.isBlank() || ThankYou.isValidEmail(email)
    val canSave = name.isNotBlank() && emailOk
    @Composable fun field(label: String, value: String, icon: ImageVector, error: String? = null, set: (String) -> Unit) =
        OutlinedTextField(value, set, label = { Text(label) }, singleLine = true, leadingIcon = { Icon(icon, null) },
            isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = darkFieldColors(filled = false))
    FormSheet("Edit profile", onDismiss, icon = Icons.Default.Edit, saveEnabled = canSave, onSave = {
        onSave(e.copy(name = name.trim(), title = title.trim(), company = company.trim(), email = email.trim(), phone = phone.trim()))
    }) {
        field("Name", name, Icons.Default.Person, if (name.isBlank()) "Name is required" else null) { name = it }
        field("Job title", title, Icons.Default.Badge) { title = it }
        field("Company", company, Icons.Default.Business) { company = it }
        field("E-mail", email, Icons.Default.Email, if (!emailOk) "Enter a full e-mail like name@company.com" else null) { email = it }
        field("Phone", phone, Icons.Default.Phone) { phone = it }
        Text("Visits you attended on this phone are updated too.", fontSize = 12.sp, color = Steel)
    }
}

@Composable
fun StatTile(label: String, value: Int, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = LineC) =
    Column(modifier.cardBorder().clip(CardShape).background(Panel).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Steel)
    }

// ---------------------------------------------------------------- About

@Composable
fun AboutScreen(deviceName: String, visitorCount: Int, onBack: () -> Unit) = SectionPage("About", onBack) {
    val ctx = LocalContext.current
    val debug = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandLogo(Modifier.width(220.dp))
        Text("Visitor Capture", fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Display, color = LineC)
        Text("Version ${BuildConfig.VERSION_NAME}" + if (debug) " (debug)" else "", color = Steel)
    }
    SectionHeader("This phone")
    SettingsGroup {
        SettingRow(Icons.Default.Smartphone, deviceName, "Device name")
        GroupDivider()
        SettingRow(Icons.Default.Groups, visitorCount.toString(), "Visitors stored")
    }
    Text("Visitors are stored on this phone only. Export the log regularly and merge exports from all stand phones.",
        color = Steel, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
    BrandCreditBlock(Modifier.padding(top = 28.dp, bottom = 12.dp))
}

// ---------------------------------------------------------------- Mail settings

/** Sender account for the thank-you mail plus the send queue. "Save & test" mails the account itself, so mistakes show up at once. */
@Composable
fun MailSettingsScreen(visitors: List<Visitor>, onBack: () -> Unit, onSaved: (message: String, tested: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val initial = remember { SmtpSettings.load(ctx) }
    var host by remember { mutableStateOf(initial.host.ifBlank { "smtp.gmail.com" }) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.user) }
    var password by remember { mutableStateOf(initial.password) }
    var showPassword by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(initial.from) }
    var fromName by remember { mutableStateOf(initial.fromName) }
    var coAddress by remember { mutableStateOf(initial.company.address) }
    var coPhone by remember { mutableStateOf(initial.company.phone) }
    var coEmail by remember { mutableStateOf(initial.company.email) }
    var coWebsite by remember { mutableStateOf(initial.company.website) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }   // message, isError
    var busy by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(initial.complete) }
    fun current() = SmtpSettings(host.trim(), port.trim().toIntOrNull() ?: 587, user.trim(), password, from.trim(), fromName.trim(),
        CompanyInfo(coAddress.trim(), coPhone.trim(), coEmail.trim(), coWebsite.trim()))

    val unsent = visitors.count { it.thankYouStatus == ThankYou.PENDING }
    val queued = visitors.count { it.thankYouStatus == ThankYou.QUEUED }
    val sent = visitors.count { it.thankYouStatus == ThankYou.SENT }
    val failed = visitors.count { it.thankYouStatus == ThankYou.FAILED }

    SectionPage("Mail settings", { if (!busy) onBack() }) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (configured) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
                tint = if (configured) Amber else Danger, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (configured) "Mail account configured" else "Mail account not set up", fontWeight = FontWeight.SemiBold, color = LineC)
                Text(if (configured) "Thank-you mails are sent from ${current().from.ifBlank { current().user }}"
                    else "Fill in the account below to send thank-you mails.", fontSize = 13.sp, color = Steel)
            }
        }

        SectionHeader("Thank-you mails")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("To send", unsent, Modifier.weight(1f), if (unsent > 0) Amber else LineC)
            StatTile("Queued", queued, Modifier.weight(1f))
            StatTile("Sent", sent, Modifier.weight(1f))
            StatTile("Failed", failed, Modifier.weight(1f), if (failed > 0) Color_F0C3B8 else LineC)
        }
        if (unsent > 0) PrimaryButton("Send thank-you mails ($unsent)", modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (!configured) scope.launch { snackbar.showSnackbar("Save the mail account first") }
            else scope.launch {
                val n = AppDb.get(ctx).dao().queueAllThankYou()
                ThankYouWorker.enqueue(ctx)
                snackbar.showSnackbar("$n thank-you mail(s) queued. They send as soon as the phone is online.")
            }
        }
        if (failed > 0) Text("Failed mails can be retried from Visitor logs (open the visitor card).",
            color = Steel, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

        SectionHeader("Account")
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("For Gmail use smtp.gmail.com, port 587 and an App Password " +
                    "(Google account › Security › 2-Step Verification › App passwords).", fontSize = 12.sp, color = Steel)
                MailField("SMTP server", host, Icons.Default.Dns) { host = it }
                MailField("Port (587 or 465)", port, Icons.Default.Numbers) { port = it.filter(Char::isDigit).take(5) }
                MailField("Username / e-mail", user, Icons.Default.AlternateEmail) { user = it }
                OutlinedTextField(password, { password = it }, label = { Text("Password / app password") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    trailingIcon = {
                        IconButton({ showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
                MailField("Send from (blank = username)", from, Icons.Default.Outbox) { from = it }
                MailField("Company name in the e-mail", fromName, Icons.Default.Business) { fromName = it }
            }
        }

        SectionHeader("Company details (shown in the mail)")
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Shown to the visitor under \"Contact us\". Leave blank to hide a line.", fontSize = 12.sp, color = Steel)
                OutlinedTextField(coAddress, { coAddress = it }, label = { Text("Address") }, minLines = 2, maxLines = 4,
                    leadingIcon = { Icon(Icons.Default.Place, null) }, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
                MailField("Phone", coPhone, Icons.Default.Phone) { coPhone = it }
                MailField("E-mail", coEmail, Icons.Default.Email) { coEmail = it }
                MailField("Website", coWebsite, Icons.Default.Language) { coWebsite = it }
            }
        }
        status?.let { (msg, err) ->
            Text(msg, fontSize = 13.sp, color = if (err) Color_F0C3B8 else AmberText, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Save", Modifier.weight(1f)) {
                if (busy) return@SecondaryButton
                val c = current(); SmtpSettings.save(ctx, c); configured = c.complete; Reminders.runNow(ctx)
                // saved: close Mail settings and go home; the message is shown there
                onSaved(if (c.complete) "Mail settings saved" else "Saved. Server, username and password are all needed to send mails.", false)
            }
            PrimaryButton(if (busy) "Sending…" else "Save & test", enabled = !busy && current().complete, modifier = Modifier.weight(1f)) {
                val c = current()
                if (!ThankYou.isValidEmail(c.user) && !ThankYou.isValidEmail(c.from)) {
                    status = "Enter a full e-mail address (name@company.com) as the username." to true; return@PrimaryButton
                }
                if (c.from.isNotBlank() && !ThankYou.isValidEmail(c.from)) {
                    status = "\"Send from\" must be a full e-mail address like name@company.com, or left blank." to true; return@PrimaryButton
                }
                busy = true; status = "Sending test mail…" to false
                scope.launch {
                    val err = withContext(Dispatchers.IO) { try { ThankYou.sendTest(c); null } catch (e: Exception) { e.message ?: e.javaClass.simpleName } }
                    busy = false
                    // only a successful test closes the page; a failure stays here with the error
                    if (err == null) { SmtpSettings.save(ctx, c); configured = true; Reminders.runNow(ctx); status = null; onSaved("A test mail was sent to ${c.from.ifBlank { c.user }}. Thank-you mails will be sent from this account.", true) }
                    else status = "Could not send: $err" to true
                }
            }
        }
    }
}

@Composable
private fun MailField(label: String, value: String, icon: ImageVector, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(icon, null) }, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
