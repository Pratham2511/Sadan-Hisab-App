package com.pansare.sadan.domain

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Canonical yyyy-MM month key used throughout the application.
 * All ledger months, payment ranges, and date comparisons use this format.
 */
object MonthKey {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM")

    private val inputFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM"),
        DateTimeFormatter.ofPattern("yyyy/MM"),
        DateTimeFormatter.ofPattern("MM/yyyy"),
        DateTimeFormatter.ofPattern("M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-M"),
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM-yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM yy", Locale.US),
        DateTimeFormatter.ofPattern("MMM-yy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM yy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM-yy", Locale.US)
    )

    private val monthNames = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10, "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    fun parse(value: String): YearMonth {
        val trimmed = value.trim()
        if (trimmed.isBlank()) throw DateTimeParseException("Empty month string", trimmed, 0)

        // Try canonical first
        runCatching { return YearMonth.parse(trimmed, format) }

        // Try standard formatters
        for (formatter in inputFormatters) {
            val ym = runCatching { YearMonth.parse(trimmed, formatter) }.getOrNull()
            if (ym != null) return ym
        }

        // Try fallback regex/word extraction
        val clean = trimmed.lowercase().replace("-", " ").replace("/", " ").replace(".", "")
        val words = clean.split(Regex("""\s+"""))
        var foundMonth = 0
        var foundYear = 0

        for (word in words) {
            val m = monthNames[word]
            if (m != null) {
                foundMonth = m
            } else if (word.all { it.isDigit() }) {
                val num = word.toIntOrNull() ?: 0
                if (num in 2000..2100) {
                    foundYear = num
                } else if (num in 24..99) {
                    foundYear = 2000 + num
                } else if (num in 1..12 && foundMonth == 0) {
                    foundMonth = num
                }
            }
        }

        if (foundMonth > 0 && foundYear > 0) {
            return YearMonth.of(foundYear, foundMonth)
        } else if (foundMonth > 0 && foundYear == 0) {
            return YearMonth.of(YearMonth.now().year, foundMonth)
        }

        throw DateTimeParseException("Could not parse month: '$value'", value, 0)
    }

    fun parseOrNull(value: String): YearMonth? = runCatching { parse(value) }.getOrNull()

    fun normalize(value: String): String {
        val ym = parseOrNull(value) ?: return value
        return format(ym)
    }

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

    /** True when [value] is a well-formed yyyy-MM key or parseable month string. */
    fun isValid(value: String): Boolean = parseOrNull(value) != null

    fun displayName(monthKey: String): String {
        val ym = parse(monthKey)
        val monthName = ym.month.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        return "$monthName ${ym.year}"
    }

    fun monthsBetween(from: String, to: String): Int {
        return betweenInclusive(from, to).size
    }
}
