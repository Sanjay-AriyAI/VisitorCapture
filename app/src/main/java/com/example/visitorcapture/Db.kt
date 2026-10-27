package com.example.visitorcapture

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "visitors", indices = [Index(value = ["uid"], unique = true)])
data class Visitor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,            // globally unique (UUID), used to merge devices
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
    val cardPhotoPath: String?,
    val createdAt: Long,        // date and time of the visit (epoch ms), stamped when the visit starts
    // the staff member who attended this visitor, copied from the QR login at the time of the visit
    val attendedByKey: String = "",
    val attendedByName: String = "",
    val attendedByTitle: String = "",
    val attendedByEmail: String = "",
    val attendedByPhone: String = ""
)

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
    @Query("DELETE FROM visitors") suspend fun clear()
    @Query("UPDATE visitors SET additionalInterest = :text WHERE uid = :uid") suspend fun setAdditionalInterest(uid: String, text: String)
    @Query("SELECT * FROM visitors ORDER BY createdAt DESC") fun all(): Flow<List<Visitor>>
}

@Dao
abstract class EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insert(e: Employee): Long

    @Query("UPDATE employees SET name = :name, title = :title, company = :company, email = :email, " +
        "phone = :phone, vcard = :vcard, lastLoginAt = :at WHERE empKey = :key")
    abstract suspend fun touch(key: String, name: String, title: String, company: String,
                               email: String, phone: String, vcard: String, at: Long)

    @Query("SELECT * FROM employees ORDER BY lastLoginAt DESC") abstract fun all(): Flow<List<Employee>>

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

@Database(entities = [Visitor::class, Employee::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): VisitorDao
    abstract fun employeeDao(): EmployeeDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "visitors.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { inst = it }
        }
    }
}
