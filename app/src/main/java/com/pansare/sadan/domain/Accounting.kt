package com.pansare.sadan.domain

/**
 * Pure-Kotlin accounting core for Sadan.
 *
 * Nothing in this file touches Room, Android or Compose, so every accounting rule
 * below is unit-testable on a plain JVM. The Room layer maps its entities onto these
 * value types, calls the engine, and writes the result back inside a transaction.
 *
 * Source-of-truth chain:
 *   RentChange  -> rent applicable to a month
 *   LedgerMonth -> what is owed for that month
 *   Allocation  -> how much of which payment settled that month
 *   MonthState  -> derived paid / outstanding / status (never stored as the truth)
 */

/** Real accounting states of a ledger month. */
enum class LedgerStatusValue { UNPAID, PARTIALLY_PAID, PAID }

/**
 * How confident we are about the rent used for a month.
 * Historical imports that do not carry enough information must NOT be silently
 * treated as authoritative — they are marked and surfaced to the user instead.
 */
enum class RentCertainty {
    /** Rent is backed by an explicit, dated rent record. */
    KNOWN,

    /** Rent was derived from a neighbouring known rate; usable but flagged. */
    APPROXIMATED,

    /** No defensible rent could be determined. Never counted as authoritative. */
    UNRESOLVED
}

/** A single month a tenant owes rent for. */
data class LedgerMonth(
    val id: Long,
    val month: String,
    val rentDue: Long,
    val certainty: RentCertainty = RentCertainty.KNOWN
) {
    init {
        require(rentDue >= 0) { "Rent due cannot be negative for $month." }
    }
}

/** A portion of one payment applied to one ledger month. */
data class Allocation(
    val paymentId: Long,
    val ledgerMonthId: Long,
    val amount: Long
) {
    init {
        require(amount > 0) { "An allocation must be a positive amount." }
    }
}

/** Derived state of a ledger month. Always computed, never trusted from storage. */
data class MonthState(
    val id: Long,
    val month: String,
    val rentDue: Long,
    val paid: Long,
    val certainty: RentCertainty
) {
    val outstanding: Long get() = rentDue - paid

    val status: LedgerStatusValue
        get() = when {
            paid <= 0L -> LedgerStatusValue.UNPAID
            paid >= rentDue -> LedgerStatusValue.PAID
            else -> LedgerStatusValue.PARTIALLY_PAID
        }

    /** Only KNOWN months may be added into authoritative financial totals. */
    val isAuthoritative: Boolean get() = certainty == RentCertainty.KNOWN
}

/** One planned allocation produced before anything is written to the database. */
data class PlannedAllocation(val ledgerMonthId: Long, val month: String, val amount: Long)

/**
 * A full preview of what a payment will do, shown to the user before saving so that
 * allocation is never a silent guess.
 */
data class AllocationPlan(
    val lines: List<PlannedAllocation>,
    val amount: Long,
    val allocated: Long,
    val statesBefore: List<MonthState>,
    val statesAfter: List<MonthState>
) {
    val unallocated: Long get() = amount - allocated
    val monthsTouched: Int get() = lines.size
}

/** Raised when an operation would break an accounting invariant. */
class AccountingException(message: String) : IllegalArgumentException(message)

/** A contiguous run of months that carry a balance, e.g. "Feb 2025 – Mar 2025". */
data class UnpaidPeriod(val fromMonth: String, val toMonth: String, val months: Int, val amount: Long)

/** Everything the defaulter view and tenant profile need, derived purely from the ledger. */
data class DefaulterSummary(
    val totalOutstanding: Long,
    val unresolvedOutstanding: Long,
    val unpaidMonths: Int,
    val partialMonths: Int,
    val outstandingSince: String?,
    val lastPaidUpTo: String?,
    val unpaidPeriods: List<UnpaidPeriod>,
    val hasUnresolvedHistory: Boolean
) {
    val isDefaulter: Boolean get() = totalOutstanding > 0
}

object LedgerEngine {

    /**
     * Derives the state of every month from the months plus the allocations against them.
     * Deterministic: months are always ordered chronologically by month key, then by id.
     */
    fun computeStates(months: List<LedgerMonth>, allocations: List<Allocation>): List<MonthState> {
        val paidByMonth = allocations.groupBy { it.ledgerMonthId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        return months
            .sortedWith(compareBy({ it.month }, { it.id }))
            .map { m ->
                val paid = paidByMonth[m.id] ?: 0L
                if (paid > m.rentDue) {
                    throw AccountingException(
                        "Allocations for ${m.month} (${paid}) exceed the rent due (${m.rentDue})."
                    )
                }
                MonthState(
                    id = m.id,
                    month = m.month,
                    rentDue = m.rentDue,
                    paid = paid,
                    certainty = m.certainty
                )
            }
    }

    /**
     * Plans how [amount] settles the given months, oldest outstanding first.
     *
     * The app has no credit/advance facility, so any amount that cannot be placed against
     * a real outstanding balance is rejected rather than silently parked or dropped.
     */
    fun plan(states: List<MonthState>, amount: Long): AllocationPlan {
        if (amount <= 0L) throw AccountingException("Payment amount must be greater than zero.")

        val ordered = states.sortedWith(compareBy({ it.month }, { it.id }))
        val capacity = ordered.sumOf { it.outstanding }
        if (amount > capacity) {
            throw AccountingException(
                "This payment of ₹$amount exceeds the ₹$capacity outstanding for the selected period. " +
                    "Advance payments are not supported — reduce the amount or extend the period."
            )
        }

        var remaining = amount
        val lines = mutableListOf<PlannedAllocation>()
        for (state in ordered) {
            if (remaining <= 0L) break
            val part = minOf(state.outstanding, remaining)
            if (part > 0L) {
                lines += PlannedAllocation(state.id, state.month, part)
                remaining -= part
            }
        }

        val allocated = lines.sumOf { it.amount }
        if (allocated != amount) {
            throw AccountingException("Allocation did not consume the full payment amount.")
        }

        val addedByMonth = lines.associate { it.ledgerMonthId to it.amount }
        val after = ordered.map { it.copy(paid = it.paid + (addedByMonth[it.id] ?: 0L)) }

        return AllocationPlan(
            lines = lines,
            amount = amount,
            allocated = allocated,
            statesBefore = ordered,
            statesAfter = after
        )
    }

    /**
     * Plans a payment while excluding the allocations of a payment being edited, so that
     * editing behaves exactly like "reverse, then re-apply" without ever double counting.
     */
    fun planForEdit(
        months: List<LedgerMonth>,
        allAllocations: List<Allocation>,
        paymentIdBeingEdited: Long,
        amount: Long
    ): AllocationPlan {
        val without = allAllocations.filterNot { it.paymentId == paymentIdBeingEdited }
        return plan(computeStates(months, without), amount)
    }

    /** Total outstanding across months up to and including [asOf]. */
    fun outstanding(states: List<MonthState>, asOf: String): Long =
        states.filter { it.month <= asOf }.sumOf { it.outstanding }

    /** Outstanding that rests on months whose rent could not be determined. */
    fun unresolvedOutstanding(states: List<MonthState>, asOf: String): Long =
        states.filter { it.month <= asOf && it.certainty == RentCertainty.UNRESOLVED }
            .sumOf { it.outstanding }

    /**
     * Groups months that carry a balance into contiguous runs.
     * A paid month in the middle correctly splits the run in two, so gaps are never
     * reported as one long unpaid stretch.
     */
    fun unpaidPeriods(states: List<MonthState>, asOf: String): List<UnpaidPeriod> {
        val relevant = states.filter { it.month <= asOf }.sortedBy { it.month }
        val periods = mutableListOf<UnpaidPeriod>()
        var runStart: MonthState? = null
        var runEnd: MonthState? = null
        var runAmount = 0L
        var runCount = 0

        fun flush() {
            val start = runStart ?: return
            val end = runEnd ?: return
            periods += UnpaidPeriod(start.month, end.month, runCount, runAmount)
            runStart = null; runEnd = null; runAmount = 0L; runCount = 0
        }

        for (state in relevant) {
            if (state.outstanding > 0L) {
                val previous = runEnd
                val contiguous = previous != null && MonthKey.next(previous.month) == state.month
                if (previous != null && !contiguous) flush()
                if (runStart == null) runStart = state
                runEnd = state
                runAmount += state.outstanding
                runCount += 1
            } else {
                flush()
            }
        }
        flush()
        return periods
    }

    /** Full derived picture for one tenant. Nothing here is read from a stored flag. */
    fun summarise(states: List<MonthState>, asOf: String): DefaulterSummary {
        val relevant = states.filter { it.month <= asOf }
        val periods = unpaidPeriods(states, asOf)
        return DefaulterSummary(
            totalOutstanding = relevant.sumOf { it.outstanding },
            unresolvedOutstanding = unresolvedOutstanding(states, asOf),
            unpaidMonths = relevant.count { it.status == LedgerStatusValue.UNPAID },
            partialMonths = relevant.count { it.status == LedgerStatusValue.PARTIALLY_PAID },
            outstandingSince = periods.firstOrNull()?.fromMonth,
            lastPaidUpTo = relevant.filter { it.outstanding == 0L }.maxByOrNull { it.month }?.month,
            unpaidPeriods = periods,
            hasUnresolvedHistory = relevant.any { it.certainty != RentCertainty.KNOWN }
        )
    }

    /**
     * Verifies every invariant the accounting model promises. Used by tests and by the
     * repository after any mutating operation so corruption can never go unnoticed.
     */
    fun verifyInvariants(
        months: List<LedgerMonth>,
        allocations: List<Allocation>,
        paymentAmounts: Map<Long, Long>
    ) {
        val monthIds = months.map { it.id }.toSet()

        allocations.forEach {
            if (it.ledgerMonthId !in monthIds) {
                throw AccountingException("Allocation references a ledger month that does not exist.")
            }
            if (it.amount <= 0L) {
                throw AccountingException("Allocation amounts must be positive.")
            }
        }

        val duplicates = allocations
            .groupingBy { it.paymentId to it.ledgerMonthId }
            .eachCount()
            .filterValues { it > 1 }
        if (duplicates.isNotEmpty()) {
            throw AccountingException("Duplicate allocation rows detected for the same payment and month.")
        }

        allocations.groupBy { it.paymentId }.forEach { (paymentId, list) ->
            val declared = paymentAmounts[paymentId]
                ?: throw AccountingException("Allocation references payment $paymentId which does not exist.")
            val sum = list.sumOf { it.amount }
            if (sum > declared) {
                throw AccountingException("Allocations for payment $paymentId total $sum but the payment is only $declared.")
            }
        }

        // Throws if any month is over-allocated; also guarantees no negative outstanding.
        computeStates(months, allocations).forEach {
            if (it.outstanding < 0L) {
                throw AccountingException("Month ${it.month} has a negative outstanding balance.")
            }
        }
    }
}
