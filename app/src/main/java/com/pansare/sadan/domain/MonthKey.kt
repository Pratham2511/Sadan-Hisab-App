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

    fun displayName(monthKey: String): String {
        val ym = parse(monthKey)
        return "${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ym.year}"
    }

    fun monthsBetween(from: String, to: String): Int {
        return betweenInclusive(from, to).size
    }
}
