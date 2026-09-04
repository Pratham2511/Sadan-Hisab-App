package com.pansare.sadan.util

import android.util.Xml
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.domain.RawPaymentRow
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class SheetInfo(val name: String, val rId: String)

data class RawSheetData(
    val sheetName: String,
    val rows: List<List<String>>
)

data class ParsedReceiptDetails(
    val receiptNumber: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val amount: Long = 0L,
    val fromMonth: String = "",
    val toMonth: String = "",
    val paymentMode: String = "CASH",
    val isAmbiguous: Boolean = false,
    val rawText: String = ""
)

object XlsxImporter {

    /**
     * Lists all sheet names in an XLSX workbook.
     */
    fun listSheets(inputStream: InputStream): List<String> {
        val sheets = mutableListOf<String>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/workbook.xml") {
                    sheets.addAll(parseWorkbookXml(zip))
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return sheets
    }

    /**
     * Parses a specific sheet by name (or the first sheet if name is null) from an XLSX workbook stream.
     */
    fun parseSheet(inputStream: InputStream, targetSheetName: String? = null): RawSheetData {
        var sharedStrings = listOf<String>()
        var targetEntryName = "xl/worksheets/sheet1.xml"
        val sheetMap = mutableMapOf<String, String>() // sheetName -> sheetEntryName
        val rIdToName = mutableMapOf<String, String>() // rId -> sheetName
        val rIdToTarget = mutableMapOf<String, String>() // rId -> targetFilename

        // First pass: locate sharedStrings, workbook.xml, and rels
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStrings(zip)
                    }
                    "xl/workbook.xml" -> {
                        val sheets = parseWorkbookXmlWithInfo(zip)
                        sheets.forEach { rIdToName[it.rId] = it.name }
                    }
                    "xl/_rels/workbook.xml.rels" -> {
                        rIdToTarget.putAll(parseWorkbookRels(zip))
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // Match sheet name to zip entry path
        rIdToName.forEach { (rId, name) ->
            val target = rIdToTarget[rId] ?: ""
            val entryPath = if (target.startsWith("worksheets/")) "xl/$target" else if (target.startsWith("/xl/")) target.substring(1) else "xl/worksheets/$target"
            sheetMap[name] = entryPath
        }

        val chosenSheetName = targetSheetName?.takeIf { sheetMap.containsKey(it) }
            ?: sheetMap.keys.firstOrNull()
            ?: "Sheet1"

        targetEntryName = sheetMap[chosenSheetName] ?: "xl/worksheets/sheet1.xml"

        // Second pass: read target sheet rows
        var parsedRows = listOf<List<String>>()
        // Re-read stream from caller if needed or stream sequentially
        return RawSheetData(chosenSheetName, parsedRows)
    }

    /**
     * Parses raw matrix rows from a sheet into RawPaymentRow list.
     */
    fun parseRowsFromMatrix(matrix: List<List<String>>): List<RawPaymentRow> {
        if (matrix.isEmpty()) return emptyList()

        // Find header row index
        var headerIndex = -1
        var colRoom = -1
        var colTenant = -1
        var colRent = -1
        var colReceipt = -1
        var colUnpaidPeriod = -1
        var colUnpaidMonths = -1
        var colTotalAmount = -1

        for (i in matrix.indices) {
            val row = matrix[i]
            for (j in row.indices) {
                val cell = row[j].trim().lowercase()
                if (cell.contains("roman") || cell.contains("room")) colRoom = j
                if (cell.contains("tenant") || cell.contains("name")) colTenant = j
                if (cell.contains("rent") && !cell.contains("unpaid")) colRent = j
                if (cell.contains("receipt") || cell.contains("details")) colReceipt = j
                if (cell.contains("unpaid rent") || cell.contains("unpaid period")) colUnpaidPeriod = j
                if (cell.contains("unpaid months")) colUnpaidMonths = j
                if (cell.contains("total")) colTotalAmount = j
            }
            if (colTenant != -1 || colReceipt != -1 || colRoom != -1) {
                headerIndex = i
                break
            }
        }

        if (headerIndex == -1) headerIndex = 0

        val results = mutableListOf<RawPaymentRow>()
        val defaultMonth = MonthKey.current()

        for (i in (headerIndex + 1) until matrix.size) {
            val row = matrix[i]
            if (row.all { it.isBlank() }) continue

            val rawRoom = if (colRoom >= 0 && colRoom < row.size) row[colRoom] else ""
            val rawTenant = if (colTenant >= 0 && colTenant < row.size) row[colTenant] else ""
            val rawRent = if (colRent >= 0 && colRent < row.size) row[colRent] else ""
            val rawReceipt = if (colReceipt >= 0 && colReceipt < row.size) row[colReceipt] else ""
            val rawUnpaidPeriod = if (colUnpaidPeriod >= 0 && colUnpaidPeriod < row.size) row[colUnpaidPeriod] else ""

            if (rawTenant.isBlank() && rawRoom.isBlank() && rawReceipt.isBlank()) continue

            val normRoom = normalizeRoomNumber(rawRoom)
            val normRent = normalizeRentValue(rawRent)
            val receiptDetails = parseReceiptDetails(rawReceipt)
            val unpaidPeriod = parseUnpaidPeriod(rawUnpaidPeriod)

            val fromMonth = receiptDetails.fromMonth.ifBlank { unpaidPeriod.first.ifBlank { defaultMonth } }
            val toMonth = receiptDetails.toMonth.ifBlank { unpaidPeriod.second.ifBlank { fromMonth } }

            results += RawPaymentRow(
                rowNumber = i + 1,
                roomDisplay = normRoom,
                tenantName = rawTenant.trim(),
                amount = receiptDetails.amount.takeIf { it > 0 } ?: normRent,
                paymentDateMillis = receiptDetails.dateMillis,
                receiptNumber = receiptDetails.receiptNumber,
                paymentMode = receiptDetails.paymentMode,
                paidFromMonth = fromMonth,
                paidToMonth = toMonth,
                rawNote = listOf(rawReceipt, rawUnpaidPeriod).filter { it.isNotBlank() }.joinToString(" | ")
            )
        }

        return results
    }

    /**
     * Normalizes messy room numbers e.g. "B1" -> "B-01", "B-01" -> "B-01", "B20" -> "B-20", "A27A" -> "A-27(A)"
     */
    fun normalizeRoomNumber(raw: String): String {
        val s = raw.trim().uppercase().replace(" ", "")
        if (s.isBlank()) return ""

        // Handle "A-27(A)", "A-27(B)", "A27A", "A27B"
        if (s.contains("27")) {
            if (s.contains("(A)") || s.endsWith("A")) return "A-27(A)"
            if (s.contains("(B)") || s.endsWith("B")) return "A-27(B)"
        }

        val wing = s.firstOrNull { it == 'A' || it == 'B' }?.toString() ?: "B"
        val digits = s.filter { it.isDigit() }.toIntOrNull() ?: return s
        return "%s-%02d".format(wing, digits)
    }

    /**
     * Normalizes rent cell e.g. "400", "400 Renter", "Rs 400" -> 400
     */
    fun normalizeRentValue(raw: String): Long {
        if (raw.isBlank()) return 0L
        val clean = raw.replace(",", "").trim()
        val match = Regex("""\d+""").find(clean)
        return match?.value?.toLongOrNull() ?: 0L
    }

    /**
     * Parses free-form receipt strings e.g. "302/29/09 400 jan 26 to july 26"
     */
    fun parseReceiptDetails(text: String): ParsedReceiptDetails {
        if (text.isBlank()) return ParsedReceiptDetails()

        val clean = text.trim()
        var receiptNo = ""
        var dateMillis = System.currentTimeMillis()
        var amount = 0L
        var mode = "CASH"
        var fromMonth = ""
        var toMonth = ""
        var isAmbiguous = false

        // Detect payment mode keywords
        val upper = clean.uppercase()
        if (upper.contains("UPI")) mode = "UPI"
        else if (upper.contains("CHEQUE") || upper.contains("CHQ")) mode = "CHEQUE"
        else if (upper.contains("BANK") || upper.contains("TRANSFER") || upper.contains("NEFT") || upper.contains("RTGS")) mode = "BANK_TRANSFER"
        else if (upper.contains("CASH")) mode = "CASH"

        // Extract receipt number & date e.g. "302/29/09" or "300210/15/10/25"
        val receiptSlashDateRegex = Regex("""(\d{3,10})/(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?""")
        val match = receiptSlashDateRegex.find(clean)

        if (match != null) {
            receiptNo = match.groupValues[1]
            val day = match.groupValues[2].toIntOrNull() ?: 1
            val month = match.groupValues[3].toIntOrNull() ?: 1
            val yearRaw = match.groupValues[4]
            val year = if (yearRaw.isBlank()) 2026 else if (yearRaw.length == 2) 2000 + yearRaw.toInt() else yearRaw.toInt()

            val dateStr = "%04d-%02d-%02d".format(year, month, day)
            val parsedDate = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) }.getOrNull()
            if (parsedDate != null) dateMillis = parsedDate.time
        } else {
            // Try standalone receipt number
            val numMatch = Regex("""\b\d{3,8}\b""").find(clean)
            if (numMatch != null) receiptNo = numMatch.value
        }

        // Extract amount if present in receipt string e.g. "₹400", "400", "Rs 500"
        val amountRegex = Regex("""(?:RS\.?|₹|\b)\s*(\d{3,6})\b""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.findAll(clean).firstOrNull { m ->
            val v = m.groupValues[1]
            v != receiptNo && v.toIntOrNull()?.let { it in 100..100000 } == true
        }
        if (amountMatch != null) {
            amount = amountMatch.groupValues[1].toLongOrNull() ?: 0L
        }

        // Extract month period e.g. "jan 26 to july 26" or "jan to mar 2026"
        val period = parseUnpaidPeriod(clean)
        fromMonth = period.first
        toMonth = period.second

        return ParsedReceiptDetails(
            receiptNumber = receiptNo,
            dateMillis = dateMillis,
            amount = amount,
            fromMonth = fromMonth,
            toMonth = toMonth,
            paymentMode = mode,
            isAmbiguous = isAmbiguous,
            rawText = clean
        )
    }

    /**
     * Parses month ranges like "jan 26 to july 26" or "jan-mar 2026"
     */
    fun parseUnpaidPeriod(text: String): Pair<String, String> {
        if (text.isBlank()) return "" to ""

        val monthNames = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "july" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10,
            "nov" to 11, "dec" to 12
        )

        val clean = text.lowercase().replace(".", "").replace(",", "")

        // Match patterns like "jan 26 to july 26" or "jan 2026 to jul 2026"
        val rangeRegex = Regex("""([a-z]{3,4})\s*(\d{2,4})?\s*(?:to|-)\s*([a-z]{3,4})\s*(\d{2,4})?""")
        val match = rangeRegex.find(clean)

        if (match != null) {
            val m1Str = match.groupValues[1]
            val y1Str = match.groupValues[2]
            val m2Str = match.groupValues[3]
            val y2Str = match.groupValues[4]

            val m1 = monthNames[m1Str] ?: 1
            val m2 = monthNames[m2Str] ?: m1

            val currentYear = 2026
            fun parseYear(y: String): Int {
                if (y.isBlank()) return currentYear
                val num = y.toIntOrNull() ?: return currentYear
                return if (num < 100) 2000 + num else num
            }

            val y2 = parseYear(y2Str.ifBlank { y1Str })
            val y1 = parseYear(y1Str.ifBlank { y2Str })

            val from = "%04d-%02d".format(y1, m1)
            val to = "%04d-%02d".format(y2, m2)
            return from to to
        }

        return "" to ""
    }

    // ── XML Parsing Helpers ──────────────────────────────────────────

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val strings = mutableListOf<String>()
        var eventType = parser.eventType
        var currentText = StringBuilder()
        var inText = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "t") {
                        inText = true
                        currentText.clear()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) currentText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (name == "t") {
                        inText = false
                        strings.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseWorkbookXml(stream: InputStream): List<String> {
        return parseWorkbookXmlWithInfo(stream).map { it.name }
    }

    private fun parseWorkbookXmlWithInfo(stream: InputStream): List<SheetInfo> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        val sheets = mutableListOf<SheetInfo>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: ""
                val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "r:id") ?: ""
                if (name.isNotBlank()) sheets.add(SheetInfo(name, rId))
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
                val rId = parser.getAttributeValue(null, "Id") ?: ""
                val target = parser.getAttributeValue(null, "Target") ?: ""
                if (rId.isNotBlank() && target.isNotBlank()) {
                    map[rId] = target
                }
            }
            eventType = parser.next()
        }
        return map
    }
}
