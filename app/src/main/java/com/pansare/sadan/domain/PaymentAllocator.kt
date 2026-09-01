package com.pansare.sadan.domain

import com.pansare.sadan.data.MonthlyLedgerEntity

data class PlannedAllocation(val ledgerId: Long, val amount: Long)

/**
 * Allocates payment amounts to ledger months.
 * Oldest outstanding months are filled first.
 * Never divides across historical rents blindly.
 */
object PaymentAllocator {

    /**
     * Plans allocation of [amount] across the given [rows].
     * Fills oldest months first up to their remaining balance.
     *
     * @throws IllegalArgumentException if amount is zero, negative, or exceeds total remaining balance
     */
    fun plan(rows: List<MonthlyLedgerEntity>, amount: Long): List<PlannedAllocation> {
        require(amount > 0) { "Payment amount cannot be zero." }

        var remaining = amount
        val result = mutableListOf<PlannedAllocation>()

        rows.forEach { row ->
            if (remaining > 0) {
                val part = minOf(row.balance, remaining)
                if (part > 0) {
                    result += PlannedAllocation(row.id, part)
                    remaining -= part
                }
            }
        }

        require(remaining == 0L) {
            "Payment exceeds the remaining balance by ₹$remaining."
        }

        return result
    }

    /**
     * Calculates how much can be allocated to a set of months.
     * Used for preview / auto-calculation in the payment entry screen.
     */
    fun maxAllocatable(rows: List<MonthlyLedgerEntity>): Long {
        return rows.sumOf { it.balance }
    }
}
