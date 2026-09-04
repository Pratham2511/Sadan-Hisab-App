package com.pansare.sadan.util

import com.pansare.sadan.domain.RawPaymentRow
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Turns a CSV file into [RawPaymentRow]s.
 *
 * Parsing is deliberately permissive: anything that cannot be understood is passed through
 * as null so that [com.pansare.sadan.domain.ImportEngine] can reject it with a specific,
 * user-visible reason. Nothing is guessed or silently dropped here — a row that reaches this
 * parser always produces exactly one RawPaymentRow.
 */
object CsvImport {

    /** Header names we recognise, lowercased and stripped of spaces/underscores. */
    private val ROOM = setOf("room", "roomno", "roomnumber", "flat")
    private val NAME = setOf("name", "tenant", "tenantname")
    private val DATE = setOf("date", "paymentdate", "paiddate", "paidon")
    private val AMOUNT = setOf("amount", "amt", "rent", "amountpaid", "paid")
    private val RECEIPT = setOf("receipt", "receiptno", "receiptnumber", "billno")
    private val MODE = setOf("mode", "paymentmode", "method", "type")
    private val FROM = setOf("from", "frommonth", "paidfrom", "periodfrom", "monthfrom")
    private val TO = setOf("to", "tomonth", "paidto", "periodto", "monthto")

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
        "d-M-yyyy", "d/M/yyyy", "dd-MMM-yyyy", "dd MMM yyyy"
    )

    private val MONTH_PATTERNS = listOf("yyyy-MM", "MM-yyyy", "MM/yyyy", "MMM-yyyy", "MMM yyyy")

    /**
     * @param text the whole CSV file
     * @return one row per non-blank data line, in file order
     * @throws IllegalArgumentException if the file has no header or no data lines at all
     */
    fun parse(text: String): List<RawPaymentRow> {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        require(lines.isNotEmpty()) { "The file is empty." }

        val header = splitLine(lines.first()).map { normalise(it) }
        require(header.any { it in ROOM }) {
            "No room column found. The first line must be a header containing a Room column."
        }
        require(lines.size > 1) { "The file has a header but no data rows." }

        fun indexOf(names: Set<String>) = header.indexOfFirst { it in names }.takeIf { it >= 0 }

        val iRoom = indexOf(ROOM)
        val iName = indexOf(NAME)
        val iDate = indexOf(DATE)
        val iAmount = indexOf(AMOUNT)
        val iReceipt = indexOf(RECEIPT)
        val iMode = indexOf(MODE)
        val iFrom = indexOf(FROM)
        val iTo = indexOf(TO)

        return lines.drop(1).mapIndexed { index, line ->
            val cells = splitLine(line)
            fun cell(i: Int?): String? = i?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

            // Row numbers are 1-based and count the header, so they match what the user
            // sees in a spreadsheet.
            RawPaymentRow(
                rowNumber = index + 2,
                roomDisplay = cell(iRoom),
                tenantName = cell(iName),
                paymentDateMillis = cell(iDate)?.let { parseDate(it) },
                amount = cell(iAmount)?.let { parseAmount(it) },
                receiptNumber = cell(iReceipt),
                paymentMode = cell(iMode),
                paidFromMonth = cell(iFrom)?.let { parseMonth(it) },
                paidToMonth = cell(iTo)?.let { parseMonth(it) }
            )
        }
    }

    /** Splits one CSV line, honouring double quotes and "" escapes. */
    internal fun splitLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out.add(cell.toString()); cell.setLength(0) }
                else -> cell.append(c)
            }
            i++
        }
        out.add(cell.toString())
        return out
    }

    private fun normalise(s: String) =
        s.lowercase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "")

    /** Accepts "600", "600.00", "₹600", "1,200" — rejects anything else by returning null. */
    internal fun parseAmount(raw: String): Long? {
        val cleaned = raw.replace("₹", "").replace(",", "").replace("Rs", "", ignoreCase = true).trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        // Rupee amounts only; fractional paise in a rent register indicate a bad column.
        if (value != Math.floor(value)) return null
        return value.toLong()
    }

    internal fun parseDate(raw: String): Long? {
        for (pattern in DATE_PATTERNS) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                return fmt.parse(raw)?.time ?: return@runCatching
            }
        }
        return null
    }

    /** Normalises any recognised month spelling to the canonical `yyyy-MM`. */
    internal fun parseMonth(raw: String): String? {
        val out = SimpleDateFormat("yyyy-MM", Locale.ENGLISH)
        for (pattern in MONTH_PATTERNS) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                val parsed = fmt.parse(raw) ?: return@runCatching
                return out.format(parsed)
            }
        }
        return null
    }
}
