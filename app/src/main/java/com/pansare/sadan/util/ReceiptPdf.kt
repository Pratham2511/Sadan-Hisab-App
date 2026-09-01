package com.pansare.sadan.util

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.pansare.sadan.data.PaymentEntity
import java.io.File
import java.io.FileOutputStream

data class ReceiptDetails(
    val tenantName: String,
    val roomNumber: String,
    val previousOutstanding: Long,
    val remainingOutstanding: Long,
    val allocations: List<AllocationLine> = emptyList()
)

data class AllocationLine(
    val month: String,
    val rent: Long,
    val allocated: Long
)

/**
 * Creates a professional PDF rent receipt ready for sharing via FileProvider.
 */
object ReceiptPdf {

    fun create(destination: File, payment: PaymentEntity, details: ReceiptDetails): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        var y = 60f
        fun line(text: String, size: Float = 13f, bold: Boolean = false, indent: Float = 48f): Float {
            paint.textSize = size
            paint.isFakeBoldText = bold
            canvas.drawText(text, indent, y, paint)
            y += size + 6f
            return y
        }

        fun separator() {
            paint.strokeWidth = 0.5f
            canvas.drawLine(48f, y, 547f, y, paint)
            y += 12f
        }

        // Header
        line("PANSARE SADAN", 22f, true)
        line("Sakinaka, Mohili Village", 12f)
        line("Mumbai, Maharashtra, India", 12f)
        y += 10f
        separator()

        // Title
        line("RENT RECEIPT", 18f, true)
        y += 6f

        // Receipt info
        line("Receipt No:  ${payment.receiptNumber}", 14f, true)
        line("Date:  ${DateUtils.formatDate(payment.paymentDate)}")
        y += 6f
        separator()

        // Tenant info
        line("Room:  ${details.roomNumber}", 14f, true)
        line("Tenant:  ${details.tenantName}")
        y += 6f

        // Payment details
        separator()
        line("Paid Period:  ${DateUtils.formatMonth(payment.paidFromMonth)} to ${DateUtils.formatMonth(payment.paidToMonth)}")
        line("Number of Months:  ${payment.numberOfMonths}")
        line("Payment Mode:  ${payment.paymentMode.name.replace('_', ' ')}")
        y += 6f

        // Amount
        line("Amount Paid:  ${CurrencyUtils.format(payment.amountPaid)}", 16f, true)
        y += 6f

        // Allocations
        if (details.allocations.isNotEmpty()) {
            separator()
            line("Allocation Details:", 13f, true)
            details.allocations.forEach { alloc ->
                line("  ${DateUtils.formatMonth(alloc.month)}  —  Rent: ${CurrencyUtils.format(alloc.rent)}  Allocated: ${CurrencyUtils.format(alloc.allocated)}", 11f)
            }
        }

        y += 6f
        separator()

        // Outstanding
        line("Previous Outstanding:  ${CurrencyUtils.format(details.previousOutstanding)}")
        line("Remaining Outstanding:  ${CurrencyUtils.format(details.remainingOutstanding)}", 14f, true)

        if (payment.notes.isNotBlank()) {
            y += 8f
            line("Notes:  ${payment.notes}", 11f)
        }

        // Footer
        y = 780f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Computer-generated receipt — Pansare Sadan Rent Management", 48f, y, paint)

        document.finishPage(page)
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use(document::writeTo)
        document.close()
        return destination
    }
}
