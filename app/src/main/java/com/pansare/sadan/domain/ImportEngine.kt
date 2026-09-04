package com.pansare.sadan.domain

/**
 * Validation and reconciliation for imported historical data.
 *
 * Design rules that this file enforces:
 *  - No row is ever silently skipped. Every input row leaves as VALID, REVIEW or REJECTED.
 *  - Imported "outstanding" figures are reconciliation references, never the source of truth.
 *  - Missing historical rent is reported as missing, never back-filled with today's rent.
 *  - Duplicate detection uses a composite fingerprint, not the receipt number alone.
 */

/** What the importer decided about a single row. */
enum class RowOutcome { VALID, REVIEW, REJECTED }

/** Why a row needs attention. Kept structured so the UI can group and explain issues. */
enum class IssueKind {
    MISSING_REQUIRED_FIELD,
    MALFORMED_VALUE,
    AMBIGUOUS_PERIOD,
    UNKNOWN_HISTORICAL_RENT,
    DUPLICATE_PAYMENT,
    RECONCILIATION_MISMATCH,
    SOURCE_DATA_INCONSISTENCY
}

data class ImportIssue(
    val kind: IssueKind,
    val message: String,
    val rowNumber: Int,
    val reference: String = ""
)

/** One raw payment row as read from a spreadsheet, before any interpretation. */
data class RawPaymentRow(
    val rowNumber: Int,
    val roomDisplay: String?,
    val tenantName: String?,
    val paymentDateMillis: Long?,
    val amount: Long?,
    val receiptNumber: String?,
    val paymentMode: String?,
    val paidFromMonth: String?,
    val paidToMonth: String?
)

/** A row that passed validation and is safe to commit. */
data class ValidatedPaymentRow(
    val rowNumber: Int,
    val roomDisplay: String,
    val paymentDateMillis: Long,
    val amount: Long,
    val receiptNumber: String,
    val paymentMode: String,
    val paidFromMonth: String,
    val paidToMonth: String,
    val fingerprint: String
)

data class ImportResult(
    val valid: List<ValidatedPaymentRow>,
    val review: List<ImportIssue>,
    val rejected: List<ImportIssue>
) {
    val importedCount: Int get() = valid.size
    val reviewCount: Int get() = review.size
    val rejectedCount: Int get() = rejected.size

    /** Human summary shown after an import, per the "no silent failure" rule. */
    fun summary(): String =
        "Imported successfully: $importedCount · Needs review: $reviewCount · Rejected: $rejectedCount"
}

/** Outcome of comparing a source document's outstanding figure against our own maths. */
data class Reconciliation(
    val reference: String,
    val importedOutstanding: Long,
    val calculatedOutstanding: Long,
    val kind: IssueKind?,
    val explanation: String
) {
    val matches: Boolean get() = kind == null
    val difference: Long get() = importedOutstanding - calculatedOutstanding
}

object ImportEngine {

    /**
     * A payment's identity for duplicate purposes. Receipt numbers are frequently missing
     * in historical records, so the fingerprint always includes tenant, date, amount, mode
     * and period. Two genuinely repeated payments differ by receipt number or period and
     * therefore survive; a re-run of the same file collides and is caught.
     */
    fun fingerprint(
        roomDisplay: String,
        dateMillis: Long,
        amount: Long,
        receiptNumber: String,
        mode: String,
        from: String,
        to: String
    ): String = listOf(
        roomDisplay.trim().uppercase(),
        dateMillis.toString(),
        amount.toString(),
        receiptNumber.trim().uppercase(),
        mode.trim().uppercase(),
        from,
        to
    ).joinToString("|")

    /**
     * Validates every row. [existingFingerprints] are the payments already in the database,
     * so re-importing the same file produces duplicates flagged for review rather than
     * double-counted money.
     */
    fun validate(
        rows: List<RawPaymentRow>,
        knownRooms: Set<String>,
        existingFingerprints: Set<String> = emptySet()
    ): ImportResult {
        val valid = mutableListOf<ValidatedPaymentRow>()
        val review = mutableListOf<ImportIssue>()
        val rejected = mutableListOf<ImportIssue>()
        val seen = existingFingerprints.toMutableSet()
        val knownUpper = knownRooms.map { it.uppercase() }.toSet()

        for (row in rows) {
            val ref = row.roomDisplay?.trim().orEmpty().ifBlank { "row ${row.rowNumber}" }

            val room = row.roomDisplay?.trim()
            if (room.isNullOrBlank()) {
                rejected += ImportIssue(
                    IssueKind.MISSING_REQUIRED_FIELD,
                    "Room is missing, so this payment cannot be attached to a tenant.",
                    row.rowNumber, ref
                )
                continue
            }
            if (room.uppercase() !in knownUpper) {
                rejected += ImportIssue(
                    IssueKind.MALFORMED_VALUE,
                    "Room \"$room\" is not part of the property's 48 rooms.",
                    row.rowNumber, ref
                )
                continue
            }

            val amount = row.amount
            if (amount == null) {
                rejected += ImportIssue(
                    IssueKind.MISSING_REQUIRED_FIELD,
                    "Payment amount is missing.", row.rowNumber, ref
                )
                continue
            }
            if (amount <= 0L) {
                rejected += ImportIssue(
                    IssueKind.MALFORMED_VALUE,
                    "Payment amount must be greater than zero (found $amount).",
                    row.rowNumber, ref
                )
                continue
            }

            val date = row.paymentDateMillis
            if (date == null) {
                rejected += ImportIssue(
                    IssueKind.MISSING_REQUIRED_FIELD,
                    "Payment date is missing.", row.rowNumber, ref
                )
                continue
            }

            val from = row.paidFromMonth?.trim()
            val to = row.paidToMonth?.trim()
            if (from.isNullOrBlank() || to.isNullOrBlank()) {
                review += ImportIssue(
                    IssueKind.AMBIGUOUS_PERIOD,
                    "The period this payment covers is not stated. Assign it manually before it affects the ledger.",
                    row.rowNumber, ref
                )
                continue
            }
            if (!MonthKey.isValid(from) || !MonthKey.isValid(to)) {
                rejected += ImportIssue(
                    IssueKind.MALFORMED_VALUE,
                    "Period \"$from\" to \"$to\" is not in yyyy-MM format.",
                    row.rowNumber, ref
                )
                continue
            }
            if (from > to) {
                rejected += ImportIssue(
                    IssueKind.MALFORMED_VALUE,
                    "Period start $from is after period end $to.",
                    row.rowNumber, ref
                )
                continue
            }

            val receipt = row.receiptNumber?.trim().orEmpty()
            val mode = row.paymentMode?.trim().orEmpty().ifBlank { "OTHER" }
            val print = fingerprint(room, date, amount, receipt, mode, from, to)

            if (print in seen) {
                review += ImportIssue(
                    IssueKind.DUPLICATE_PAYMENT,
                    "This payment looks identical to one already recorded and was not imported again.",
                    row.rowNumber, ref
                )
                continue
            }
            seen += print

            valid += ValidatedPaymentRow(
                rowNumber = row.rowNumber,
                roomDisplay = room,
                paymentDateMillis = date,
                amount = amount,
                receiptNumber = receipt,
                paymentMode = mode,
                paidFromMonth = from,
                paidToMonth = to,
                fingerprint = print
            )
        }

        return ImportResult(valid, review, rejected)
    }

    /**
     * Compares an outstanding figure taken from a source document against the figure our
     * ledger calculates, and classifies any difference. Crucially it distinguishes a real
     * accounting mismatch from simply not knowing the historical rent — the second must
     * never be reported to the user as a payment error.
     */
    fun reconcile(
        reference: String,
        importedOutstanding: Long,
        calculatedOutstanding: Long,
        hasUnresolvedHistory: Boolean,
        sourceSelfConsistent: Boolean = true
    ): Reconciliation {
        val kind = when {
            importedOutstanding == calculatedOutstanding -> null
            !sourceSelfConsistent -> IssueKind.SOURCE_DATA_INCONSISTENCY
            hasUnresolvedHistory -> IssueKind.UNKNOWN_HISTORICAL_RENT
            else -> IssueKind.RECONCILIATION_MISMATCH
        }

        val explanation = when (kind) {
            null ->
                "The source figure agrees with the calculated ledger."
            IssueKind.SOURCE_DATA_INCONSISTENCY ->
                "The source document contradicts itself, so neither figure can be treated as authoritative. " +
                    "Both values are retained for review."
            IssueKind.UNKNOWN_HISTORICAL_RENT ->
                "The difference cannot be attributed to a payment error: the rent for one or more historical " +
                    "months is unknown, so the calculated total is incomplete rather than wrong."
            else ->
                "The ledger and the source figure genuinely disagree. The ledger remains authoritative; " +
                    "review the payment history for this tenant."
        }

        return Reconciliation(reference, importedOutstanding, calculatedOutstanding, kind, explanation)
    }

    /**
     * Checks a source document's own arithmetic (rent x months == stated outstanding).
     * A mismatch is reported, never quietly corrected.
     */
    fun checkSourceArithmetic(
        reference: String,
        statedRent: Long,
        statedMonths: Int,
        statedOutstanding: Long
    ): ImportIssue? {
        val product = statedRent * statedMonths
        if (product == statedOutstanding) return null
        return ImportIssue(
            IssueKind.SOURCE_DATA_INCONSISTENCY,
            "Source states rent ₹$statedRent x $statedMonths months = ₹$statedOutstanding, but that multiplies to ₹$product. " +
                "Recorded for review; no value was assumed.",
            0, reference
        )
    }

    /**
     * Verifies that a stated month count matches the inclusive span of the stated dates.
     */
    fun checkMonthCount(reference: String, from: String, to: String, statedMonths: Int): ImportIssue? {
        if (!MonthKey.isValid(from) || !MonthKey.isValid(to) || from > to) return null
        val actual = MonthKey.monthsBetween(from, to)
        if (actual == statedMonths) return null
        return ImportIssue(
            IssueKind.SOURCE_DATA_INCONSISTENCY,
            "Source states $statedMonths months for $from to $to, but that range inclusively spans $actual months.",
            0, reference
        )
    }
}
