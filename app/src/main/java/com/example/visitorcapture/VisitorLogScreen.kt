package com.example.visitorcapture

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private enum class LogFilter(val label: String) { All("All"), Today("Today"), Pending("Mail pending"), Failed("Mail failed") }

private fun LogFilter.matches(v: Visitor, today: Long) = when (this) {
    LogFilter.All -> true
    LogFilter.Today -> v.createdAt >= today
    LogFilter.Pending -> v.thankYouStatus == ThankYou.PENDING || v.thankYouStatus == ThankYou.QUEUED
    LogFilter.Failed -> v.thankYouStatus == ThankYou.FAILED
}

/** "Today", "Yesterday" or e.g. "Fri, 13 Nov 2026" for the sticky headers. */
private fun dayLabel(at: Long): String {
    val today = startOfToday()
    return when {
        at >= today -> "Today"
        at >= today - 86_400_000L -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(at))
    }
}

/** Every visitor saved on this phone, newest first. Export / import / clear sit in the ⋮ menu of the top bar. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VisitorLogScreen(
    visitors: List<Visitor>, onBack: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit,
    onEdit: (Visitor) -> Unit, onDelete: (Visitor) -> Unit, onClear: () -> Unit, onRetryThankYou: (Visitor) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LogFilter.All) }
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var editing by remember { mutableStateOf<Visitor?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var clearText by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
    val today = remember { startOfToday() }
    val shown = remember(visitors, query, filter) {
        val q = query.trim().lowercase()
        visitors.filter { v ->
            filter.matches(v, today) && (q.isEmpty() ||
                listOf(v.name, v.company, v.email, v.phone, v.title).any { it.lowercase().contains(q) })
        }
    }

    Column(Modifier.fillMaxSize()) {
        SectionTopBar("Visitor logs (${visitors.size})", onBack) {
            Box {
                IconButton({ menuOpen = true }) { Icon(Icons.Default.MoreVert, "More options", tint = LineC) }
                DropdownMenu(menuOpen, { menuOpen = false }, containerColor = PanelOn) {
                    DropdownMenuItem({ Text("Export (zip)", color = LineC) }, { menuOpen = false; onExport() },
                        leadingIcon = { Icon(Icons.Default.Upload, null, tint = LineDim) }, enabled = visitors.isNotEmpty())
                    DropdownMenuItem({ Text("Import / merge", color = LineC) }, { menuOpen = false; onImport() },
                        leadingIcon = { Icon(Icons.Default.Download, null, tint = LineDim) })
                    HorizontalDivider(color = LineC.copy(alpha = 0.1f))
                    DropdownMenuItem({ Text("Clear all", color = Color_F0C3B8) }, { menuOpen = false; confirmClear = true },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = Danger) }, enabled = visitors.isNotEmpty())
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true,
                placeholder = { Text("Search name, company, e-mail, phone") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear search") } }) else null,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogFilter.values().forEach { f ->
                    val n = visitors.count { f.matches(it, today) }
                    OptionChip("${f.label} ($n)", filter == f) { filter = f }
                }
            }
        }
        if (shown.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Amber.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(if (visitors.isEmpty()) Icons.Default.GroupAdd else Icons.Default.SearchOff, null,
                    tint = AmberText, modifier = Modifier.size(36.dp))
            }
            Text(if (visitors.isEmpty()) "No visitors yet" else "No visitors match", style = MaterialTheme.typography.titleLarge, color = LineC)
            Text(if (visitors.isEmpty()) "Visitors you log will appear here." else "Try another search or filter.", color = Steel)
        }
        // newest first, grouped under sticky day headers
        val days = remember(shown) { shown.groupBy { dayLabel(it.createdAt) } }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
          days.forEach { (day, list) ->
            stickyHeader(key = "day_$day") {
                Row(Modifier.fillMaxWidth().background(Navy).padding(top = 6.dp, bottom = 4.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(day.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Steel)
                    Spacer(Modifier.width(8.dp))
                    Pill(list.size.toString(), Steel, LineDim)
                }
            }
            items(list, key = { it.id }) { v ->
                VisitorCard(v, fmt, expanded = v.id in expanded,
                    onToggle = { expanded = if (v.id in expanded) expanded - v.id else expanded + v.id },
                    onEdit = { editing = v }, onDelete = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onDelete(v) }, onRetry = { onRetryThankYou(v) },
                    modifier = Modifier.animateItem())
            }
          }
        }
    }

    editing?.let { v -> EditVisitorDialog(v, onDismiss = { editing = null }, onSave = { onEdit(it); editing = null }) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false; clearText = "" },
        icon = { Icon(Icons.Default.Warning, null, tint = Danger) },
        title = { Text("Delete all ${visitors.size} visitors?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Every visitor on this phone, with card photos and attached images, will be permanently deleted. " +
                    "Export first if this data hasn't been merged yet.")
                OutlinedTextField(clearText, { clearText = it }, singleLine = true,
                    label = { Text("Type DELETE to confirm") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton({ onClear(); confirmClear = false; clearText = "" }, enabled = clearText.trim() == "DELETE") {
                Text("Delete all", color = if (clearText.trim() == "DELETE") Danger else Steel)
            }
        },
        dismissButton = {
            Row {
                TextButton({ confirmClear = false; clearText = ""; onExport() }) { Text("Export first") }
                TextButton({ confirmClear = false; clearText = "" }) { Text("Cancel") }
            }
        })
}

/** Compact card: who, when and a few tags. Tapping it slides open the full record. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisitorCard(v: Visitor, fmt: SimpleDateFormat, expanded: Boolean, onToggle: () -> Unit,
                        onEdit: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier.fillMaxWidth().pressScale(src, 0.985f).cardBorder().clip(CardShape).background(Panel)
        .clickable(src, ripple(), onClick = onToggle)
        .padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (v.cardPhotoPath != null)
                AsyncImage(File(v.cardPhotoPath), "Business card", Modifier.padding(top = 2.dp).size(width = 56.dp, height = 38.dp)
                    .clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            else Avatar(v.name, size = 40.dp, container = PanelOn, content = AmberText)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(v.name.ifBlank { "Unnamed visitor" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LineC,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(v.title, v.company).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = LineDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(fmt.format(Date(v.createdAt)) + (if (v.attendedByName.isNotBlank()) " · ${v.attendedByName}" else ""),
                    fontSize = 12.sp, color = Steel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Visitor options", tint = Steel) }
                DropdownMenu(menu, { menu = false }, containerColor = PanelOn) {
                    DropdownMenuItem({ Text("Edit", color = LineC) }, { menu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = LineDim) })
                    DropdownMenuItem({ Text("Delete", color = Color_F0C3B8) }, { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Danger) })
                }
            }
        }
        FlowRow(Modifier.padding(top = 8.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(v.customerType, v.marketExposure).filter { it.isNotBlank() }.forEach { Pill(it) }
            when (v.thankYouStatus) {
                ThankYou.SENT -> Pill("Mail sent", Steel, LineDim)
                ThankYou.QUEUED -> Pill("Mail queued", Amber)
                ThankYou.PENDING -> Pill("Mail not sent", Amber)
                ThankYou.FAILED -> Pill("Mail failed", Danger, Color_F0C3B8)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = LineC.copy(alpha = 0.08f))
                Detail("Contact", listOf(v.email, v.phone).filter { it.isNotBlank() }.joinToString(" · "))
                Detail("Address", listOf(v.address, v.state, v.country).filter { it.isNotBlank() }.joinToString(", "))
                Detail("Products", Catalog.summarize(v.products.split("; ").filter { it.isNotBlank() }))
                Detail("Other products", v.otherDetail)
                Detail("Also interested in", v.additionalInterest)
                val notes = Sync.notePhotos(v)
                if (notes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    notes.forEach {
                        AsyncImage(it, "Handwritten enquiry", Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop)
                    }
                }
                Detail("Outcome", v.leadOutcomes.replace("; ", " · "))
                Detail("Remarks", if (v.remarks == "Others" && v.remarksOther.isNotBlank()) "Others: ${v.remarksOther}" else v.remarks)
                Detail("Follow-up", listOf(v.followUpAction, if (v.followUpAt > 0) fmt.format(Date(v.followUpAt)) else "", v.followUpNote)
                    .filter { it.isNotBlank() }.joinToString(" · "))
                Detail("Discussion notes", v.discussionNotes)
                when (v.thankYouStatus) {
                    ThankYou.SENT -> Detail("Thank-you mail", "Sent " + fmt.format(Date(v.thankYouSentAt)))
                    ThankYou.QUEUED -> Detail("Thank-you mail", "Queued, sends when online")
                    ThankYou.PENDING -> Detail("Thank-you mail", "Not sent yet")
                    ThankYou.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Detail("Thank-you mail", "Failed", Modifier.weight(1f))
                        TextButton(onRetry) { Text("Retry", color = AmberText) }
                    }
                }
                Text("ID: ${v.uid}", fontSize = 11.sp, color = Steel)
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "Show less" else "Show more", color = AmberText, fontSize = 12.sp)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = AmberText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Detail(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Column(modifier) {
        Text(label.uppercase(), fontSize = 10.sp, color = Steel, letterSpacing = 0.8.sp)
        Text(value, fontSize = 13.sp, color = LineC)
    }
}

/** Edit a saved record. The UID, visit time, photos and thank-you status are left untouched. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditVisitorDialog(v: Visitor, onDismiss: () -> Unit, onSave: (Visitor) -> Unit) {
    var name by remember { mutableStateOf(v.name) }
    var title by remember { mutableStateOf(v.title) }
    var company by remember { mutableStateOf(v.company) }
    var email by remember { mutableStateOf(v.email) }
    var phone by remember { mutableStateOf(v.phone) }
    var address by remember { mutableStateOf(v.address) }
    var state by remember { mutableStateOf(v.state) }
    var country by remember { mutableStateOf(v.country) }
    var customerType by remember { mutableStateOf(v.customerType) }
    var market by remember { mutableStateOf(v.marketExposure) }
    var other by remember { mutableStateOf(v.otherDetail) }
    var interest by remember { mutableStateOf(v.additionalInterest) }
    var outcomes by remember { mutableStateOf(v.leadOutcomes.split("; ").filter { it.isNotBlank() }.toSet()) }
    var notes by remember { mutableStateOf(v.discussionNotes) }
    var remark by remember { mutableStateOf(v.remarks) }
    var remarkOther by remember { mutableStateOf(v.remarksOther) }
    var followNote by remember { mutableStateOf(v.followUpNote) }
    @Composable fun field(label: String, value: String, multi: Boolean = false, set: (String) -> Unit) =
        OutlinedTextField(value, set, label = { Text(label) }, singleLine = !multi, minLines = if (multi) 2 else 1,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = darkFieldColors(filled = false))
    @Composable fun chips(options: List<String>, selected: (String) -> Boolean, pick: (String) -> Unit) =
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> OptionChip(o, selected(o)) { pick(o) } }
        }
    fun save() {
        val mail = email.trim()
        onSave(v.copy(name = name.trim(), title = title.trim(), company = company.trim(), email = mail, phone = phone.trim(),
            address = address.trim(), state = state.trim(), country = country.trim(),
            customerType = customerType, marketExposure = market, otherDetail = other.trim(),
            additionalInterest = interest.trim(),
            leadOutcomes = LEAD_OUTCOMES.filter { it in outcomes }.joinToString("; "),
            discussionNotes = notes.trim(), remarks = remark,
            remarksOther = if (remark == "Others") remarkOther.trim() else "",
            followUpNote = if (v.followUpAction == "Remarks") followNote.trim() else v.followUpNote,
            // a corrected e-mail on a record that had none becomes mailable
            thankYouStatus = if (v.thankYouStatus == "" && ThankYou.isValidEmail(mail)) ThankYou.PENDING else v.thankYouStatus))
    }
    FormSheet("Edit visitor", onDismiss, onSave = { save() }, icon = Icons.Default.Edit) {
                Text("ID: ${v.uid}", fontSize = 12.sp, color = Steel)
                field("Name", name) { name = it }
                field("Title", title) { title = it }
                field("Company", company) { company = it }
                field("Email", email) { email = it }
                field("Phone", phone) { phone = it }
                field("Address", address, true) { address = it }
                field("State", state) { state = it }
                field("Country", country) { country = it }
                Text("Customer type", fontSize = 12.sp, color = Steel)
                chips(CUSTOMER_TYPES, { it == customerType }) { customerType = if (customerType == it) "" else it }
                Text("Market exposure", fontSize = 12.sp, color = Steel)
                chips(MARKET_EXPOSURE, { it == market }) { market = if (market == it) "" else it }
                field("Other product detail", other, true) { other = it }
                field("Also interested in", interest, true) { interest = it }
                Text("Lead outcome", fontSize = 12.sp, color = Steel)
                chips(LEAD_OUTCOMES, { it in outcomes }) { outcomes = if (it in outcomes) outcomes - it else outcomes + it }
                field("Discussion notes", notes, true) { notes = it }
                Text("Remarks", fontSize = 12.sp, color = Steel)
                chips(REMARK_OPTIONS, { it == remark }) { remark = if (remark == it) "" else it }
                if (remark == "Others") field("Remarks (other)", remarkOther) { remarkOther = it }
                if (v.followUpAction == "Remarks") field("Follow-up note", followNote, true) { followNote = it }
    }
}
