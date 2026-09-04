package com.pansare.sadan.ui

import com.pansare.sadan.data.LedgerStatus
import com.pansare.sadan.data.RentRepository
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.domain.UnpaidPeriod

/**
 * Builds every report from live ledger and payment data.
 * No figure in this file is hard-coded; totals change the moment a payment does.
 */
class ReportBuilder(private val repo: RentRepository) {

    data class MonthlyCollection(
        val month: String,
        val expected: Long,
        val collected: Long,
        val outstanding: Long,
        val paidCount: Int,
        val partialCount: Int,
        val unpaidCount: Int
    ) {
        val hasData: Boolean get() = expected > 0 || collected > 0
        val collectionRate: Int get() = if (expected > 0) ((collected * 100) / expected).toInt() else 0
    }

    data class YearlyCollection(
        val year: Int,
        val expected: Long,
        val collected: Long,
        val outstanding: Long,
        val months: List<MonthlyCollection>
    ) {
        val hasData: Boolean get() = expected > 0 || collected > 0
        val collectionRate: Int get() = if (expected > 0) ((collected * 100) / expected).toInt() else 0
    }

    data class TenantHistoryRow(
        val month: String,
        val rentDue: Long,
        val paid: Long,
        val balance: Long,
        val status: LedgerStatus
    )

    data class TenantHistory(
        val tenantName: String,
        val roomNumber: String,
        val rows: List<TenantHistoryRow>,
        val totalDue: Long,
        val totalPaid: Long,
        val totalOutstanding: Long,
        val unpaidPeriods: List<UnpaidPeriod>,
        val hasUnresolvedHistory: Boolean
    ) {
        val hasData: Boolean get() = rows.isNotEmpty()
    }

    suspend fun monthly(month: String): MonthlyCollection {
        require(MonthKey.isValid(month)) { "Enter a month as yyyy-MM." }
        val expected = repo.database().ledgerDao().expectedForMonth(month)
        val collected = repo.database().ledgerDao().collectedForMonth(month)
        val dao = repo.database().ledgerDao()
        return MonthlyCollection(
            month = month,
            expected = expected,
            collected = collected,
            outstanding = (expected - collected).coerceAtLeast(0),
            paidCount = dao.countByStatusForMonth(month, LedgerStatus.PAID),
            partialCount = dao.countByStatusForMonth(month, LedgerStatus.PARTIALLY_PAID),
            unpaidCount = dao.countByStatusForMonth(month, LedgerStatus.UNPAID)
        )
    }

    suspend fun yearly(year: Int): YearlyCollection {
        require(year in 1900..2200) { "Enter a four digit year." }
        val from = "%04d-01".format(year)
        val to = "%04d-12".format(year)
        val dao = repo.database().ledgerDao()
        val expected = dao.expectedForRange(from, to)
        val collected = dao.collectedForRange(from, to)
        val months = MonthKey.betweenInclusive(from, to).map { monthly(it) }
        return YearlyCollection(
            year = year,
            expected = expected,
            collected = collected,
            outstanding = (expected - collected).coerceAtLeast(0),
            months = months
        )
    }

    suspend fun tenantHistory(tenantId: Long): TenantHistory {
        val tenant = repo.findTenant(tenantId) ?: error("Tenant not found.")
        val room = repo.findRoom(tenant.roomId) ?: error("Room not found.")
        val rows = repo.getAllLedger().filter { it.tenantId == tenantId }.sortedBy { it.month }
        val summary = repo.summaryFor(tenantId)
        return TenantHistory(
            tenantName = tenant.tenantName,
            roomNumber = room.displayRoomNumber,
            rows = rows.map {
                TenantHistoryRow(it.month, it.rentDue, it.totalPaid, it.balance, it.status)
            },
            totalDue = rows.sumOf { it.rentDue },
            totalPaid = rows.sumOf { it.totalPaid },
            totalOutstanding = summary.totalOutstanding,
            unpaidPeriods = summary.unpaidPeriods,
            hasUnresolvedHistory = summary.hasUnresolvedHistory
        )
    }
}
