package com.pansare.sadan.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pansare.sadan.data.ImportValidationIssueEntity
import com.pansare.sadan.data.MonthlyLedgerEntity
import com.pansare.sadan.data.PaymentAllocationEntity
import com.pansare.sadan.data.PaymentEntity
import com.pansare.sadan.data.RentChangeEntity
import com.pansare.sadan.data.RentRepository
import com.pansare.sadan.data.RoomEntity
import com.pansare.sadan.data.SettingEntity
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.util.BackupCrypto
import java.io.ByteArrayOutputStream

/**
 * Encrypted, versioned backup of every table.
 *
 * Restore order is: read -> decrypt (authenticates) -> parse -> validate -> stage ->
 * transactional replace. A failure at any step before the transaction leaves the live
 * database untouched, and a failure inside it rolls back.
 */
class BackupManager(private val repo: RentRepository) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    data class BackupPayload(
        val version: Int = CURRENT_VERSION,
        val createdAt: Long = System.currentTimeMillis(),
        val rooms: List<RoomEntity> = emptyList(),
        val tenants: List<TenantEntity> = emptyList(),
        val payments: List<PaymentEntity> = emptyList(),
        val ledger: List<MonthlyLedgerEntity> = emptyList(),
        val allocations: List<PaymentAllocationEntity> = emptyList(),
        val rentChanges: List<RentChangeEntity> = emptyList(),
        val settings: List<SettingEntity> = emptyList(),
        val validationIssues: List<ImportValidationIssueEntity> = emptyList()
    )

    suspend fun export(context: Context, uri: Uri, password: CharArray) {
        require(password.size >= MIN_PASSWORD) {
            "Choose a backup password of at least $MIN_PASSWORD characters."
        }

        val payload = BackupPayload(
            rooms = repo.getAllRooms(),
            tenants = repo.getAllTenants(),
            payments = repo.getAllPayments(),
            ledger = repo.getAllLedger(),
            allocations = repo.getAllAllocations(),
            rentChanges = repo.getAllRentChanges(),
            settings = repo.getAllSettings(),
            validationIssues = repo.getAllValidationIssues()
        )

        val bytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val envelope = BackupCrypto.encrypt(bytes, password)

        context.contentResolver.openOutputStream(uri)?.use { it.write(envelope) }
            ?: throw IllegalStateException("Could not open the selected file for writing.")
    }

    /** Decrypts and validates without touching the database. */
    suspend fun validate(context: Context, uri: Uri, password: CharArray): BackupPayload {
        val envelope = context.contentResolver.openInputStream(uri)?.use { input ->
            ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray()
        } ?: throw IllegalStateException("Could not open the selected backup file.")

        val json = BackupCrypto.decrypt(envelope, password).decodeToString()

        val payload = runCatching { gson.fromJson(json, BackupPayload::class.java) }
            .getOrNull() ?: throw IllegalArgumentException("The backup file is not readable.")

        if (payload.version > CURRENT_VERSION) {
            throw IllegalArgumentException(
                "This backup was made by a newer version of Sadan (format ${payload.version}). Update the app first."
            )
        }
        if (payload.rooms.isEmpty()) {
            throw IllegalArgumentException("The backup contains no room inventory and cannot be restored.")
        }

        // Referential sanity before anything is written.
        val roomIds = payload.rooms.map { it.id }.toSet()
        val tenantIds = payload.tenants.map { it.id }.toSet()
        val ledgerIds = payload.ledger.map { it.id }.toSet()
        val paymentIds = payload.payments.map { it.id }.toSet()

        if (payload.tenants.any { it.roomId !in roomIds }) {
            throw IllegalArgumentException("The backup is inconsistent: a tenant refers to a missing room.")
        }
        if (payload.payments.any { it.tenantId !in tenantIds }) {
            throw IllegalArgumentException("The backup is inconsistent: a payment refers to a missing tenant.")
        }
        if (payload.ledger.any { it.tenantId !in tenantIds }) {
            throw IllegalArgumentException("The backup is inconsistent: a ledger month refers to a missing tenant.")
        }
        if (payload.allocations.any { it.paymentId !in paymentIds || it.ledgerMonthId !in ledgerIds }) {
            throw IllegalArgumentException("The backup is inconsistent: an allocation refers to missing records.")
        }

        return payload
    }

    /**
     * Validates first, then replaces the database contents in a single transaction.
     * If the restore throws, Room rolls back and the previous data survives intact.
     */
    suspend fun restore(context: Context, uri: Uri, password: CharArray): BackupPayload {
        val payload = validate(context, uri, password)
        val db = repo.database()

        db.withTransaction {
            db.clearAllTables()
            db.roomDao().insertAll(payload.rooms)
            payload.tenants.forEach { db.tenantDao().insert(it) }
            payload.payments.forEach { db.paymentDao().insert(it) }
            db.ledgerDao().insertAll(payload.ledger)
            db.allocationDao().insertAll(payload.allocations)
            payload.rentChanges.forEach { db.rentChangeDao().insert(it) }
            payload.settings.forEach { db.settingsDao().upsert(it) }
            db.validationDao().insertAll(payload.validationIssues)

            // Rebuild the derived projection so restored data is self-consistent.
            payload.tenants.forEach { repo.recalculateTenant(it.id) }
        }
        return payload
    }

    companion object {
        const val CURRENT_VERSION = 2
        const val MIN_PASSWORD = 8
        const val FILE_EXTENSION = "sadanbackup"
    }
}
