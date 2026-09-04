package com.pansare.sadan.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for XLSX parsing, header detection, messy receipt string extraction,
 * and room/rent normalization using synthetic data only.
 */
class XlsxImportTest {

    @Test
    fun `room numbers are normalized into canonical display formats`() {
        assertEquals("B-01", XlsxImporter.normalizeRoomNumber("B1"))
        assertEquals("B-01", XlsxImporter.normalizeRoomNumber("B-01"))
        assertEquals("B-20", XlsxImporter.normalizeRoomNumber("B20"))
        assertEquals("B-20", XlsxImporter.normalizeRoomNumber("B-20"))
        assertEquals("A-27(A)", XlsxImporter.normalizeRoomNumber("A27A"))
        assertEquals("A-27(A)", XlsxImporter.normalizeRoomNumber("A-27(A)"))
        assertEquals("A-27(B)", XlsxImporter.normalizeRoomNumber("A-27(B)"))
    }

    @Test
    fun `rent values with suffix words or symbols are extracted cleanly`() {
        assertEquals(400L, XlsxImporter.normalizeRentValue("400"))
        assertEquals(400L, XlsxImporter.normalizeRentValue("400 Renter"))
        assertEquals(5500L, XlsxImporter.normalizeRentValue("Rs. 5500"))
        assertEquals(6000L, XlsxImporter.normalizeRentValue("₹6,000"))
    }

    @Test
    fun `messy receipt string with date amount and period is parsed`() {
        val details = XlsxImporter.parseReceiptDetails("302/29/09 400 jan 26 to july 26")
        assertEquals("302", details.receiptNumber)
        assertEquals(400L, details.amount)
        assertEquals("2026-01", details.fromMonth)
        assertEquals("2026-07", details.toMonth)
    }

    @Test
    fun `payment mode is extracted from receipt details string`() {
        val upiDetails = XlsxImporter.parseReceiptDetails("300210/15/10/25 ₹500 UPI")
        assertEquals("300210", upiDetails.receiptNumber)
        assertEquals(500L, upiDetails.amount)
        assertEquals("UPI", upiDetails.paymentMode)

        val chequeDetails = XlsxImporter.parseReceiptDetails("Chq 400123 / 500 / Cheque paid")
        assertEquals("CHEQUE", chequeDetails.paymentMode)
    }

    @Test
    fun `free form unpaid period string is parsed into canonical yyyy-MM range`() {
        val (from, to) = XlsxImporter.parseUnpaidPeriod("jan 26 to july 26")
        assertEquals("2026-01", from)
        assertEquals("2026-07", to)
    }

    @Test
    fun `matrix row extraction matches headers dynamically`() {
        val matrix = listOf(
            listOf("Roman.", "Tenant's name", "Rent", "Receipt no. and date", "Unpaid Rent", "Unpaid months", "Total Amount"),
            listOf("B1", "Synthetic Tenant 1", "400", "301/15/08 400 jan 26 to jun 26", "jan 26 to jun 26", "6", "2400"),
            listOf("B20", "Synthetic Tenant 2", "500", "302/20/09 500 jul 26 to sep 26", "", "3", "1500")
        )

        val rows = XlsxImporter.parseRowsFromMatrix(matrix)

        assertEquals(2, rows.size)
        assertEquals("B-01", rows[0].roomDisplay)
        assertEquals("Synthetic Tenant 1", rows[0].tenantName)
        assertEquals(400L, rows[0].amount)
        assertEquals("301", rows[0].receiptNumber)
        assertEquals("2026-01", rows[0].paidFromMonth)

        assertEquals("B-20", rows[1].roomDisplay)
        assertEquals("Synthetic Tenant 2", rows[1].tenantName)
        assertEquals(500L, rows[1].amount)
        assertEquals("302", rows[1].receiptNumber)
    }
}
