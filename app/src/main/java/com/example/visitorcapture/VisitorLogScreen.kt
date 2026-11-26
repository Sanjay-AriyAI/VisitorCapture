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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * Every visitor saved on this phone, newest first. Export / import / clear sit in the ⋮ menu of the top bar.
 * The date chip narrows the list to a day or range of visit dates; Export starts from that same range.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VisitorLogScreen(
    visitors: List<Visitor>, onBack: () -> Unit,
    onExport: (List<Visitor>, DateRange?) -> Unit, onImport: () -> Unit,
    onEdit: (Visitor) -> Unit, onDelete: (Visitor) -> Unit, onClear: () -> Unit, onRetryThankYou: (Visitor) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LogFilter.All) }
    var range by rememberSaveable(stateSaver = DateRangeSaver) { mutableStateOf<DateRange?>(null) }
    var pickingRange by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var editing by remember { mutableStateOf<Visitor?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var clearText by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }
    val today = remember { startOfToday() }
    val inRange = remember(visitors, range) { range?.let { r -> visitors.filter { it.createdAt in r } } ?: visitors }
    // search + status chip (everything but the dates)
    val matchesView: (Visitor) -> Boolean = remember(query, filter) {
        val q = query.trim().lowercase()
        val match: (Visitor) -> Boolean = { v -> filter.matches(v, today) && (q.isEmpty() ||
            listOf(v.name, v.company, v.email, v.extraEmails, v.phone, v.extraPhones, v.title).any { it.lowercase().contains(q) }) }
        match
    }
    val shown = remember(inRange, matchesView) { inRange.filter(matchesView) }

    Column(Modifier.fillMaxSize()) {
        SectionTopBar(if (range == null) "Visitor logs (${visitors.size})" else "Visitor logs (${inRange.size}/${visitors.size})", onBack) {
            Box {
                IconButton({ menuOpen = true }) { Icon(Icons.Default.MoreVert, "More options", tint = LineC) }
                DropdownMenu(menuOpen, { menuOpen = false }, containerColor = PanelOn) {
                    DropdownMenuItem({ Text("Export (zip)…", color = LineC) }, { menuOpen = false; exporting = true },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateChip(range, onClick = { pickingRange = true }, onClear = { range = null })
                LogFilter.values().forEach { f ->
                    val n = inRange.count { f.matches(it, today) }
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
            Text(when {
                visitors.isEmpty() -> "Visitors you log will appear here."
                range != null && inRange.isEmpty() -> "No visits on ${range!!.label()}. Try other dates."
                else -> "Try another search or filter."
            }, color = Steel, textAlign = TextAlign.Center)
            if (range != null) LinkButton("Show all dates") { range = null }
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
    if (pickingRange) DateRangeDialog(range, onPick = { range = it; pickingRange = false }, onDismiss = { pickingRange = false })
    if (exporting) ExportDialog(visitors, range, matchesView, narrowed = query.isNotBlank() || filter != LogFilter.All,
        onExport = { list, r -> exporting = false; onExport(list, r) }, onDismiss = { exporting = false })
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
                TextButton({ confirmClear = false; clearText = ""; onExport(visitors, null) }) { Text("Export first") }
                TextButton({ confirmClear = false; clearText = "" }) { Text("Cancel") }
            }
        })
}

private val DateRangeSaver = Saver<DateRange?, LongArray>(
    save = { r -> r?.let { longArrayOf(it.from, it.to) } ?: longArrayOf() },
    restore = { a -> if (a.size == 2) DateRange(a[0], a[1]) else null })

/** "All dates" or the chosen range; tapping opens the picker, ✕ goes back to all dates. */
@Composable
private fun DateChip(range: DateRange?, onClick: () -> Unit, onClear: () -> Unit) {
    val on = range != null
    FilterChip(on, onClick,
        label = { Text(range?.label() ?: "All dates", fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium) },
        shape = RoundedCornerShape(50),
        leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)) },
        trailingIcon = if (on) ({
            Icon(Icons.Default.Close, "Show all dates", Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onClear))
        }) else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel, labelColor = LineC, iconColor = LineDim,
            selectedContainerColor = Amber, selectedLabelColor = OnAmber,
            selectedLeadingIconColor = OnAmber, selectedTrailingIconColor = OnAmber),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on,
            borderColor = if (IsDark) LineDim.copy(alpha = 0.35f) else Outline, selectedBorderColor = Amber))
}

/**
 * Calendar for visit dates: tap the From day, then the To day (one tap = that single day).
 * Quick chips above the calendar fill in Today / Yesterday / Last 7 days / This month; "All dates" clears the filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(initial: DateRange?, onPick: (DateRange?) -> Unit, onDismiss: () -> Unit) {
    val today = remember { startOfToday() }
    val todayUtc = remember { utcDay(System.currentTimeMillis()) }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.let { utcDay(it.from) },
        initialSelectedEndDateMillis = initial?.let { utcDay(it.lastDay) },
        initialDisplayedMonthMillis = initial?.let { utcDay(it.lastDay) } ?: todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
        })
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    val picked = start?.let { DateRange.fromPicker(it, end) }
    val dayFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPick(picked) }, enabled = picked != null) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton({ onPick(null) }) { Text("All dates") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        }) {
        DateRangePicker(state, Modifier.weight(1f), showModeToggle = false,
            title = {
                Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Visit dates", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DatePreset.values().forEach { p ->
                            val r = p.range(today)
                            OptionChip(p.label, picked == r) {
                                state.setSelection(utcDay(r.from), utcDay(r.lastDay))
                                state.displayedMonthMillis = utcDay(r.lastDay)
                            }
                        }
                    }
                }
            },
            headline = {
                Column(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateBox("From", start?.let { dayFmt.format(Date(it)) }, active = start == null, Modifier.weight(1f))
                        DateBox("To", end?.let { dayFmt.format(Date(it)) } ?: start?.let { "Same day" },
                            active = start != null && end == null, Modifier.weight(1f))
                    }
                    Text(when {
                        start == null -> "Tap the first day"
                        end == null -> "Tap the last day, or Apply for just this day"
                        else -> picked!!.let { r -> ((r.to - r.from + 3_600_000L) / 86_400_000L).let { n ->
                            if (n == 1L) "1 day selected" else "$n days selected" } }
                    }, fontSize = 12.sp, color = Steel, modifier = Modifier.padding(top = 6.dp))
                }
            })
    }
}

/** One end of the range in the calendar header; [active] marks the end the next tap will set. */
@Composable
private fun DateBox(label: String, value: String?, active: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (active) Amber.copy(alpha = 0.16f) else LineC.copy(alpha = 0.06f))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 0.8.sp, color = if (active) AmberText else Steel)
        Text(value ?: "Select", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = if (value == null) Steel else LineC, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Export confirmation: which visit dates go into the zip, how many visitors that is. */
@Composable
private fun ExportDialog(visitors: List<Visitor>, initial: DateRange?, matchesView: (Visitor) -> Boolean, narrowed: Boolean,
                         onExport: (List<Visitor>, DateRange?) -> Unit, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf(initial) }
    var onlyShown by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val list = remember(visitors, range, onlyShown) {
        visitors.filter { v -> (range == null || v.createdAt in range!!) && (!onlyShown || matchesView(v)) }
    }
    if (picking) { DateRangeDialog(range, onPick = { range = it; picking = false }, onDismiss = { picking = false }); return }
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Upload, null) },
        title = { Text("Export visitors") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picking = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, null, tint = Steel)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("VISIT DATES", fontSize = 10.sp, color = Steel, letterSpacing = 0.8.sp)
                        Text(range?.label() ?: "All dates", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Change", color = AmberText, fontSize = 13.sp)
                }
                if (narrowed) Row(Modifier.fillMaxWidth().clickable { onlyShown = !onlyShown },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(onlyShown, { onlyShown = it })
                    Text("Only visitors matching the current search and status filter", fontSize = 13.sp)
                }
                Text(if (list.isEmpty()) "No visitors in this range." else
                    "${list.size} visitor${if (list.size == 1) "" else "s"} with card photos and notes will be zipped.",
                    color = if (list.isEmpty()) Danger else Steel, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ onExport(list, range) }, enabled = list.isNotEmpty()) { Text("Export ${list.size}") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
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
                    .clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
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
                ThankYou.QUEUED -> Pill(if (ThankYou.isUpdate(v)) "Update queued" else "Mail queued", Amber)
                ThankYou.PENDING -> Pill(if (ThankYou.isUpdate(v)) "Update not sent" else "Mail not sent", Amber)
                ThankYou.FAILED -> Pill("Mail failed", Danger, Color_F0C3B8)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = LineC.copy(alpha = 0.08f))
                Detail("Contact", (listOf(v.email) + v.extraEmailList() + v.phone + v.extraPhoneList()).filter { it.isNotBlank() }.joinToString(" · "))
                Detail("Website", v.website)
                Detail("Address", listOf(v.address, v.state, v.country).filter { it.isNotBlank() }.joinToString(", "))
                Detail("Products", Catalog.summarize(v.products.split("; ").filter { it.isNotBlank() }))
                Detail(if (Brand.describeAnyProduct) "Product description" else "Other products", v.otherDetail)
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
                if (v.factoryVisitAt > 0) Detail("Factory visit", fmt.format(Date(v.factoryVisitAt)) + reminderText(v.factoryReminderStatus))
                if (v.followUpAction.isNotBlank() || v.followUpNote.isNotBlank())   // older records
                    Detail("Follow-up", listOf(v.followUpAction, if (v.followUpAt > 0) fmt.format(Date(v.followUpAt)) else "", v.followUpNote)
                        .filter { it.isNotBlank() }.joinToString(" · "))
                else if (v.followUpAt > 0) Detail("Follow-up", fmt.format(Date(v.followUpAt)) + reminderText(v.followUpReminderStatus))
                Detail(if (Brand.outcomeDescriptionMin > 0) "Description" else "Discussion notes", v.discussionNotes)
                when (v.thankYouStatus) {
                    ThankYou.SENT -> Detail("Thank-you mail", "Sent " + fmt.format(Date(v.thankYouSentAt)))
                    ThankYou.QUEUED -> Detail("Thank-you mail", if (ThankYou.isUpdate(v))
                        "First sent ${fmt.format(Date(v.thankYouSentAt))} · updated version queued, sends when online"
                        else "Queued, sends when online")
                    ThankYou.PENDING -> Detail("Thank-you mail", if (ThankYou.isUpdate(v))
                        "First sent ${fmt.format(Date(v.thankYouSentAt))} · updated version not sent yet" else "Not sent yet")
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

/** Reminder mail status after the scheduled time, e.g. " · reminder sent". */
private fun reminderText(status: String) = when (status) {
    Reminder.SCHEDULED -> " · reminder mail scheduled (24 h before)"
    Reminder.SENT -> " · reminder mail sent"
    Reminder.FAILED -> " · reminder mail failed"
    Reminder.EXPIRED -> " · reminder not sent (time passed)"
    else -> ""
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
    var extraEmails by remember { mutableStateOf(v.extraEmailList()) }
    var website by remember { mutableStateOf(v.website) }
    var phone by remember { mutableStateOf(v.phone) }
    var extraPhones by remember { mutableStateOf(v.extraPhoneList()) }
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
    var factoryAt by remember { mutableStateOf(v.factoryVisitAt) }
    var followAt by remember { mutableStateOf(v.followUpAt) }
    var scheduling by remember { mutableStateOf<Reminder?>(null) }
    @Composable fun field(label: String, value: String, multi: Boolean = false, set: (String) -> Unit) =
        OutlinedTextField(value, set, label = { Text(label) }, singleLine = !multi, minLines = if (multi) 2 else 1,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = darkFieldColors(filled = false))
    @Composable fun chips(options: List<String>, selected: (String) -> Boolean, pick: (String) -> Unit) =
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> OptionChip(o, selected(o)) { pick(o) } }
        }
    fun save() {
        val (mail, extras) = splitEmails(email, extraEmails)
        val (mainPhone, morePhones) = splitPhones(phone, extraPhones)
        val newFactory = if (Reminder.FACTORY.outcome in outcomes) factoryAt else 0L
        val newFollow = if (Brand.followUpOnDone || Reminder.FOLLOW_UP.outcome in outcomes) followAt else 0L
        val edited = v.copy(name = name.trim(), title = title.trim(), company = company.trim(), email = mail, extraEmails = extras, website = website.trim(), phone = mainPhone, extraPhones = morePhones,
            address = address.trim(), state = state.trim(), country = country.trim(),
            customerType = customerType, marketExposure = market, otherDetail = other.trim(),
            additionalInterest = interest.trim(),
            leadOutcomes = LEAD_OUTCOMES.filter { it in outcomes }.joinToString("; "),
            discussionNotes = notes.trim(), remarks = remark,
            remarksOther = if (remark == "Others") remarkOther.trim() else "",
            followUpNote = if (v.followUpAction == "Remarks") followNote.trim() else v.followUpNote,
            // a corrected e-mail on a record that had none becomes mailable
            thankYouStatus = if (v.thankYouStatus == "" && ThankYou.isValidEmail(mail)) ThankYou.PENDING else v.thankYouStatus,
            factoryVisitAt = newFactory, followUpAt = newFollow)
        // A new time re-schedules its reminder; an unchanged one keeps its status, unless a fixed e-mail makes an unsent one mailable.
        fun status(old: Long, new: Long, s: String) =
            if (old != new || (s != Reminder.SENT && s != Reminder.SCHEDULED && mail != v.email.trim())) Reminder.initialStatus(new, edited) else s
        onSave(edited.copy(factoryReminderStatus = status(v.factoryVisitAt, newFactory, v.factoryReminderStatus),
            followUpReminderStatus = status(v.followUpAt, newFollow, v.followUpReminderStatus)))
    }
    scheduling?.let { kind ->
        if (Brand.followUpOnDone && kind == Reminder.FOLLOW_UP)   // follow-up is its own field here, not a lead outcome
            ScheduleDialog(kind, followAt, onSave = { at -> followAt = at; scheduling = null },
                onRemove = if (followAt > 0) ({ followAt = 0L; scheduling = null }) else null,
                onDismiss = { scheduling = null })
        else {
        val ticked = kind.outcome in outcomes
        ScheduleDialog(kind, if (!ticked) 0L else if (kind == Reminder.FACTORY) factoryAt else followAt,
            onSave = { at ->
                if (kind == Reminder.FACTORY) factoryAt = at else followAt = at
                outcomes = outcomes + kind.outcome; scheduling = null
            },
            onRemove = if (ticked) ({ outcomes = outcomes - kind.outcome; scheduling = null }) else null,
            onDismiss = { scheduling = null })
        }
    }
    FormSheet("Edit visitor", onDismiss, onSave = { save() }, icon = Icons.Default.Edit) {
                Text("ID: ${v.uid}", fontSize = 12.sp, color = Steel)
                field("Name", name) { name = it }
                field("Title", title) { title = it }
                field("Company", company) { company = it }
                field("Email", email) { email = it }
                extraEmails.forEachIndexed { i, mail ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(mail, { v -> extraEmails = extraEmails.toMutableList().also { it[i] = v } },
                            label = { Text(EXTRA_EMAIL_LABELS[i]) }, singleLine = true, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp), colors = darkFieldColors(filled = false))
                        IconButton({ extraEmails = extraEmails.filterIndexed { j, _ -> j != i } }) {
                            Icon(Icons.Default.Close, "Remove ${EXTRA_EMAIL_LABELS[i].lowercase()}", tint = Steel)
                        }
                    }
                }
                if (extraEmails.size < MAX_EMAILS - 1)
                    LinkButton(if (extraEmails.isEmpty()) "+ Add secondary e-mail" else "+ Add another e-mail") { extraEmails = extraEmails + "" }
                field("Website", website) { website = it }
                field("Phone", phone) { phone = it }
                extraPhones.forEachIndexed { i, num ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(num, { v -> extraPhones = extraPhones.toMutableList().also { it[i] = v } },
                            label = { Text(EXTRA_PHONE_LABELS[i]) }, singleLine = true, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp), colors = darkFieldColors(filled = false))
                        IconButton({ extraPhones = extraPhones.filterIndexed { j, _ -> j != i } }) {
                            Icon(Icons.Default.Close, "Remove ${EXTRA_PHONE_LABELS[i].lowercase()}", tint = Steel)
                        }
                    }
                }
                if (extraPhones.size < MAX_PHONES - 1)
                    LinkButton(if (extraPhones.isEmpty()) "+ Add secondary phone" else "+ Add another phone") { extraPhones = extraPhones + "" }
                field("Address", address, true) { address = it }
                field("State", state) { state = it }
                field("Country", country) { country = it }
                Text("Customer type", fontSize = 12.sp, color = Steel)
                chips(CUSTOMER_TYPES, { it == customerType }) { customerType = if (customerType == it) "" else it }
                Text("Market exposure", fontSize = 12.sp, color = Steel)
                chips(MARKET_EXPOSURE, { it == market }) { market = if (market == it) "" else it }
                field(if (Brand.describeAnyProduct) "Product description" else "Other product detail", other, true) { other = it }
                field("Also interested in", interest, true) { interest = it }
                Text("Lead outcome", fontSize = 12.sp, color = Steel)
                chips(LEAD_OUTCOMES, { it in outcomes }) { o ->
                    val kind = Reminder.values().find { it.outcome == o }
                    if (kind != null) scheduling = kind   // Factory Visit / Follow-up: pick the date and time first
                    else outcomes = if (o in outcomes) outcomes - o else outcomes + o
                }
                if (Reminder.FACTORY.outcome in outcomes) scheduleBadge(factoryAt)?.let { Text("Factory visit: $it", fontSize = 12.sp, color = Steel) }
                if (Brand.followUpOnDone)
                    LinkButton(scheduleBadge(followAt)?.let { "Follow-up: $it (change)" } ?: "+ Schedule follow-up") { scheduling = Reminder.FOLLOW_UP }
                else if (Reminder.FOLLOW_UP.outcome in outcomes) scheduleBadge(followAt)?.let { Text("Follow-up: $it", fontSize = 12.sp, color = Steel) }
                field(if (Brand.outcomeDescriptionMin > 0) "Description" else "Discussion notes", notes, true) { notes = it }
                Text("Remarks", fontSize = 12.sp, color = Steel)
                chips(REMARK_OPTIONS, { it == remark }) { remark = if (remark == it) "" else it }
                if (remark == "Others") field("Remarks (other)", remarkOther) { remarkOther = it }
                if (v.followUpAction == "Remarks") field("Follow-up note", followNote, true) { followNote = it }
    }
}
