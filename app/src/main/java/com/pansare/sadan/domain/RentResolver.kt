package com.pansare.sadan.domain

/** A dated rent rate: [amount] applies from [effectiveFromMonth] until the next change. */
data class RentPeriod(val effectiveFromMonth: String, val amount: Long) {
    init {
        require(amount >= 0) { "Rent cannot be negative." }
    }
}

/** The rent decided for a month, together with how much we trust it. */
data class ResolvedRent(val amount: Long, val certainty: RentCertainty)

/**
 * Decides the rent that applied to a given month.
 *
 * The rule that matters: a month is charged the rate that was in force *for that month*,
 * never the tenant's current rate. If a month falls before the earliest known rate,
 * no value is invented — the caller receives null (or an UNRESOLVED marker) and the
 * period is surfaced for review instead of quietly entering the accounts.
 */
object RentResolver {

    /**
     * The rate in force for [month], or null when [month] precedes every known rate.
     * Returning null is deliberate: fabricating a historical rent would corrupt the ledger.
     */
    fun rentFor(periods: List<RentPeriod>, month: String): RentPeriod? =
        periods.filter { it.effectiveFromMonth <= month }
            .maxByOrNull { it.effectiveFromMonth }

    /**
     * Resolves [month] to a rent and a certainty level.
     *
     * - KNOWN       a dated rate covers the month.
     * - UNRESOLVED  the month predates every known rate. Amount is 0 and must never be
     *               presented as an authoritative figure.
     */
    fun resolve(periods: List<RentPeriod>, month: String): ResolvedRent {
        val match = rentFor(periods, month)
        return if (match != null) {
            ResolvedRent(match.amount, RentCertainty.KNOWN)
        } else {
            ResolvedRent(0L, RentCertainty.UNRESOLVED)
        }
    }

    /**
     * Builds the rent timeline for a whole range, so a ledger can be created month by month
     * with each month carrying its own correct rate.
     */
    fun resolveRange(periods: List<RentPeriod>, from: String, to: String): Map<String, ResolvedRent> =
        MonthKey.betweenInclusive(from, to).associateWith { resolve(periods, it) }
}
