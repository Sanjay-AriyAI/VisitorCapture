package com.example.visitorcapture

import android.content.Context
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

/** Short date for the tile badge, e.g. "Fri 21 Nov, 10:00". */
fun scheduleBadge(at: Long): String? =
    if (at > 0) SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(at)) else null

/** Staff step after Products: what came out of the conversation. Factory Visit / Follow-up ask for a date and time. */
@Composable
fun LeadOutcomeScreen(form: Form, onChange: (Form) -> Unit, staff: EmployeeInfo?, onBack: () -> Unit, onFinish: () -> Unit) = Scrolling {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Reminder?>(null) }   // popup open for this tile
    var askExact by remember { mutableStateOf(false) }

    fun timeOf(k: Reminder) = when (k) { Reminder.FACTORY -> form.factoryVisitAt; Reminder.FOLLOW_UP -> form.followUpAt }
    fun withTime(k: Reminder, at: Long, on: Boolean): Form {
        val outcomes = if (on) form.outcomes + k.outcome else form.outcomes - k.outcome
        return when (k) {
            Reminder.FACTORY -> form.copy(outcomes = outcomes, factoryVisitAt = at)
            Reminder.FOLLOW_UP -> form.copy(outcomes = outcomes, followUpAt = at)
        }
    }

    Text("Purpose of Visit", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
    Text("PURPOSE OF VISIT", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
    Text("Tap all that apply.", color = Steel)
    TileGrid(LEAD_OUTCOMES.map { o ->
        val kind = Reminder.values().find { it.outcome == o }
        TileItem(o, when (o) {
            "Product Enquiry" -> Icons.Default.QuestionAnswer
            "Sample Request" -> Icons.Default.Science
            "General Enquiry" -> Icons.Default.Info
            "Order" -> Icons.Default.ShoppingCart
            "Factory Visit" -> Icons.Default.Factory
            else -> Icons.Default.EventRepeat
        }, o in form.outcomes, badge = if (kind != null && o in form.outcomes) scheduleBadge(timeOf(kind)) else null)
    }) { k ->
        val kind = Reminder.values().find { it.outcome == k }
        if (kind != null) editing = kind   // pick the date first; the tile is ticked on Save
        else onChange(form.copy(outcomes = if (k in form.outcomes) form.outcomes - k else form.outcomes + k))
    }

    editing?.let { kind ->
        val ticked = kind.outcome in form.outcomes
        ScheduleDialog(kind, if (ticked) timeOf(kind) else 0L,
            onSave = { at ->
                onChange(withTime(kind, at, true)); editing = null
                if (!Reminders.canExact(ctx) && !exactAsked(ctx)) askExact = true
            },
            onRemove = if (ticked) ({ onChange(withTime(kind, 0L, false)); editing = null }) else null,
            onDismiss = { editing = null })
    }
    if (askExact) ExactAlarmDialog { askExact = false }

    Spacer(Modifier.height(6.dp))
    Text("DISCUSSION NOTES", fontSize = 12.sp, color = Steel, letterSpacing = 1.sp)
    OutlinedTextField(form.notes, { onChange(form.copy(notes = it.take(2000))) },
        placeholder = { Text("What was discussed: parts, quantities, prices, next steps") },
        minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors())

    NavRow(
        { SecondaryButton("Back") { onBack() } },
        { PrimaryButton("Finish", enabled = form.outcomes.isNotEmpty()) { onFinish() } })
}

/**
 * Factory Visit / Follow-up: opens the calendar straight away, then the clock; Save on the clock ticks the tile.
 * A reminder mail goes out 24 hours before. [onRemove] is offered when the tile is already ticked; it unticks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(kind: Reminder, initial: Long, onSave: (Long) -> Unit, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val what = if (kind == Reminder.FACTORY) "factory visit" else "follow-up"
    var day by remember { mutableStateOf<Long?>(null) }   // chosen day (UTC midnight), waiting for a time

    val today = remember { utcDay(System.currentTimeMillis()) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = if (initial > 0) utcDay(initial) else today,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
        })

    if (day == null) DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ day = dateState.selectedDateMillis }, enabled = dateState.selectedDateMillis != null) { Text("Next") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onRemove) { Text("Remove") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        }) {
        DatePicker(dateState, title = {
            Text("Date of $what", Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
        })
    }

    day?.let { d ->
        val prev = remember { Calendar.getInstance().apply { timeInMillis = initial } }
        val timeState = rememberTimePickerState(if (initial > 0) prev.get(Calendar.HOUR_OF_DAY) else 10,
            if (initial > 0) prev.get(Calendar.MINUTE) else 0, android.text.format.DateFormat.is24HourFormat(ctx))
        val at = localAt(d, timeState.hour, timeState.minute)
        val future = at > System.currentTimeMillis()
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text("Time of $what") },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    TimePicker(timeState)
                    Text(if (future) "A reminder e-mail goes to the visitor (you in CC) 24 hours before."
                        else "That time has already passed. Pick a later one.",
                        color = if (future) Steel else Danger, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton({ onSave(at) }, enabled = future) { Text("Save") } },
            dismissButton = { TextButton({ day = null }) { Text("Back") } })
    }
}

private const val EXACT_ASKED = "reminder.exactAsked"
private fun exactAsked(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(EXACT_ASKED, false)

/** Asked once: without "Alarms & reminders" the mail still goes out, but Android may send it a few minutes late. */
@Composable
private fun ExactAlarmDialog(onDone: () -> Unit) {
    val ctx = LocalContext.current
    fun done() { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(EXACT_ASKED, true).apply(); onDone() }
    AlertDialog(onDismissRequest = { done() },
        icon = { Icon(Icons.Default.Alarm, null) },
        title = { Text("Send reminders on time?") },
        text = { Text("Allow \"Alarms & reminders\" for this app so reminder e-mails go out at exactly the right time, " +
            "even when the app is closed. Without it they are still sent, but may be a few minutes late.") },
        confirmButton = {
            TextButton({
                Reminders.exactAlarmSettings(ctx)?.let { runCatching { ctx.startActivity(it) } }
                done()
            }) { Text("Allow") }
        },
        dismissButton = { TextButton({ done() }) { Text("Not now") } })
}
// utcDay() / localAt() (date picker UTC midnights ↔ the phone's calendar day) live in DateRange.kt.
