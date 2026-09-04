package com.pansare.sadan.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReceiptPdfTest {

    @Test
    fun `formats single month receipt data cleanly without exception`() {
        val data = ReceiptData(
            propertyName = "Pansare Sadan",
            propertyAddress = "Sakinaka, Mohili Village",
            receiptNumber = "REC-101",
            paymentDate = 1700000000000L,
            roomNumber = "B-01",
            tenantName = "Synthetic Tenant A",
            paidFromMonth = "2026-09",
            paidToMonth = "2026-09",
            monthsCovered = 1,
            amount = 5000L,
            paymentMode = "UPI",
            remainingOutstanding = 0L,
            allocations = listOf(ReceiptLine("2026-09", 5000L, 5000L))
        )

        assertEquals("Sep 2026", DateUtils.formatMonth(data.paidFromMonth))
        assertEquals("Sep 2026", DateUtils.formatMonth(data.paidToMonth))
        assertEquals("₹5,000", CurrencyUtils.format(data.amount))
    }

    @Test
    fun `formats multi month advance receipt data cleanly`() {
        val data = ReceiptData(
            propertyName = "Pansare Sadan",
            propertyAddress = "Sakinaka, Mohili Village",
            receiptNumber = "REC-102",
            paymentDate = 1700000000000L,
            roomNumber = "A-12",
            tenantName = "Synthetic Tenant B",
            paidFromMonth = "2026-09",
            paidToMonth = "2026-11",
            monthsCovered = 3,
            amount = 15000L,
            paymentMode = "CASH",
            remainingOutstanding = 0L,
            allocations = listOf(
                ReceiptLine("2026-09", 5000L, 5000L),
                ReceiptLine("2026-10", 5000L, 5000L),
                ReceiptLine("2026-11", 5000L, 5000L)
            )
        )

        assertEquals("Sep 2026", DateUtils.formatMonth(data.paidFromMonth))
        assertEquals("Nov 2026", DateUtils.formatMonth(data.paidToMonth))
        assertEquals(3, data.allocations.size)
        assertEquals("Sep 2026", DateUtils.formatMonth(data.allocations[0].month))
        assertEquals("Oct 2026", DateUtils.formatMonth(data.allocations[1].month))
        assertEquals("Nov 2026", DateUtils.formatMonth(data.allocations[2].month))
    }

    @Test
    fun `formats historical and human readable month strings cleanly`() {
        val data = ReceiptData(
            propertyName = "Pansare Sadan",
            propertyAddress = "Sakinaka, Mohili Village",
            receiptNumber = "REC-103",
            paymentDate = 1700000000000L,
            roomNumber = "B-05",
            tenantName = "Synthetic Tenant C",
            paidFromMonth = "September 2026",
            paidToMonth = "Sep-26",
            monthsCovered = 1,
            amount = 6000L,
            paymentMode = "CHEQUE",
            remainingOutstanding = 0L,
            allocations = listOf(ReceiptLine("Sep 2026", 6000L, 6000L))
        )

        assertEquals("Sep 2026", DateUtils.formatMonth(data.paidFromMonth))
        assertEquals("Sep 2026", DateUtils.formatMonth(data.paidToMonth))
    }
}
