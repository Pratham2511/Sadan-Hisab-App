package com.pansare.sadan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import validation, duplicate detection and reconciliation tests.
 * All data is synthetic.
 */
class ImportEngineTest {

    private val rooms = setOf("A-01", "A-02", "B-01", "A-27(A)")

    private fun row(
        n: Int,
        room: String? = "A-01",
        amount: Long? = 600,
        date: Long? = 1_700_000_000_000,
        receipt: String? = "PS-2025-0001",
        mode: String? = "CASH",
        from: String? = "2025-01",
        to: String? = "2025-01"
    ) = RawPaymentRow(n, room, "Rahul Sharma", date, amount, receipt, mode, from, to)

    // ── 13-14. Valid, invalid and duplicate rows ──────────────────────

    @Test
    fun `a well formed row is imported`() {
        val result = ImportEngine.validate(listOf(row(1)), rooms)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.reviewCount)
        assertEquals(0, result.rejectedCount)
    }

    @Test
    fun `every input row is accounted for and none is silently dropped`() {
        val rows = listOf(
            row(1),
            row(2, amount = null),
            row(3, from = null, to = null),
            row(4, room = "Z-99"),
            row(5, amount = -100)
        )
        val result = ImportEngine.validate(rows, rooms)

        assertEquals(rows.size, result.importedCount + result.reviewCount + result.rejectedCount)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.reviewCount)
        assertEquals(3, result.rejectedCount)
    }

    @Test
    fun `missing room is rejected`() {
        val result = ImportEngine.validate(listOf(row(1, room = null)), rooms)
        assertEquals(1, result.rejectedCount)
        assertEquals(IssueKind.MISSING_REQUIRED_FIELD, result.rejected.single().kind)
    }

    @Test
    fun `unknown room is rejected`() {
        val result = ImportEngine.validate(listOf(row(1, room = "C-05")), rooms)
        assertEquals(IssueKind.MALFORMED_VALUE, result.rejected.single().kind)
    }

    @Test
    fun `zero or negative amount is rejected`() {
        val result = ImportEngine.validate(listOf(row(1, amount = 0), row(2, amount = -5)), rooms)
        assertEquals(2, result.rejectedCount)
    }

    @Test
    fun `missing date is rejected`() {
        val result = ImportEngine.validate(listOf(row(1, date = null)), rooms)
        assertEquals(IssueKind.MISSING_REQUIRED_FIELD, result.rejected.single().kind)
    }

    @Test
    fun `malformed period is rejected`() {
        val result = ImportEngine.validate(listOf(row(1, from = "Jan-2025", to = "2025-01")), rooms)
        assertEquals(IssueKind.MALFORMED_VALUE, result.rejected.single().kind)
    }

    @Test
    fun `reversed period is rejected`() {
        val result = ImportEngine.validate(listOf(row(1, from = "2025-06", to = "2025-01")), rooms)
        assertEquals(IssueKind.MALFORMED_VALUE, result.rejected.single().kind)
    }

    @Test
    fun `an unstated period goes to review rather than being guessed`() {
        val result = ImportEngine.validate(listOf(row(1, from = null, to = null)), rooms)
        assertEquals(1, result.reviewCount)
        assertEquals(IssueKind.AMBIGUOUS_PERIOD, result.review.single().kind)
    }

    // ── 13. Duplicate import protection ───────────────────────────────

    @Test
    fun `re-importing the same file does not duplicate payments`() {
        val first = ImportEngine.validate(listOf(row(1), row(2, receipt = "PS-2025-0002", from = "2025-02", to = "2025-02")), rooms)
        assertEquals(2, first.importedCount)

        val existing = first.valid.map { it.fingerprint }.toSet()
        val second = ImportEngine.validate(
            listOf(row(1), row(2, receipt = "PS-2025-0002", from = "2025-02", to = "2025-02")),
            rooms,
            existing
        )
        assertEquals(0, second.importedCount)
        assertEquals(2, second.reviewCount)
        assertTrue(second.review.all { it.kind == IssueKind.DUPLICATE_PAYMENT })
    }

    @Test
    fun `duplicates within a single file are caught`() {
        val result = ImportEngine.validate(listOf(row(1), row(2)), rooms)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.reviewCount)
    }

    @Test
    fun `legitimate repeat payments in the same month are not treated as duplicates`() {
        // Two genuine 200 instalments toward the same month, different receipts and dates.
        val a = row(1, amount = 200, receipt = "PS-2025-0010", date = 1_700_000_000_000)
        val b = row(2, amount = 200, receipt = "PS-2025-0011", date = 1_700_600_000_000)
        val result = ImportEngine.validate(listOf(a, b), rooms)
        assertEquals(2, result.importedCount)
        assertEquals(0, result.reviewCount)
    }

    @Test
    fun `duplicate detection does not rely on receipt number alone`() {
        // Receipt numbers absent in both rows, but they are otherwise different payments.
        val a = row(1, receipt = "", amount = 300, from = "2025-01", to = "2025-01")
        val b = row(2, receipt = "", amount = 300, from = "2025-02", to = "2025-02")
        val result = ImportEngine.validate(listOf(a, b), rooms)
        assertEquals(2, result.importedCount)

        // Identical in every field including the blank receipt -> duplicate.
        val c = row(3, receipt = "", amount = 300, from = "2025-01", to = "2025-01")
        val again = ImportEngine.validate(listOf(a, c), rooms)
        assertEquals(1, again.importedCount)
        assertEquals(1, again.reviewCount)
    }

    @Test
    fun `import summary reports all three buckets`() {
        val result = ImportEngine.validate(
            listOf(row(1), row(2, from = null, to = null), row(3, room = "Z-1")),
            rooms
        )
        val summary = result.summary()
        assertTrue(summary.contains("Imported successfully: 1"))
        assertTrue(summary.contains("Needs review: 1"))
        assertTrue(summary.contains("Rejected: 1"))
    }

    // ── 11. Reconciliation of imported outstanding figures ────────────

    @Test
    fun `matching figures reconcile cleanly`() {
        val r = ImportEngine.reconcile("A-01", 1200, 1200, hasUnresolvedHistory = false)
        assertTrue(r.matches)
        assertNull(r.kind)
        assertEquals(0L, r.difference)
    }

    @Test
    fun `a genuine disagreement is an accounting mismatch`() {
        val r = ImportEngine.reconcile("A-01", 1200, 900, hasUnresolvedHistory = false)
        assertEquals(IssueKind.RECONCILIATION_MISMATCH, r.kind)
        assertEquals(300L, r.difference)
    }

    @Test
    fun `missing historical rent is not mislabelled as a payment error`() {
        val r = ImportEngine.reconcile("A-01", 5000, 1200, hasUnresolvedHistory = true)
        assertEquals(IssueKind.UNKNOWN_HISTORICAL_RENT, r.kind)
        assertTrue(r.explanation.contains("cannot be attributed to a payment error"))
    }

    @Test
    fun `a self contradictory source is classified separately`() {
        val r = ImportEngine.reconcile(
            "A-01", 6650, 7600,
            hasUnresolvedHistory = false,
            sourceSelfConsistent = false
        )
        assertEquals(IssueKind.SOURCE_DATA_INCONSISTENCY, r.kind)
    }

    // ── 44. Source data arithmetic checks ─────────────────────────────

    @Test
    fun `consistent source arithmetic raises no issue`() {
        assertNull(ImportEngine.checkSourceArithmetic("A-01", 400, 19, 7600))
    }

    @Test
    fun `rent times months not matching the stated total is reported not corrected`() {
        val issue = ImportEngine.checkSourceArithmetic("A-01", 400, 19, 6650)
        assertNotNull(issue)
        assertEquals(IssueKind.SOURCE_DATA_INCONSISTENCY, issue!!.kind)
        assertTrue(issue.message.contains("no value was assumed"))
    }

    @Test
    fun `a month count that disagrees with the date range is reported`() {
        val issue = ImportEngine.checkMonthCount("A-01", "2002-01", "2026-07", 307)
        assertNotNull(issue)
        assertTrue(issue!!.message.contains("295"))
    }

    @Test
    fun `a correct month count raises no issue`() {
        assertNull(ImportEngine.checkMonthCount("A-01", "2025-01", "2025-03", 3))
    }
}
