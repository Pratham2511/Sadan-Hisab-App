package com.pansare.sadan.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.pansare.sadan.data.RentRepository
import com.pansare.sadan.util.ReceiptData
import com.pansare.sadan.util.ReceiptLine
import com.pansare.sadan.util.ReceiptPdf
import com.pansare.sadan.util.ShareUtils
import kotlinx.coroutines.launch
import java.io.File

/** One-tap WhatsApp receipt sharing without adding a network dependency to the app. */
fun AppViewModel.shareReceiptOnWhatsApp(paymentId: Long) = viewModelScope.launch {
    runCatching {
        val payment = repo.findPayment(paymentId) ?: error("Payment not found")
        val tenant = repo.findTenant(payment.tenantId) ?: error("Tenant not found")
        val room = repo.findRoom(tenant.roomId) ?: error("Room not found")
        val allocs = repo.allocationsForPayment(paymentId)
        val summary = repo.summaryFor(tenant.id)

        val propertyName = repo.getSetting(RentRepository.KEY_PROPERTY_NAME)
            ?: RentRepository.DEFAULT_PROPERTY_NAME
        val propertyAddress = repo.getSetting(RentRepository.KEY_PROPERTY_ADDRESS)
            ?: RentRepository.DEFAULT_PROPERTY_ADDRESS

        val lines = allocs.map { allocation ->
            val ledger = repo.database().ledgerDao().findById(allocation.ledgerMonthId)
            ReceiptLine(
                month = ledger?.month.orEmpty(),
                rentDue = ledger?.rentDue ?: 0L,
                allocated = allocation.allocatedAmount
            )
        }

        val context = getApplication<Application>()
        val receiptDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val dest = File(
            receiptDir,
            "receipt_${payment.receiptNumber.replace('/', '_').ifBlank { payment.id.toString() }}.pdf"
        )
        val file = ReceiptPdf.create(
            dest,
            ReceiptData(
                propertyName = propertyName,
                propertyAddress = propertyAddress,
                receiptNumber = payment.receiptNumber,
                paymentDate = payment.paymentDate,
                roomNumber = room.displayRoomNumber,
                tenantName = tenant.tenantName,
                paidFromMonth = payment.paidFromMonth,
                paidToMonth = payment.paidToMonth,
                monthsCovered = lines.size,
                amount = payment.amountPaid,
                paymentMode = payment.paymentMode.label,
                remainingOutstanding = summary.totalOutstanding,
                allocations = lines,
                notes = payment.notes
            )
        )

        ShareUtils.sharePdfToWhatsApp(
            context = context,
            file = file,
            title = "Rent Receipt ${payment.receiptNumber}",
            mobileNumber = tenant.mobileNumber
        )
    }.onFailure {
        // Existing share flow already surfaces generation errors to the user.
        shareReceipt(paymentId)
    }
}
