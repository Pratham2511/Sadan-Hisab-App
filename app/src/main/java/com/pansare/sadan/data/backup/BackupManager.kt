package com.pansare.sadan.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pansare.sadan.data.*
import com.pansare.sadan.util.BackupCrypto
import java.io.ByteArrayOutputStream
import androidx.room.withTransaction

/**
 * Manages backup export and restore using encrypted JSON snapshots.
 * Backup format: AES-GCM encrypted JSON containing all tables.
 */
class BackupManager(private val repo: RentRepository) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    data class BackupPayload(
        val version: Int = 1,
        val appVersion: String = "1.0.0",
        val timestamp: Long = System.currentTimeMillis(),
        val rooms: List<RoomEntity>,
        val tenants: List<TenantEntity>,
        val payments: List<PaymentEntity>,
        val ledger: List<MonthlyLedgerEntity>,
        val allocations: List<PaymentAllocationEntity>,
        val rentChanges: List<RentChangeEntity>,
        val settings: List<SettingEntity>,
        val validationIssues: List<ImportValidationIssueEntity>
    )

    /**
     * Export encrypted backup to the given URI via SAF.
     */
    suspend fun exportBackup(context: Context, uri: Uri, password: CharArray) {
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

        val json = gson.toJson(payload)
        val encrypted = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(encrypted)
        } ?: throw IllegalStateException("Unable to write backup file.")
    }

    /**
     * Import and restore backup from the given URI.
     * Validates before replacing data. Transactional — all or nothing.
     */
    suspend fun restoreBackup(context: Context, uri: Uri, password: CharArray): BackupPayload {
        val encrypted = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArrayOutputStream()
            stream.copyTo(buffer)
            buffer.toByteArray()
        } ?: throw IllegalStateException("Unable to read backup file.")

        val decrypted = BackupCrypto.decrypt(encrypted, password)
        val json = decrypted.decodeToString()
        val payload = gson.fromJson(json, BackupPayload::class.java)
            ?: throw IllegalArgumentException("Backup file is invalid.")

        // Validate
        require(payload.version >= 1) { "Unsupported backup version." }
        require(payload.rooms.isNotEmpty()) { "Backup contains no room data." }
        require(payload.tenants.isNotEmpty()) { "Backup contains no tenant data." }

        // Transactional restore
        val db = repo.getDatabase()
        db.withTransaction {
            db.clearAllTables()

            payload.rooms.forEach { db.roomDao().insert(it) }
            payload.tenants.forEach { db.tenantDao().insert(it) }
            payload.payments.forEach { db.paymentDao().insert(it) }
            db.ledgerDao().insertAll(payload.ledger)
            db.allocationDao().insertAll(payload.allocations)
            payload.rentChanges.forEach { db.rentChangeDao().insert(it) }
            payload.settings.forEach { db.settingsDao().upsert(it) }
            db.validationDao().insertAll(payload.validationIssues)
        }

        return payload
    }

    /**
     * Validate a backup file without restoring.
     */
    suspend fun validateBackup(context: Context, uri: Uri, password: CharArray): BackupPayload {
        val encrypted = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArrayOutputStream()
            stream.copyTo(buffer)
            buffer.toByteArray()
        } ?: throw IllegalStateException("Unable to read backup file.")

        val decrypted = BackupCrypto.decrypt(encrypted, password)
        val json = decrypted.decodeToString()
        return gson.fromJson(json, BackupPayload::class.java)
            ?: throw IllegalArgumentException("Backup file is invalid.")
    }
}
