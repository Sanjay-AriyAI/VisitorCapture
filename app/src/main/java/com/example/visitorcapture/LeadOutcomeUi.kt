package com.example.visitorcapture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/** Staff step after Products: what came out of the conversation, remarks and the follow-up to do. */
@Composable
fun LeadOutcomeScreen(form: Form, onChange: (Form) -> Unit, staff: EmployeeInfo?, onBack: () -> Unit, onFinish: () -> Unit) = Scrolling {
    val ctx = LocalContext.current
    Text("Lead outcome", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
    Text("LEAD OUTCOME", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
    Text("Tap all that apply.", color = Steel)
    TileGrid(LEAD_OUTCOMES.map { o ->
        TileItem(o, when (o) {
            "Product Enquiry" -> Icons.Default.QuestionAnswer
            "Sample Request" -> Icons.Default.Science
            "Spare Parts Requirement" -> Icons.Default.Build
            "Order" -> Icons.Default.ShoppingCart
            "Factory Visit" -> Icons.Default.Factory
            else -> Icons.Default.EventRepeat
        }, o in form.outcomes)
    }) { k ->
        val outcomes = if (k in form.outcomes) form.outcomes - k else form.outcomes + k
        // A factory visit almost always means scheduling one, so start the follow-up there.
        val preset = k == "Factory Visit" && k in outcomes && form.followUpAction == null
        onChange(form.copy(outcomes = outcomes, followUpAction = if (preset) "Schedule Factory Visit" else form.followUpAction))
    }

    Spacer(Modifier.height(6.dp))
    Text("DISCUSSION NOTES", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
    OutlinedTextField(form.notes, { onChange(form.copy(notes = it.take(2000))) },
        placeholder = { Text("What was discussed: parts, quantities, prices, next steps") },
        minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())

    Spacer(Modifier.height(6.dp))
    Text("REMARKS", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
    DropdownField("Choose a remark", REMARK_OPTIONS, form.remark) { onChange(form.copy(remark = it)) }
    if (form.remark == "Others")
        OutlinedTextField(form.remarkOther, { onChange(form.copy(remarkOther = it.take(500))) },
            placeholder = { Text("Describe the remark") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())

    if (form.needsFollowUp) {
        Spacer(Modifier.height(6.dp))
        Text("FOLLOW-UP", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
        DropdownField("Choose a follow-up", FOLLOW_UP_ACTIONS, form.followUpAction) { onChange(form.copy(followUpAction = it)) }
        when (form.followUpAction) {
            null -> {}
            "Remarks" -> OutlinedTextField(form.followUpNote, { onChange(form.copy(followUpNote = it.take(500))) },
                placeholder = { Text("Follow-up remarks") }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
            else -> {
                FollowUpDateTime(form.followUpAt) { onChange(form.copy(followUpAt = it)) }
                if (form.followUpAction == "Send Quotation") {
                    SecondaryButton("Draft quotation email") { draftQuotation(ctx, form, staff) }
                    if (form.email.isBlank())
                        Text("No email for this visitor yet. Add it on Details, or type it in the email app.", color = Steel, fontSize = 13.sp)
                } else if (form.followUpAt > 0) SecondaryButton("Add to calendar") { addToCalendar(ctx, form, staff) }
                else Text("Pick a date and time, then add it to the calendar.", color = Steel, fontSize = 13.sp)
            }
        }
    }

    NavRow(
        { SecondaryButton("Back") { onBack() } },
        { PrimaryButton("Finish", enabled = form.outcomes.isNotEmpty()) { onFinish() } })
}

/** Read-only field that opens a date picker, then a time picker. [at] is epoch ms, 0 if not set yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowUpDateTime(at: Long, onPick: (Long) -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickedDay by remember { mutableStateOf<Long?>(null) }   // chosen day, waiting for a time

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(if (at > 0) fmt.format(Date(at)) else "", {}, readOnly = true, singleLine = true,
            placeholder = { Text("Pick date & time") }, trailingIcon = { Icon(Icons.Default.Event, null, tint = Steel) },
            modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())
        // A read-only text field still takes the tap for focus, so catch it on top.
        Box(Modifier.matchParentSize().clickable { pickDate = true })
    }

    if (pickDate) {
        val today = remember { utcDay(System.currentTimeMillis()) }
        val state = rememberDatePickerState(initialSelectedDateMillis = if (at > 0) utcDay(at) else today,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            })
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({ pickedDay = state.selectedDateMillis; pickDate = false },
                    enabled = state.selectedDateMillis != null) { Text("Next") }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } }) { DatePicker(state) }
    }

    pickedDay?.let { day ->
        val prev = Calendar.getInstance().apply { timeInMillis = at }
        val state = rememberTimePickerState(if (at > 0) prev.get(Calendar.HOUR_OF_DAY) else 10,
            if (at > 0) prev.get(Calendar.MINUTE) else 0, android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(onDismissRequest = { pickedDay = null },
            confirmButton = { TextButton({ onPick(localAt(day, state.hour, state.minute)); pickedDay = null }) { Text("OK") } },
            dismissButton = { TextButton({ pickedDay = null }) { Text("Cancel") } },
            text = { TimePicker(state) })
    }
}

/** Products the visitor picked, one "- " line each, for the email and calendar text. */
private fun interestLines(form: Form): String {
    val lines = Catalog.summarize(form.products).lines().filter { it.isNotBlank() } +
        (if ("Other Products" in form.products && form.otherDetail.isNotBlank()) listOf("Other: ${form.otherDetail.trim()}") else emptyList())
    return lines.distinct().joinToString("\n") { "- $it" }
}

/** Opens the email app with a quotation email to the visitor; staff attach the quotation and press Send. */
private fun draftQuotation(ctx: Context, form: Form, staff: EmployeeInfo?) {
    val first = form.name.split(" ").firstOrNull().orEmpty()
    val interest = interestLines(form)
    val body = buildString {
        append("Dear ${first.ifBlank { "Sir / Madam" }},\n\n")
        append("Thank you for visiting our stand. As discussed, please find attached our quotation")
        append(if (interest.isNotBlank()) " for:\n\n$interest\n\n" else ".\n\n")
        append("Please let us know if you have any questions.\n\nRegards,\n")
        append(listOf(staff?.name, staff?.title, staff?.company, staff?.phone).filterNot { it.isNullOrBlank() }.joinToString("\n"))
    }
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, listOf(form.email.trim()).filter { it.isNotBlank() }.toTypedArray())
        .putExtra(Intent.EXTRA_SUBJECT, if (form.company.isNotBlank()) "Quotation for ${form.company}" else "Quotation")
        .putExtra(Intent.EXTRA_TEXT, body)
    launch(ctx, i, "No email app found on this phone.")
}

/**
 * Opens the calendar app with the follow-up filled in; staff tap Save. The calendar app then sends the reminders.
 * A factory visit invites the visitor as a guest; a reminder is for the staff member only.
 */
private fun addToCalendar(ctx: Context, form: Form, staff: EmployeeInfo?) {
    val visit = form.followUpAction == "Schedule Factory Visit"
    val who = listOf(form.name, form.company).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "visitor" }
    val interest = interestLines(form)
    val description = buildString {
        if (form.phone.isNotBlank()) append("Phone: ${form.phone}\n")
        if (form.email.isNotBlank()) append("Email: ${form.email}\n")
        if (form.outcomes.isNotEmpty()) append("Outcome: " + LEAD_OUTCOMES.filter { it in form.outcomes }.joinToString(", ") + "\n")
        if (interest.isNotBlank()) append("Interested in:\n$interest\n")
        if (form.notes.isNotBlank()) append("Notes: ${form.notes.trim()}\n")
        staff?.let { append("Attended by ${it.name}") }
    }.trim()
    val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, (if (visit) "Factory visit: " else "Follow up: ") + who)
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, form.followUpAt)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, form.followUpAt + 60 * 60 * 1000L)
    if (visit && form.email.isNotBlank()) i.putExtra(Intent.EXTRA_EMAIL, form.email.trim())   // guests, comma-separated
    launch(ctx, i, "No calendar app found on this phone.")
}

private fun launch(ctx: Context, i: Intent, missing: String) =
    try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) { Toast.makeText(ctx, missing, Toast.LENGTH_LONG).show() }

// The date picker works in UTC midnights; these convert to and from the phone's own calendar day,
// so a date picked late in the evening never lands on the neighbouring day.
private fun utcDay(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun localAt(utcDay: Long, hour: Int, minute: Int): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), hour, minute)
    }.timeInMillis
}
