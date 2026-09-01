package com.pansare.sadan.domain

import com.pansare.sadan.data.LedgerStatus
import com.pansare.sadan.data.MonthlyLedgerEntity

/**
 * Core ledger engine. Derives status from amounts and calculates
 * outstanding balances using only the month-wise ledger — never
 * from current rent × historical months.
 */
object LedgerEngine {

    fun statusFor(due: Long, paid: Long): LedgerStatus = when {
        paid <= 0 -> LedgerStatus.UNPAID
        paid < due -> LedgerStatus.PARTIALLY_PAID
        else -> LedgerStatus.PAID
    }

    fun outstanding(rows: List<MonthlyLedgerEntity>, asOf: String): Long =
        rows.filter { it.month <= asOf }.sumOf { it.balance }

    fun unpaidMonths(rows: List<MonthlyLedgerEntity>, asOf: String): Int =
        rows.count { it.month <= asOf && it.status == LedgerStatus.UNPAID }

    fun partiallyPaidMonths(rows: List<MonthlyLedgerEntity>, asOf: String): Int =
        rows.count { it.month <= asOf && it.status == LedgerStatus.PARTIALLY_PAID }

    fun monthsWithBalance(rows: List<MonthlyLedgerEntity>, asOf: String): Int =
        rows.count { it.month <= asOf && it.balance > 0 }
}
