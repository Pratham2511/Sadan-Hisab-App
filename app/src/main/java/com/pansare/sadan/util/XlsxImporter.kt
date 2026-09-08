package com.pansare.sadan.util

import android.util.Xml
import com.pansare.sadan.domain.RawPaymentRow
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

data class SheetInfo(val name: String, val rId: String)

data class RawSheetData(
    val sheetName: String,
    val rows: List<List<String>>
)

data class ParsedReceiptDetails(
    val receiptNumber: String = "",
    val dateMillis: Long? = null,
    val amount: Long = 0L,
    val fromMonth: String = "",
    val toMonth: String = "",
    val paymentMode: String = "CASH",
    val isAmbiguous: Boolean = false,
    val rawText: String = ""
)

object XlsxImporter {
    /**
     * Legacy register forms include both `305/29/09/2016` and `270  5/10/2017`.
     * Day/month are range-limited so an amount before the next receipt cannot become
     * a false receipt. The year may be followed immediately by text in old sheets.
     */
    private val receiptDateRegex = Regex(
        """\b(\d{3,8})\s*(?:/|\s+)\s*(0{0,2}(?:[1-9]|[12]\d|3[01]))\s*[./-]\s*((?:0\s*)?(?:[1-9]|1[0-2]))\s*[./-]\s*(\d{2,4})(?!\d)"""
    )

    fun listSheets(inputStream: InputStream): List<String> {
        val bytes = inputStream.use { it.readBytes() }
        val workbook = readZipEntry(bytes, "xl/workbook.xml") ?: return emptyList()
        return parseWorkbookXml(ByteArrayInputStream(workbook))
    }

    fun parseSheet(inputStream: InputStream, targetSheetName: String? = null): RawSheetData {
        val bytes = inputStream.use { it.readBytes() }
        val workbook = readZipEntry(bytes, "xl/workbook.xml")
            ?: error("This Excel file has no workbook.xml entry.")
        val rels = readZipEntry(bytes, "xl/_rels/workbook.xml.rels")
            ?: error("This Excel file has no workbook relationship map.")

        val sheetInfos = parseWorkbookXmlWithInfo(ByteArrayInputStream(workbook))
        require(sheetInfos.isNotEmpty()) { "The workbook has no worksheets." }
        val relationships = parseWorkbookRels(ByteArrayInputStream(rels))

        val chosen = targetSheetName?.let { requested ->
            sheetInfos.firstOrNull { it.name == requested }
        } ?: sheetInfos.first()

        val target = relationships[chosen.rId]
            ?: error("Could not locate worksheet '${chosen.name}'.")
        val entryName = normalizeWorksheetTarget(target)
        val sheetBytes = readZipEntry(bytes, entryName)
            ?: error("Could not read worksheet '${chosen.name}'.")

        val sharedStrings = readZipEntry(bytes, "xl/sharedStrings.xml")?.let {
            parseSharedStrings(ByteArrayInputStream(it))
        }.orEmpty()

        return RawSheetData(
            sheetName = chosen.name,
            rows = parseWorksheetXml(ByteArrayInputStream(sheetBytes), sharedStrings)
        )
    }

    fun parseRowsFromMatrix(matrix: List<List<String>>): List<RawPaymentRow> {
        if (matrix.isEmpty()) return emptyList()

        val headerIndex = matrix.indexOfFirst { row ->
            val headers = row.map(::normalizeHeader)
            headers.any { it == "room" || it == "roman" || it.startsWith("room") } &&
                headers.any { it.contains("tenant") || it == "name" || it.endsWith("name") }
        }.takeIf { it >= 0 } ?: 0

        val headers = matrix.getOrNull(headerIndex).orEmpty().map(::normalizeHeader)
        fun findColumn(predicate: (String) -> Boolean): Int = headers.indexOfFirst(predicate)

        val colRoom = findColumn { it == "room" || it == "roman" || it.startsWith("room") }
        val colTenant = findColumn { it.contains("tenant") || it == "name" || it.endsWith("name") }
        val colRent = findColumn { it == "rent" || (it.contains("rent") && !it.contains("unpaid")) }
        val colReceipt = findColumn { it.contains("receipt") || it.contains("paymentdetails") || it == "details" }
        val colUnpaidPeriod = findColumn { it.contains("unpaidrent") || it.contains("unpaidperiod") }

        val results = mutableListOf<RawPaymentRow>()

        for (i in (headerIndex + 1) until matrix.size) {
            val row = matrix[i]
            fun cell(index: Int): String = if (index >= 0) row.getOrNull(index).orEmpty().trim() else ""

            val rawRoom = cell(colRoom)
            val rawTenant = cell(colTenant)
            val rawRent = cell(colRent)
            val rawReceipt = cell(colReceipt)
            val rawUnpaidPeriod = cell(colUnpaidPeriod)

            if (rawRoom.isBlank() && rawTenant.isBlank() && rawReceipt.isBlank()) continue

            val room = normalizeRoomNumber(rawRoom)
            val rent = normalizeRentValue(rawRent).takeIf { it > 0L }
            val entries = parseReceiptEntries(rawReceipt)

            if (entries.isNotEmpty()) {
                entries.forEach { details ->
                    results += RawPaymentRow(
                        rowNumber = i + 1,
                        roomDisplay = room,
                        tenantName = rawTenant.trim(),
                        paymentDateMillis = details.dateMillis,
                        amount = details.amount.takeIf { it > 0L },
                        receiptNumber = details.receiptNumber,
                        paymentMode = details.paymentMode,
                        paidFromMonth = details.fromMonth.takeIf { it.isNotBlank() },
                        paidToMonth = details.toMonth.takeIf { it.isNotBlank() },
                        sourceMonthlyRent = rent
                    )
                }
            } else {
                val period = parseUnpaidPeriod(rawUnpaidPeriod)
                results += RawPaymentRow(
                    rowNumber = i + 1,
                    roomDisplay = room,
                    tenantName = rawTenant.trim(),
                    paymentDateMillis = null,
                    amount = null,
                    receiptNumber = "",
                    paymentMode = "OTHER",
                    paidFromMonth = period.first.takeIf { it.isNotBlank() },
                    paidToMonth = period.second.takeIf { it.isNotBlank() },
                    sourceMonthlyRent = rent
                )
            }
        }

        return results
    }

    fun normalizeRoomNumber(raw: String): String {
        val s = raw.trim().uppercase(Locale.ROOT).replace("\u00A0", "").replace(" ", "")
        if (s.isBlank()) return ""

        if (s.contains("27")) {
            if (s.contains("(A)") || s.endsWith("A")) return "A-27(A)"
            if (s.contains("(B)") || s.endsWith("B")) return "A-27(B)"
        }

        val wing = s.firstOrNull { it == 'A' || it == 'B' }?.toString() ?: return s
        val digits = s.filter(Char::isDigit).toIntOrNull() ?: return s
        return "%s-%02d".format(Locale.US, wing, digits)
    }

    fun normalizeRentValue(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.replace(",", "").trim()
        return Regex("""\d+""").find(clean)?.value?.toLongOrNull() ?: 0L
    }

    fun parseReceiptEntries(text: String): List<ParsedReceiptDetails> {
        if (text.isBlank()) return emptyList()
        val matches = receiptDateRegex.findAll(text).toList()
        if (matches.isEmpty()) return listOf(parseReceiptDetails(text))

        return matches.mapIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val segment = text.substring(match.range.first, end).trim()
            parseReceiptDetails(segment)
        }
    }

    fun parseReceiptDetails(text: String): ParsedReceiptDetails {
        if (text.isBlank()) return ParsedReceiptDetails()

        val clean = text.trim()
        val dateMatch = receiptDateRegex.find(clean)
        val receiptNo = dateMatch?.groupValues?.get(1).orEmpty()
        val dateMillis = dateMatch?.let(::parseReceiptDate)
        val upper = clean.uppercase(Locale.ROOT)
        val mode = when {
            upper.contains("UPI") || upper.contains("GPAY") || upper.contains("G PAY") || upper.contains("PHONEPE") -> "UPI"
            upper.contains("CHEQUE") || upper.contains("CHEQ") || upper.contains("CHQ") -> "CHEQUE"
            upper.contains("BANK") || upper.contains("TRANSFER") || upper.contains("NEFT") || upper.contains("RTGS") -> "BANK_TRANSFER"
            upper.contains("CASH") -> "CASH"
            else -> "OTHER"
        }

        val amount = extractPaymentAmount(clean, dateMatch, receiptNo)
        val period = parseUnpaidPeriod(clean)

        return ParsedReceiptDetails(
            receiptNumber = receiptNo,
            dateMillis = dateMillis,
            amount = amount,
            fromMonth = period.first,
            toMonth = period.second,
            paymentMode = mode,
            isAmbiguous = dateMillis == null || amount <= 0L || period.first.isBlank(),
            rawText = clean
        )
    }

    private fun extractPaymentAmount(text: String, dateMatch: MatchResult?, receiptNo: String): Long {
        fun valid(value: Long?): Long? = value?.takeIf {
            it > 0L && it.toString() != receiptNo && it !in 1900L..2100L
        }

        Regex("""=\s*(\d{2,8})(?!\d)""")
            .find(text)
            ?.groupValues?.get(1)?.toLongOrNull()
            ?.let(::valid)
            ?.let { return it }

        Regex("""(?:₹|\bRS\.?\s*)\s*(\d{2,8})(?!\d)""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { valid(it.groupValues[1].toLongOrNull()) }
            .firstOrNull()
            ?.let { return it }

        val start = dateMatch?.range?.last?.plus(1) ?: 0
        return Regex("""(?<!\d)(\d{2,8})(?!\d)""")
            .findAll(text.substring(start))
            .mapNotNull { valid(it.groupValues[1].toLongOrNull()) }
            .lastOrNull() ?: 0L
    }

    private fun parseReceiptDate(match: MatchResult): Long? {
        val day = match.groupValues[2].filter(Char::isDigit).toIntOrNull() ?: return null
        val month = match.groupValues[3].filter(Char::isDigit).toIntOrNull() ?: return null
        val rawYear = match.groupValues[4].toIntOrNull() ?: return null
        val year = if (rawYear < 100) 2000 + rawYear else rawYear
        if (day !in 1..31 || month !in 1..12 || year !in 1900..2100) return null

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        return runCatching {
            fmt.parse("%04d-%02d-%02d".format(Locale.US, year, month, day))?.time
        }.getOrNull()
    }

    fun parseUnpaidPeriod(text: String): Pair<String, String> {
        if (text.isBlank()) return "" to ""

        val monthNames = mapOf(
            "jan" to 1, "january" to 1,
            "feb" to 2, "february" to 2,
            "mar" to 3, "march" to 3,
            "apr" to 4, "april" to 4,
            "may" to 5,
            "jun" to 6, "june" to 6,
            "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8,
            "sep" to 9, "sept" to 9, "supt" to 9, "september" to 9,
            "oct" to 10, "october" to 10,
            "nov" to 11, "november" to 11,
            "dec" to 12, "des" to 12, "december" to 12
        )

        val clean = text.lowercase(Locale.ROOT).replace(".", "").replace(",", "")
        val regex = Regex(
            """([a-z]{3,9})\s*(\d{2,4})?\s*(?:to|-)\s*([a-z]{3,9})\s*(\d{2,4})?"""
        )
        val match = regex.find(clean) ?: return "" to ""
        val m1 = monthNames[match.groupValues[1]] ?: return "" to ""
        val m2 = monthNames[match.groupValues[3]] ?: return "" to ""

        fun year(raw: String, fallback: String): Int {
            val value = raw.ifBlank { fallback }.toIntOrNull() ?: return java.time.Year.now().value
            return if (value < 100) 2000 + value else value
        }

        val y2 = year(match.groupValues[4], match.groupValues[2])
        val y1 = year(match.groupValues[2], match.groupValues[4])
        return "%04d-%02d".format(Locale.US, y1, m1) to
            "%04d-%02d".format(Locale.US, y2, m2)
    }

    private fun normalizeHeader(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("'", "")
        .replace(Regex("""[^a-z0-9]+"""), "")

    private fun readZipEntry(bytes: ByteArray, requested: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == requested) return zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun normalizeWorksheetTarget(target: String): String {
        val clean = target.removePrefix("/")
        return when {
            clean.startsWith("xl/") -> clean
            clean.startsWith("worksheets/") -> "xl/$clean"
            else -> "xl/$clean"
        }
    }

    private fun parseWorksheetXml(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val rows = mutableListOf<List<String>>()
        var current = sortedMapOf<Int, String>()
        var currentColumn = -1
        var cellType = ""
        var capture: String? = null
        var text = StringBuilder()
        var eventType = parser.eventType

        fun decode(value: String): String = when (cellType) {
            "s" -> value.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
            "b" -> if (value == "1") "TRUE" else "FALSE"
            else -> value
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> current = sortedMapOf()
                    "c" -> {
                        currentColumn = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                    }
                    "v", "t" -> if (currentColumn >= 0) {
                        capture = parser.name
                        text = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (capture != null) text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (capture != null && parser.name == capture) {
                        current[currentColumn] = decode(text.toString())
                        capture = null
                    } else when (parser.name) {
                        "c" -> {
                            currentColumn = -1
                            cellType = ""
                        }
                        "row" -> {
                            val max = current.keys.maxOrNull() ?: -1
                            rows += if (max < 0) emptyList() else List(max + 1) { current[it].orEmpty() }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter).uppercase(Locale.ROOT)
        if (letters.isBlank()) return -1
        var value = 0
        letters.forEach { value = value * 26 + (it - 'A' + 1) }
        return value - 1
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val strings = mutableListOf<String>()
        var inSi = false
        var inText = false
        var builder = StringBuilder()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; builder = StringBuilder() }
                    "t" -> if (inSi) inText = true
                }
                XmlPullParser.TEXT -> if (inSi && inText) builder.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> { strings += builder.toString(); inSi = false }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseWorkbookXml(stream: InputStream): List<String> =
        parseWorkbookXmlWithInfo(stream).map { it.name }

    private fun parseWorkbookXmlWithInfo(stream: InputStream): List<SheetInfo> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val sheets = mutableListOf<SheetInfo>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                val rId = parser.getAttributeValue(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                    "id"
                ) ?: parser.getAttributeValue(null, "r:id").orEmpty()
                if (name.isNotBlank()) sheets += SheetInfo(name, rId)
            }
            eventType = parser.next()
        }
        return sheets
    }

    private fun parseWorkbookRels(stream: InputStream): Map<String, String> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val map = mutableMapOf<String, String>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id").orEmpty()
                val target = parser.getAttributeValue(null, "Target").orEmpty()
                if (id.isNotBlank() && target.isNotBlank()) map[id] = target
            }
            eventType = parser.next()
        }
        return map
    }
}
