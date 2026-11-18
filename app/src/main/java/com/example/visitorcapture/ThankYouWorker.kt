package com.example.visitorcapture

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.SendFailedException
import javax.mail.internet.AddressException

/** Sends the thank-you mail to every PENDING visitor; runs as soon as the phone is online and retries on temporary errors. */
class ThankYouWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cfg = SmtpSettings.load(applicationContext)
        if (!cfg.complete) { Log.w(TAG, "Mail settings not filled in; thank-you mails stay PENDING"); return Result.success() }
        val dao = AppDb.get(applicationContext).dao()
        var retry = false
        val queued = dao.queuedThankYou()
        val logo = if (queued.isNotEmpty()) ThankYou.loadLogo(applicationContext) else null   // read once per run
        for (v in queued) {
            if (!ThankYou.isValidEmail(v.email)) { dao.setThankYouStatus(v.uid, ThankYou.FAILED); continue }
            try {
                ThankYou.send(cfg, v, logo, updated = ThankYou.isUpdate(v))   // mailed before: "updated, final version" mail
                dao.markThankYou(v.uid, ThankYou.SENT, System.currentTimeMillis())
            } catch (e: AddressException) {
                dao.setThankYouStatus(v.uid, ThankYou.FAILED)
            } catch (e: SendFailedException) {
                // the server refused this recipient (e.g. mailbox does not exist): retrying will not help
                Log.w(TAG, "Refused for ${v.uid}: ${e.message}")
                dao.setThankYouStatus(v.uid, ThankYou.FAILED)
            } catch (e: AuthenticationFailedException) {
                Log.e(TAG, "SMTP login failed - check smtp.user / smtp.password", e); retry = true
            } catch (e: MessagingException) {
                Log.w(TAG, "Temporary send error for ${v.uid}: ${e.message}"); retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "ThankYou"
        private const val UNIQUE = "thank-you-mail"

        /** Queue a send run; safe to call often (runs are merged and never overlap, so nobody is mailed twice). */
        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<ThankYouWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
