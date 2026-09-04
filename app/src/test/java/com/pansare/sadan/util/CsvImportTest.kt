package com.pansare.sadan.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser must never invent a value. Anything it cannot read becomes null so the import
 * engine can reject the row with a specific reason.
 *
 * All data here is synthetic.
 */
class CsvImportTest {

    private val header = "Room,Name,Date,Amount,Receipt,Mode,From,To"

    @Test
    fun `a well formed file parses every data row`() {
        val csv = """
            $header
            A-01,Rahul Sharma,2025-01-05,600,PS0001,CASH,2025-01,2025-01
            B-04,Priya Patil,2025-02-11,1200,PS0002,UPI,2025-01,2025-02
        """.trimIndent()

        val rows = CsvImport.parse(csv)

        assertEquals(2, rows.size)
        assertEquals("A-01", rows[0].roomDisplay)
        assertEquals(600L, rows[0].amount)
        assertEquals("2025-01", rows[0].paidFromMonth)
        assertEquals(1200L, rows[1].amount)
        assertEquals("2025-02", rows[1].paidToMonth)
    }

    @Test
    fun `row numbers match the spreadsheet line the user sees`() {
        val csv = "$header\nA-01,Rahul Sharma,2025-01-05,600,PS0001,CASH,2025-01,2025-01"
        // Header is line 1, so the first data row must report 2.
        assertEquals(2, CsvImport.parse(csv).single().rowNumber)
    }

    @Test
    fun `missing cells become null rather than a guess`() {
        val csv = "$header\nA-01,,,,,,,"
        val row = CsvImport.parse(csv).single()

        assertEquals("A-01", row.roomDisplay)
        assertNull(row.amount)
        assertNull(row.paymentDateMillis)
        assertNull(row.paidFromMonth)
        assertNull(row.paidToMonth)
    }

    @Test
    fun `a short row does not crash and yields nulls for the absent columns`() {
        val csv = "$header\nA-02,Amit Kulkarni"
        val row = CsvImport.parse(csv).single()

        assertEquals("A-02", row.roomDisplay)
        assertNull(row.amount)
    }

    @Test
    fun `quoted cells containing commas stay intact`() {
        val cells = CsvImport.splitLine("""A-01,"Sharma, Rahul",600""")

        assertEquals(listOf("A-01", "Sharma, Rahul", "600"), cells)
    }

    @Test
    fun `doubled quotes inside a quoted cell are unescaped`() {
        assertEquals(listOf("""a"b""", "c"), CsvImport.splitLine(""""a""b",c"""))
    }

    @Test
    fun `amounts accept rupee symbols and separators but reject text`() {
        assertEquals(600L, CsvImport.parseAmount("600"))
        assertEquals(600L, CsvImport.parseAmount("₹600"))
        assertEquals(1200L, CsvImport.parseAmount("1,200"))
        assertEquals(600L, CsvImport.parseAmount("600.00"))
        assertNull(CsvImport.parseAmount("six hundred"))
        assertNull(CsvImport.parseAmount(""))
    }

    @Test
    fun `a fractional amount is rejected rather than rounded`() {
        // Rounding here would silently alter the books.
        assertNull(CsvImport.parseAmount("600.50"))
    }

    @Test
    fun `several date spellings are understood and nonsense is not`() {
        assertTrue(CsvImport.parseDate("2025-01-05") != null)
        assertTrue(CsvImport.parseDate("05-01-2025") != null)
        assertTrue(CsvImport.parseDate("05/01/2025") != null)
        assertNull(CsvImport.parseDate("not a date"))
        assertNull(CsvImport.parseDate("2025-13-45"))
    }

    @Test
    fun `months are normalised to the canonical form`() {
        assertEquals("2025-01", CsvImport.parseMonth("2025-01"))
        assertEquals("2025-01", CsvImport.parseMonth("01-2025"))
        assertEquals("2025-01", CsvImport.parseMonth("Jan-2025"))
        assertNull(CsvImport.parseMonth("sometime in 2025"))
    }

    @Test
    fun `headers are matched regardless of case spacing or underscores`() {
        val csv = "ROOM No,Payment_Date,Amount Paid\nA-01,2025-01-05,600"
        val row = CsvImport.parse(csv).single()

        assertEquals("A-01", row.roomDisplay)
        assertEquals(600L, row.amount)
        assertTrue(row.paymentDateMillis != null)
    }

    @Test
    fun `blank lines are ignored and do not become empty rows`() {
        val csv = "$header\n\nA-01,Rahul Sharma,2025-01-05,600,PS0001,CASH,2025-01,2025-01\n\n"
        assertEquals(1, CsvImport.parse(csv).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file without a room column is refused outright`() {
        CsvImport.parse("Name,Amount\nRahul Sharma,600")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty file is refused`() {
        CsvImport.parse("   \n\n")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a header with no data rows is refused`() {
        CsvImport.parse(header)
    }
}
