package com.pansare.sadan.domain

import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Canonical yyyy-MM month key used throughout the application.
 * All ledger months, payment ranges, and date comparisons use this format.
 */
object MonthKey {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM")

    fun parse(value: String): YearMonth = YearMonth.parse(value, format)

    fun format(value: YearMonth): String = value.format(format)

    fun betweenInclusive(from: String, to: String): List<String> {
        val start = parse(from)
        val end = parse(to)
        require(!start.isAfter(end)) { "From month must not be after to month." }
        return generateSequence(start) { if (it == end) null else it.plusMonths(1) }
            .map(::format)
            .toList()
    }

    fun current(): String = format(YearMonth.now())

    /** The month immediately after [monthKey]. Used to detect gaps in a ledger run. */
    fun next(monthKey: String): String = format(parse(monthKey).plusMonths(1))

    /** The month immediately before [monthKey]. */
    fun previous(monthKey: String): String = format(parse(monthKey).minusMonths(1))

    /** True when [value] is a well-formed yyyy-MM key. */
    fun isValid(value: String): Boolean = runCatching { parse(value) }.isSuccess

    fun displayName(monthKey: String): String {
        val ym = parse(monthKey)
        return "${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ym.year}"
    }

    fun monthsBetween(from: String, to: String): Int {
        return betweenInclusive(from, to).size
    }
}
