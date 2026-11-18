package com.example.visitorcapture

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.SendFailedException
import javax.mail.internet.AddressException

/**
 * Schedules the 24-hour reminder mails so they go out even when the app is closed or the phone was restarted.
 * The database is the source of truth; two independent triggers wake [ReminderWorker] at the reminder time:
 *  - a delayed WorkManager job (survives app close and reboot, but Android may run it a few minutes late), and
 *  - an exact AlarmManager alarm (on time even in Doze), when the "Alarms & reminders" permission is granted.
 * Whichever fires first sends the mail; the other finds it already SENT and does nothing.
 */
object Reminders {
    private const val TAG = "Reminder"
    private const val SEND_NOW = "reminder-send"

    private val network get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Arms (or disarms) both reminders of [v] from its current statuses. Safe to call again after an edit. */
    fun schedule(ctx: Context, v: Visitor) {
        val now = System.currentTimeMillis()
        for (kind in Reminder.values()) {
            if (kind.status(v) != Reminder.SCHEDULED) { cancel(ctx, v.uid, kind); continue }
            val trigger = Reminder.remindAt(kind.at(v))
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((trigger - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(v.uid, kind), ExistingWorkPolicy.REPLACE, req)
            if (trigger > now) setAlarm(ctx, v.uid, kind, trigger)
        }
    }

    fun cancel(ctx: Context, uid: String, kind: Reminder) {
        WorkManager.getInstance(ctx).cancelUniqueWork(workName(uid, kind))
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(ctx, uid, kind))
    }

    /** After a reboot, app update or clock change: alarms are wiped by Android, so re-arm everything still pending. */
    suspend fun rescheduleAll(ctx: Context) {
        AppDb.get(ctx).dao().scheduledReminders().forEach { schedule(ctx, it) }
        runNow(ctx)   // anything that fell due while the phone was off
    }

    /** Sends whatever is due as soon as the phone is online (expedited, so it is not held back by Doze). */
    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(SEND_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** Android 12+ needs the user's "Alarms & reminders" permission for exact alarms; older versions always allow them. */
    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    /** System screen where the user grants "Alarms & reminders" for this app (Android 12+). */
    fun exactAlarmSettings(ctx: Context): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")) else null

    private fun setAlarm(ctx: Context, uid: String, kind: Reminder, at: Long) {
        if (!canExact(ctx)) return   // the WorkManager job still sends it, just possibly a little late
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(ctx, uid, kind))
            else am.setExact(AlarmManager.RTC_WAKEUP, at, alarmIntent(ctx, uid, kind))
        } catch (e: SecurityException) { Log.w(TAG, "Exact alarm not allowed", e) }
    }

    private fun workName(uid: String, kind: Reminder) = "reminder-$uid-${kind.key}"

    // A distinct data URI per reminder keeps the PendingIntents apart (extras alone don't).
    private fun alarmIntent(ctx: Context, uid: String, kind: Reminder) = PendingIntent.getBroadcast(ctx, 0,
        Intent(ctx, ReminderAlarmReceiver::class.java).setData(Uri.parse("reminder://$uid/${kind.key}")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Sends every reminder mail that is due. Runs in the background, also when the app is closed. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = lock.withLock { sendDue() }   // two triggers never mail the same person twice

    private suspend fun sendDue(): Result {
        val cfg = SmtpSettings.load(applicationContext)
        if (!cfg.complete) { Log.w(TAG, "Mail settings not filled in; reminders stay scheduled"); return Result.success() }
        val dao = AppDb.get(applicationContext).dao()
        val now = System.currentTimeMillis()
        var retry = false
        var logo: ByteArray? = null
        for (v in dao.scheduledReminders()) for (kind in Reminder.values()) {
            val at = kind.at(v)
            if (kind.status(v) != Reminder.SCHEDULED || !Reminder.isDue(at, now)) continue
            if (at <= now) { kind.setStatus(dao, v.uid, Reminder.EXPIRED); continue }   // too late to remind anyone
            try {
                if (logo == null) logo = ThankYou.loadLogo(applicationContext)
                ReminderMail.send(cfg, v, kind, logo)
                kind.setStatus(dao, v.uid, Reminder.SENT)
            } catch (e: AddressException) {
                kind.setStatus(dao, v.uid, Reminder.FAILED)
            } catch (e: SendFailedException) {
                Log.w(TAG, "Refused for ${v.uid}: ${e.message}"); kind.setStatus(dao, v.uid, Reminder.FAILED)
            } catch (e: AuthenticationFailedException) {
                Log.e(TAG, "SMTP login failed - check mail settings", e); retry = true
            } catch (e: MessagingException) {
                Log.w(TAG, "Temporary send error for ${v.uid}: ${e.message}"); retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    /** Needed for expedited work on Android 11 and older, where it runs as a short foreground job. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminder mails", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Sending reminder mail")
            .setOngoing(true).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "Reminder"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 4201
        private val lock = Mutex()
    }
}

/** Exact alarm at the reminder time: hand over to the worker straight away. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminders.runNow(ctx)
}

/** Reboot, app update, clock / time-zone change or the exact-alarm permission granted: re-arm pending reminders. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Reminders.rescheduleAll(ctx.applicationContext) } finally { pending.finish() }
        }
    }
}
