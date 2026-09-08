package com.pansare.sadan.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** XLSX parsing tests use synthetic data only. */
class XlsxImportTest {

    @Test
    fun `room numbers are normalized into canonical display formats`() {
        assertEquals("B-01", XlsxImporter.normalizeRoomNumber("B1"))
        assertEquals("B-01", XlsxImporter.normalizeRoomNumber(" B-01"))
        assertEquals("B-20", XlsxImporter.normalizeRoomNumber("B20"))
        assertEquals("A-27(A)", XlsxImporter.normalizeRoomNumber("A27A"))
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
    fun `messy receipt string with full date amount and period is parsed`() {
        val details = XlsxImporter.parseReceiptDetails("302/29/09/2026 400 jan 26 to july 26")
        assertEquals("302", details.receiptNumber)
        assertEquals(400L, details.amount)
        assertEquals("2026-01", details.fromMonth)
        assertEquals("2026-07", details.toMonth)
    }

    @Test
    fun `year in receipt date is never mistaken for amount`() {
        val details = XlsxImporter.parseReceiptDetails("305/29/09/2016 Oct to Des 16 =1500")
        assertEquals("305", details.receiptNumber)
        assertEquals(1500L, details.amount)
        assertEquals("2016-10", details.fromMonth)
        assertEquals("2016-12", details.toMonth)
    }

    @Test
    fun `multiple receipts in one cell become multiple payment entries`() {
        val entries = XlsxImporter.parseReceiptEntries(
            "305/29/09/2016 Oct to Des 16 =1500, 370/08/02/2017 jan to feb 17=1000"
        )
        assertEquals(2, entries.size)
        assertEquals("305", entries[0].receiptNumber)
        assertEquals(1500L, entries[0].amount)
        assertEquals("370", entries[1].receiptNumber)
        assertEquals(1000L, entries[1].amount)
    }

    @Test
    fun `payment mode is extracted from receipt details string`() {
        val upiDetails = XlsxImporter.parseReceiptDetails("300210/15/10/2025 ₹500 UPI")
        assertEquals("300210", upiDetails.receiptNumber)
        assertEquals(500L, upiDetails.amount)
        assertEquals("UPI", upiDetails.paymentMode)

        val chequeDetails = XlsxImporter.parseReceiptDetails("400123/15/10/2025 ₹500 Cheque paid")
        assertEquals("CHEQUE", chequeDetails.paymentMode)
    }

    @Test
    fun `legacy month spelling is parsed into canonical range`() {
        val (from, to) = XlsxImporter.parseUnpaidPeriod("Aug to Supt 16")
        assertEquals("2016-08", from)
        assertEquals("2016-09", to)
    }

    @Test
    fun `matrix extraction matches headers dynamically and ignores totals footer`() {
        val matrix = listOf(
            listOf("Roman.", "Tenant's name", "Rent", "Receipt no. and date", "Unpaid Rent", "Unpaid months", "Total Amount"),
            listOf("B1", "Synthetic Tenant 1", "400", "301/15/08/2026 jan 26 to jun 26 =2400", "", "0", "0"),
            listOf("B20", "Synthetic Tenant 2", "500", "302/20/09/2026 jul 26 to sep 26 =1500", "", "0", "0"),
            listOf("", "", "", "", "", "9", "3900")
        )

        val rows = XlsxImporter.parseRowsFromMatrix(matrix)

        assertEquals(2, rows.size)
        assertEquals("B-01", rows[0].roomDisplay)
        assertEquals("Synthetic Tenant 1", rows[0].tenantName)
        assertEquals(2400L, rows[0].amount)
        assertEquals("301", rows[0].receiptNumber)
        assertEquals("2026-01", rows[0].paidFromMonth)
        assertEquals(400L, rows[0].sourceMonthlyRent)

        assertEquals("B-20", rows[1].roomDisplay)
        assertEquals(1500L, rows[1].amount)
        assertEquals("302", rows[1].receiptNumber)
    }
}
