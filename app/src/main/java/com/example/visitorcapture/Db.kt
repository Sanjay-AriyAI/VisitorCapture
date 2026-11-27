package com.example.visitorcapture

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "visitors", indices = [Index(value = ["uid"], unique = true)])
data class Visitor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,            // globally unique, e.g. SIV-13-0922-0001 (see UidGen); used to merge devices
    val deviceName: String,
    val name: String,
    val title: String,
    val company: String,
    val email: String,
    val phone: String,
    val address: String = "",
    val state: String = "",
    val country: String = "",   // optional, "if applicable"
    val customerType: String,   // OEM | End User | Agent | Distributor
    val marketExposure: String = "",   // Domestic Visitor | International Visitor
    val products: String,       // selected catalogue paths joined with "; "
    val otherDetail: String,
    val additionalInterest: String = "",   // free text typed on the Done screen: other products / models of interest
    val notePhotoPaths: String = "",       // photos / uploads of handwritten enquiries from the Done screen, absolute paths joined with "|"
    val cardPhotoPath: String?,
    val createdAt: Long,        // date and time of the visit (epoch ms), stamped when the visit starts
    // the staff member who attended this visitor, copied from the QR login at the time of the visit
    val attendedByKey: String = "",
    val attendedByName: String = "",
    val attendedByTitle: String = "",
    val attendedByEmail: String = "",
    val attendedByPhone: String = "",
    // lead outcome & follow-up, recorded by staff on the Outcome step
    val leadOutcomes: String = "",      // selected outcomes joined with "; "
    val discussionNotes: String = "",
    val remarks: String = "",           // Call Back | Factory Visit | Need Follow-up | Others
    val remarksOther: String = "",      // free text when remarks is "Others"
    val followUpAction: String = "",    // Send Quotation | Schedule Factory Visit | Automatic E-mail Reminder | Remarks
    val followUpAt: Long = 0,           // follow-up date and time (epoch ms), 0 if none
    val followUpNote: String = "",      // free text when followUpAction is "Remarks"
    val thankYouStatus: String = "",    // "" (no valid e-mail / imported) | PENDING | SENT | FAILED
    val thankYouSentAt: Long = 0,       // epoch ms the last thank-you mail was accepted by the SMTP server, 0 if never;
                                        // > 0 on a re-queued record means the next mail is the "updated, final version" one
    // scheduled factory visit (epoch ms, 0 if none); the Follow-up time lives in followUpAt
    val factoryVisitAt: Long = 0,
    // 24-hour reminder mails, separate from the thank-you mail: "" (none) | SCHEDULED | SENT | FAILED (see Reminder)
    val factoryReminderStatus: String = "",
    val followUpReminderStatus: String = "",
    // optional photo of the back of the business card (the front is cardPhotoPath)
    val cardBackPhotoPath: String? = null,
    // secondary / third e-mail addresses joined with "; " (the main one is [email]); mails go to all of them
    val extraEmails: String = "",
    val website: String = "",            // company website from the card, e.g. www.example.com
    // secondary / third phone numbers joined with "; " (the main one is [phone])
    val extraPhones: String = "",
    // landline / office number, e.g. "0422 2673333" (LRT; empty elsewhere)
    val officePhone: String = ""
)

/** The extra addresses as a list (see [Visitor.extraEmails]). */
fun Visitor.extraEmailList(): List<String> = extraEmails.split(";").map { it.trim() }.filter { it.isNotEmpty() }

/** The extra phone numbers as a list (see [Visitor.extraPhones]). */
fun Visitor.extraPhoneList(): List<String> = extraPhones.split(";").map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Tidies the phone fields of a form before saving: blanks and repeats (same last 10 digits) are dropped and the first
 * remaining number becomes the main one. Returns (main phone, extras joined with "; ").
 */
fun splitPhones(main: String, extras: List<String>): Pair<String, String> {
    val all = (listOf(main) + extras).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::phoneKey).take(MAX_PHONES)
    return all.firstOrNull().orEmpty() to all.drop(1).joinToString("; ")
}

/** Every address the visitor can be mailed at, main one first; invalid ones and repeats are left out. */
fun Visitor.mailableEmails(): List<String> =
    (listOf(email.trim()) + extraEmailList()).filter { ThankYou.isValidEmail(it) }.distinctBy { it.lowercase() }

/**
 * Tidies the e-mail fields of a form before saving: blanks and repeats are dropped and the first remaining address
 * becomes the main one. Returns (main e-mail, extras joined with "; ").
 */
fun splitEmails(main: String, extras: List<String>): Pair<String, String> {
    val all = (listOf(main) + extras).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_EMAILS)
    return all.firstOrNull().orEmpty() to all.drop(1).joinToString("; ")
}

/** Staff who have logged in on this phone by scanning their vCard QR code. */
@Entity(tableName = "employees", indices = [Index(value = ["empKey"], unique = true)])
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val empKey: String,         // email, else phone digits, else name (see EmployeeInfo.key)
    val name: String,
    val title: String,
    val company: String,
    val email: String,
    val phone: String,
    val vcard: String,          // the QR text, kept for audit
    val firstLoginAt: Long,
    val lastLoginAt: Long
)

@Dao
interface VisitorDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(v: Visitor): Long  // -1 if uid already exists
    @Delete suspend fun delete(v: Visitor)
    @Update suspend fun update(v: Visitor)
    @Query("DELETE FROM visitors") suspend fun clear()
    @Query("UPDATE visitors SET additionalInterest = :text WHERE uid = :uid") suspend fun setAdditionalInterest(uid: String, text: String)
    @Query("UPDATE visitors SET notePhotoPaths = :paths WHERE uid = :uid") suspend fun setNotePhotos(uid: String, paths: String)
    @Query("SELECT * FROM visitors WHERE thankYouStatus = 'QUEUED' ORDER BY createdAt") suspend fun queuedThankYou(): List<Visitor>
    /** "Send thank-you mails" button: everything saved but not yet mailed joins the send queue. Returns how many. */
    @Query("UPDATE visitors SET thankYouStatus = 'QUEUED' WHERE thankYouStatus = 'PENDING'") suspend fun queueAllThankYou(): Int
    @Query("UPDATE visitors SET thankYouStatus = :status, thankYouSentAt = :at WHERE uid = :uid")
    suspend fun markThankYou(uid: String, status: String, at: Long)
    /** Automatic send: queues one visitor, unless it was already queued or mailed in the meantime. */
    @Query("UPDATE visitors SET thankYouStatus = 'QUEUED' WHERE uid = :uid AND thankYouStatus = 'PENDING'")
    suspend fun queueThankYou(uid: String)
    /** Changes only the status. thankYouSentAt is kept, so a record that was mailed before still sends the "updated" version. */
    @Query("UPDATE visitors SET thankYouStatus = :status WHERE uid = :uid")
    suspend fun setThankYouStatus(uid: String, status: String)
    @Query("SELECT * FROM visitors WHERE factoryReminderStatus = 'SCHEDULED' OR followUpReminderStatus = 'SCHEDULED'")
    suspend fun scheduledReminders(): List<Visitor>
    @Query("UPDATE visitors SET factoryReminderStatus = :status WHERE uid = :uid") suspend fun setFactoryReminder(uid: String, status: String)
    @Query("UPDATE visitors SET followUpReminderStatus = :status WHERE uid = :uid") suspend fun setFollowUpReminder(uid: String, status: String)
    @Query("UPDATE visitors SET followUpAt = :at, followUpReminderStatus = :status WHERE uid = :uid")
    suspend fun setFollowUp(uid: String, at: Long, status: String)
    @Query("SELECT * FROM visitors WHERE uid = :uid") suspend fun byUid(uid: String): Visitor?
    @Query("SELECT * FROM visitors ORDER BY createdAt DESC") fun all(): Flow<List<Visitor>>
    @Query("SELECT COUNT(*) FROM visitors WHERE uid LIKE :prefix || '%'") suspend fun countUidPrefix(prefix: String): Int
    /** Profile edited: visits this staff member attended on this phone follow the corrected details. */
    @Query("UPDATE visitors SET attendedByKey = :newKey, attendedByName = :name, attendedByTitle = :title, " +
        "attendedByEmail = :email, attendedByPhone = :phone WHERE attendedByKey = :oldKey")
    suspend fun renameStaff(oldKey: String, newKey: String, name: String, title: String, email: String, phone: String): Int
}

/** Readable UID: STAFF3-dd-HHmm-NNNN, e.g. SIV-13-0922-0001(also the photo file name in exports). */
object UidGen {
    fun prefix(staffName: String, deviceName: String, at: Long): String {
        fun letters(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .filter { it in 'A'..'Z' || it in 'a'..'z' }.take(3).uppercase()
        val code = letters(staffName).ifEmpty { letters(deviceName) }.ifEmpty { "VIS" }.padEnd(3, 'X')
        val stamp = java.text.SimpleDateFormat("dd-HHmm", java.util.Locale.US).format(java.util.Date(at))
        return "$code-$stamp-"
    }
    fun withSeq(prefix: String, existing: Int) = prefix + (existing + 1).toString().padStart(4, '0')
}

@Dao
abstract class EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insert(e: Employee): Long

    @Query("UPDATE employees SET name = :name, title = :title, company = :company, email = :email, " +
        "phone = :phone, vcard = :vcard, lastLoginAt = :at WHERE empKey = :key")
    abstract suspend fun touch(key: String, name: String, title: String, company: String,
                               email: String, phone: String, vcard: String, at: Long)

    @Query("SELECT * FROM employees ORDER BY lastLoginAt DESC") abstract fun all(): Flow<List<Employee>>

    @Query("UPDATE employees SET empKey = :newKey, name = :name, title = :title, company = :company, " +
        "email = :email, phone = :phone WHERE empKey = :oldKey")
    abstract suspend fun edit(oldKey: String, newKey: String, name: String, title: String, company: String,
                              email: String, phone: String): Int

    /** First login adds the person; later logins refresh their details and last-login time. */
    @Transaction
    open suspend fun login(e: Employee) {
        if (insert(e) == -1L) touch(e.empKey, e.name, e.title, e.company, e.email, e.phone, e.vcard, e.lastLoginAt)
    }
}

/** v1 -> v2: address fields and "attended by" on visitors, plus the employees table. Existing rows are kept. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("address", "state", "country", "attendedByKey", "attendedByName",
            "attendedByTitle", "attendedByEmail", "attendedByPhone").forEach {
            db.execSQL("ALTER TABLE visitors ADD COLUMN `$it` TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `employees` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`empKey` TEXT NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `company` TEXT NOT NULL, " +
            "`email` TEXT NOT NULL, `phone` TEXT NOT NULL, `vcard` TEXT NOT NULL, " +
            "`firstLoginAt` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_employees_empKey` ON `employees` (`empKey`)")
    }
}

/** v2 -> v3: market exposure (Domestic / International visitor) on visitors. Existing rows are kept with a blank value. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `marketExposure` TEXT NOT NULL DEFAULT ''")
    }
}

/** v3 -> v4: free-text additional product interest / models, captured on the Done screen. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `additionalInterest` TEXT NOT NULL DEFAULT ''")
    }
}

/** v4 -> v5: lead outcome, discussion notes, remarks and follow-up, captured on the Outcome step. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("leadOutcomes", "discussionNotes", "remarks", "remarksOther", "followUpAction", "followUpNote").forEach {
            db.execSQL("ALTER TABLE visitors ADD COLUMN `$it` TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("ALTER TABLE visitors ADD COLUMN `followUpAt` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5 -> v6: photos / uploads of handwritten enquiries, attached on the Done screen. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `notePhotoPaths` TEXT NOT NULL DEFAULT ''")
    }
}

/** v6 -> v7: status of the automatic thank-you e-mail. Existing visitors stay blank, so they are never mailed retroactively. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `thankYouStatus` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE visitors ADD COLUMN `thankYouSentAt` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v7 -> v8: factory visit time and the two 24-hour reminder mail statuses. Existing rows stay blank: nothing is mailed retroactively. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `factoryVisitAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE visitors ADD COLUMN `factoryReminderStatus` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE visitors ADD COLUMN `followUpReminderStatus` TEXT NOT NULL DEFAULT ''")
    }
}

/** v8 -> v9: optional photo of the back of the business card. Existing rows have none. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `cardBackPhotoPath` TEXT")
    }
}

/** v9 -> v10: secondary / third e-mail addresses. Existing rows have none. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `extraEmails` TEXT NOT NULL DEFAULT ''")
    }
}

/** v10 -> v11: company website. Existing rows have none. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `website` TEXT NOT NULL DEFAULT ''")
    }
}

/** v11 -> v12: secondary / third phone numbers. Existing rows have none. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `extraPhones` TEXT NOT NULL DEFAULT ''")
    }
}

/** v12 -> v13: office (landline) number. Existing rows have none. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE visitors ADD COLUMN `officePhone` TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Visitor::class, Employee::class], version = 13, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): VisitorDao
    abstract fun employeeDao(): EmployeeDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "visitors.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .build().also { inst = it }
        }
    }
}
