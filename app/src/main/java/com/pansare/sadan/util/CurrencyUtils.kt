package com.pansare.sadan.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CurrencyUtils {
    private val indiaFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    /** Format Long rupee amount as ₹X,XX,XXX */
    fun format(amount: Long): String = indiaFormat.format(amount).replace(".00", "")

    /** Format with sign: +₹500 or -₹500 */
    fun formatSigned(amount: Long): String {
        val prefix = if (amount >= 0) "" else "-"
        return "$prefix${format(kotlin.math.abs(amount))}"
    }
}

object DateUtils {
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    /** Format epoch millis to "02 Sep 2026" */
    fun formatDate(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.format(dateFormat)
    }

    /** Format "yyyy-MM" to "Sep 2026" */
    fun formatMonth(monthKey: String): String {
        val ym = YearMonth.parse(monthKey)
        return ym.format(monthYearFormat)
    }

    /** Current date as epoch millis */
    fun today(): Long = System.currentTimeMillis()

    /** Current year */
    fun currentYear(): Int = LocalDate.now().year
}
