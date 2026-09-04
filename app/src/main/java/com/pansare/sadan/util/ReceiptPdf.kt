package com.pansare.sadan.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** One month's share of a payment, printed on the receipt so allocation is transparent. */
data class ReceiptLine(val month: String, val rentDue: Long, val allocated: Long)

/** Everything printed on a receipt. Every field comes from the database. */
data class ReceiptData(
    val propertyName: String,
    val propertyAddress: String,
    val receiptNumber: String,
    val paymentDate: Long,
    val roomNumber: String,
    val tenantName: String,
    val paidFromMonth: String,
    val paidToMonth: String,
    val monthsCovered: Int,
    val amount: Long,
    val paymentMode: String,
    val remainingOutstanding: Long,
    val allocations: List<ReceiptLine> = emptyList(),
    val notes: String = ""
)

/** Renders an A4 rent receipt locally. No network, no external service. */
object ReceiptPdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 48f
    private const val RIGHT = 547f

    fun create(destination: File, data: ReceiptData): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        var y = 64f

        fun text(value: String, size: Float = 12f, bold: Boolean = false, x: Float = LEFT) {
            paint.textSize = size
            paint.isFakeBoldText = bold
            canvas.drawText(value, x, y, paint)
        }

        fun row(label: String, value: String, size: Float = 12f, bold: Boolean = false) {
            paint.textSize = size
            paint.isFakeBoldText = false
            canvas.drawText(label, LEFT, y, paint)
            paint.isFakeBoldText = bold
            canvas.drawText(value, LEFT + 170f, y, paint)
            y += size + 9f
        }

        fun rule() {
            paint.strokeWidth = 0.7f
            canvas.drawLine(LEFT, y, RIGHT, y, paint)
            y += 16f
        }

        // Header
        text(data.propertyName, 20f, true); y += 26f
        text(data.propertyAddress, 11f); y += 20f
        rule()

        text("RENT RECEIPT", 15f, true); y += 26f

        row("Receipt No.", data.receiptNumber, bold = true)
        row("Date", DateUtils.formatDate(data.paymentDate))
        y += 4f
        rule()

        row("Room", data.roomNumber, bold = true)
        row("Tenant", data.tenantName)
        y += 4f
        rule()

        row("Paid From", DateUtils.formatMonth(data.paidFromMonth))
        row("Paid To", DateUtils.formatMonth(data.paidToMonth))
        row("Months Covered", data.monthsCovered.toString())
        row("Payment Mode", data.paymentMode)
        y += 4f
        rule()

        row("Amount Paid", CurrencyUtils.format(data.amount), size = 15f, bold = true)
        y += 6f

        if (data.allocations.isNotEmpty()) {
            rule()
            text("Applied to", 12f, true); y += 20f
            paint.textSize = 11f
            paint.isFakeBoldText = false
            data.allocations.forEach { line ->
                canvas.drawText(DateUtils.formatMonth(line.month), LEFT + 8f, y, paint)
                canvas.drawText("Rent ${CurrencyUtils.format(line.rentDue)}", LEFT + 150f, y, paint)
                canvas.drawText("Applied ${CurrencyUtils.format(line.allocated)}", LEFT + 300f, y, paint)
                y += 17f
            }
            y += 6f
        }

        rule()
        row("Remaining Outstanding", CurrencyUtils.format(data.remainingOutstanding), size = 13f, bold = true)

        if (data.notes.isNotBlank()) {
            y += 8f
            row("Notes", data.notes, size = 11f)
        }

        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText(
            "Computer-generated receipt. Figures are taken from the property ledger.",
            LEFT, 790f, paint
        )

        document.finishPage(page)
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use(document::writeTo)
        document.close()
        return destination
    }
}
