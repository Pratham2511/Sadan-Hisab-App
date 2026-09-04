package com.pansare.sadan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accounting behaviour tests. All data here is synthetic.
 */
class AccountingEngineTest {

    private var nextId = 1L
    private fun month(m: String, rent: Long, certainty: RentCertainty = RentCertainty.KNOWN) =
        LedgerMonth(id = nextId++, month = m, rentDue = rent, certainty = certainty)

    private fun alloc(paymentId: Long, monthId: Long, amount: Long) =
        Allocation(paymentId, monthId, amount)

    // ── 1. New unpaid month ───────────────────────────────────────────

    @Test
    fun `a fresh month with no payment is UNPAID and fully outstanding`() {
        val jan = month("2025-01", 600)
        val state = LedgerEngine.computeStates(listOf(jan), emptyList()).single()

        assertEquals(LedgerStatusValue.UNPAID, state.status)
        assertEquals(0L, state.paid)
        assertEquals(600L, state.outstanding)
    }

    // ── 2-5. The partial payment sequence from the specification ──────

    @Test
    fun `600 rupee month settled by three 200 rupee payments`() {
        val jan = month("2025-01", 600)
        val months = listOf(jan)
        val allocations = mutableListOf<Allocation>()

        // Payment 1 — 200
        val plan1 = LedgerEngine.plan(LedgerEngine.computeStates(months, allocations), 200)
        allocations += plan1.lines.map { alloc(1, it.ledgerMonthId, it.amount) }
        var state = LedgerEngine.computeStates(months, allocations).single()
        assertEquals(LedgerStatusValue.PARTIALLY_PAID, state.status)
        assertEquals(400L, state.outstanding)

        // Payment 2 — another 200
        val plan2 = LedgerEngine.plan(LedgerEngine.computeStates(months, allocations), 200)
        allocations += plan2.lines.map { alloc(2, it.ledgerMonthId, it.amount) }
        state = LedgerEngine.computeStates(months, allocations).single()
        assertEquals(LedgerStatusValue.PARTIALLY_PAID, state.status)
        assertEquals(200L, state.outstanding)

        // Payment 3 — final 200
        val plan3 = LedgerEngine.plan(LedgerEngine.computeStates(months, allocations), 200)
        allocations += plan3.lines.map { alloc(3, it.ledgerMonthId, it.amount) }
        state = LedgerEngine.computeStates(months, allocations).single()
        assertEquals(LedgerStatusValue.PAID, state.status)
        assertEquals(0L, state.outstanding)
        assertEquals(600L, state.paid)
    }

    @Test
    fun `partial then exact remainder closes the month`() {
        val june = month("2025-06", 600)
        val months = listOf(june)
        val existing = listOf(alloc(1, june.id, 400))

        val plan = LedgerEngine.plan(LedgerEngine.computeStates(months, existing), 200)

        assertEquals(1, plan.monthsTouched)
        assertEquals("2025-06", plan.lines.single().month)
        assertEquals(LedgerStatusValue.PAID, plan.statesAfter.single().status)
        assertEquals(0L, plan.statesAfter.single().outstanding)
    }

    @Test
    fun `full payment in one go marks the month PAID`() {
        val jan = month("2025-01", 600)
        val plan = LedgerEngine.plan(LedgerEngine.computeStates(listOf(jan), emptyList()), 600)
        assertEquals(LedgerStatusValue.PAID, plan.statesAfter.single().status)
    }

    // ── 6. Multi-month payment ────────────────────────────────────────

    @Test
    fun `one payment spans several months oldest first`() {
        val jan = month("2025-01", 600)
        val feb = month("2025-02", 600)
        val mar = month("2025-03", 600)
        val months = listOf(jan, feb, mar)

        val plan = LedgerEngine.plan(LedgerEngine.computeStates(months, emptyList()), 1400)

        assertEquals(listOf("2025-01", "2025-02", "2025-03"), plan.lines.map { it.month })
        assertEquals(listOf(600L, 600L, 200L), plan.lines.map { it.amount })
        assertEquals(LedgerStatusValue.PARTIALLY_PAID, plan.statesAfter.last().status)
    }

    // ── 7. Payment gaps ───────────────────────────────────────────────

    @Test
    fun `gaps report only the months that actually carry a balance`() {
        val jan = month("2025-01", 600)
        val feb = month("2025-02", 600)
        val mar = month("2025-03", 600)
        val apr = month("2025-04", 600)
        val may = month("2025-05", 600)
        val jun = month("2025-06", 600)
        val months = listOf(jan, feb, mar, apr, may, jun)

        // Jan, Apr, May paid. Feb, Mar, Jun unpaid.
        val allocations = listOf(
            alloc(1, jan.id, 600),
            alloc(2, apr.id, 600),
            alloc(3, may.id, 600)
        )

        val summary = LedgerEngine.summarise(
            LedgerEngine.computeStates(months, allocations), "2025-06"
        )

        assertEquals(3, summary.unpaidMonths)
        assertEquals(1800L, summary.totalOutstanding)
        assertEquals("2025-02", summary.outstandingSince)
        assertEquals("2025-05", summary.lastPaidUpTo)

        // Two distinct runs: Feb–Mar and Jun. NOT one Jan–Jun block.
        assertEquals(2, summary.unpaidPeriods.size)
        assertEquals("2025-02", summary.unpaidPeriods[0].fromMonth)
        assertEquals("2025-03", summary.unpaidPeriods[0].toMonth)
        assertEquals(2, summary.unpaidPeriods[0].months)
        assertEquals("2025-06", summary.unpaidPeriods[1].fromMonth)
        assertEquals("2025-06", summary.unpaidPeriods[1].toMonth)
    }

    // ── 8. Historical rent changes ────────────────────────────────────

    @Test
    fun `each month uses the rent applicable to that month not the current rent`() {
        // 2024 rent was 300; 2025 rent is 400.
        val dec2024 = month("2024-12", 300)
        val jan2025 = month("2025-01", 400)
        val months = listOf(dec2024, jan2025)

        val states = LedgerEngine.computeStates(months, emptyList())
        assertEquals(300L, states[0].rentDue)
        assertEquals(400L, states[1].rentDue)

        // 300 fully settles the 2024 month, using 300 — not the current 400.
        val plan = LedgerEngine.plan(states, 300)
        assertEquals(1, plan.monthsTouched)
        assertEquals("2024-12", plan.lines.single().month)
        assertEquals(LedgerStatusValue.PAID, plan.statesAfter[0].status)
        assertEquals(LedgerStatusValue.UNPAID, plan.statesAfter[1].status)

        // Total owed is 700, never 2 x 400.
        assertEquals(700L, LedgerEngine.outstanding(states, "2025-01"))
    }

    @Test
    fun `rent resolution picks the change in force for the month`() {
        val changes = listOf(
            RentPeriod("2018-01", 200),
            RentPeriod("2021-01", 300),
            RentPeriod("2024-01", 500)
        )
        assertEquals(200L, RentResolver.rentFor(changes, "2019-06")?.amount)
        assertEquals(300L, RentResolver.rentFor(changes, "2021-01")?.amount)
        assertEquals(300L, RentResolver.rentFor(changes, "2023-12")?.amount)
        assertEquals(500L, RentResolver.rentFor(changes, "2025-09")?.amount)
        // Before any known rate — must not fabricate a value.
        assertNull(RentResolver.rentFor(changes, "2017-01"))
    }

    // ── 9-10. Payment edit and delete ─────────────────────────────────

    @Test
    fun `editing a payment down leaves no stale allocation`() {
        val mar = month("2025-03", 600)
        val months = listOf(mar)
        var allocations = listOf(alloc(7, mar.id, 600))

        assertEquals(LedgerStatusValue.PAID, LedgerEngine.computeStates(months, allocations).single().status)

        // Edit payment 7 from 600 down to 300.
        val plan = LedgerEngine.planForEdit(months, allocations, paymentIdBeingEdited = 7, amount = 300)
        allocations = allocations.filterNot { it.paymentId == 7L } +
            plan.lines.map { alloc(7, it.ledgerMonthId, it.amount) }

        val state = LedgerEngine.computeStates(months, allocations).single()
        assertEquals(LedgerStatusValue.PARTIALLY_PAID, state.status)
        assertEquals(600L, state.rentDue)
        assertEquals(300L, state.paid)
        assertEquals(300L, state.outstanding)
        assertEquals(1, allocations.count { it.paymentId == 7L })
    }

    @Test
    fun `deleting a payment returns the month to UNPAID`() {
        val mar = month("2025-03", 600)
        val months = listOf(mar)
        val allocations = listOf(alloc(7, mar.id, 600))

        val afterDelete = allocations.filterNot { it.paymentId == 7L }
        val state = LedgerEngine.computeStates(months, afterDelete).single()

        assertEquals(LedgerStatusValue.UNPAID, state.status)
        assertEquals(0L, state.paid)
        assertEquals(600L, state.outstanding)
    }

    // ── 11. Overpayment rejection ─────────────────────────────────────

    @Test
    fun `overpaying a month is rejected rather than silently absorbed`() {
        val jan = month("2025-01", 600)
        val states = LedgerEngine.computeStates(listOf(jan), emptyList())

        val error = runCatching { LedgerEngine.plan(states, 700) }.exceptionOrNull()
        assertTrue(error is AccountingException)
        assertTrue(error!!.message!!.contains("exceeds"))
    }

    @Test
    fun `zero and negative payments are rejected`() {
        val states = LedgerEngine.computeStates(listOf(month("2025-01", 600)), emptyList())
        assertTrue(runCatching { LedgerEngine.plan(states, 0) }.exceptionOrNull() is AccountingException)
        assertTrue(runCatching { LedgerEngine.plan(states, -50) }.exceptionOrNull() is AccountingException)
    }

    @Test
    fun `paying an already settled month is rejected`() {
        val jan = month("2025-01", 600)
        val states = LedgerEngine.computeStates(listOf(jan), listOf(alloc(1, jan.id, 600)))
        assertTrue(runCatching { LedgerEngine.plan(states, 100) }.exceptionOrNull() is AccountingException)
    }

    // ── 12. Allocation ordering determinism ───────────────────────────

    @Test
    fun `allocation order does not depend on input row order`() {
        val jan = month("2025-01", 600)
        val feb = month("2025-02", 600)
        val mar = month("2025-03", 600)

        val shuffled = listOf(mar, jan, feb)
        val plan = LedgerEngine.plan(LedgerEngine.computeStates(shuffled, emptyList()), 900)

        assertEquals(listOf("2025-01", "2025-02"), plan.lines.map { it.month })
        assertEquals(listOf(600L, 300L), plan.lines.map { it.amount })
    }

    @Test
    fun `recalculation is deterministic and idempotent`() {
        val jan = month("2025-01", 600)
        val feb = month("2025-02", 600)
        val months = listOf(jan, feb)
        val allocations = listOf(alloc(1, jan.id, 600), alloc(2, feb.id, 250))

        val first = LedgerEngine.computeStates(months, allocations)
        val second = LedgerEngine.computeStates(months, allocations)
        val third = LedgerEngine.computeStates(months, allocations)

        assertEquals(first, second)
        assertEquals(second, third)
    }

    // ── 16-17. Outstanding and defaulter derivation ───────────────────

    @Test
    fun `outstanding respects the as-of month`() {
        val months = listOf(month("2025-01", 600), month("2025-02", 600), month("2025-03", 600))
        val states = LedgerEngine.computeStates(months, emptyList())

        assertEquals(600L, LedgerEngine.outstanding(states, "2025-01"))
        assertEquals(1200L, LedgerEngine.outstanding(states, "2025-02"))
        assertEquals(1800L, LedgerEngine.outstanding(states, "2025-03"))
    }

    @Test
    fun `a fully settled tenant is not a defaulter`() {
        val jan = month("2025-01", 600)
        val states = LedgerEngine.computeStates(listOf(jan), listOf(alloc(1, jan.id, 600)))
        val summary = LedgerEngine.summarise(states, "2025-01")

        assertFalse(summary.isDefaulter)
        assertEquals(0L, summary.totalOutstanding)
        assertNull(summary.outstandingSince)
        assertTrue(summary.unpaidPeriods.isEmpty())
    }

    @Test
    fun `a partially paid tenant is a defaulter for the shortfall only`() {
        val jan = month("2025-01", 600)
        val states = LedgerEngine.computeStates(listOf(jan), listOf(alloc(1, jan.id, 250)))
        val summary = LedgerEngine.summarise(states, "2025-01")

        assertTrue(summary.isDefaulter)
        assertEquals(350L, summary.totalOutstanding)
        assertEquals(1, summary.partialMonths)
        assertEquals(0, summary.unpaidMonths)
    }

    // ── 18. Unresolved historical rent ────────────────────────────────

    @Test
    fun `unresolved months are flagged and kept out of authoritative totals`() {
        val unknown = month("2019-05", 0, RentCertainty.UNRESOLVED)
        val known = month("2025-01", 600, RentCertainty.KNOWN)
        val states = LedgerEngine.computeStates(listOf(unknown, known), emptyList())
        val summary = LedgerEngine.summarise(states, "2025-01")

        assertTrue(summary.hasUnresolvedHistory)
        assertFalse(states.first { it.month == "2019-05" }.isAuthoritative)
        assertTrue(states.first { it.month == "2025-01" }.isAuthoritative)
        // No rent was invented for the unresolved month.
        assertEquals(0L, states.first { it.month == "2019-05" }.rentDue)
    }

    @Test
    fun `unresolved outstanding is reported separately`() {
        val unresolved = month("2019-05", 250, RentCertainty.UNRESOLVED)
        val known = month("2025-01", 600, RentCertainty.KNOWN)
        val states = LedgerEngine.computeStates(listOf(unresolved, known), emptyList())

        assertEquals(850L, LedgerEngine.outstanding(states, "2025-01"))
        assertEquals(250L, LedgerEngine.unresolvedOutstanding(states, "2025-01"))
    }

    // ── 19. Zero-data state ───────────────────────────────────────────

    @Test
    fun `a tenant with no ledger months has an empty summary and never crashes`() {
        val summary = LedgerEngine.summarise(emptyList(), "2025-01")

        assertEquals(0L, summary.totalOutstanding)
        assertEquals(0, summary.unpaidMonths)
        assertFalse(summary.isDefaulter)
        assertNull(summary.outstandingSince)
        assertNull(summary.lastPaidUpTo)
        assertTrue(summary.unpaidPeriods.isEmpty())
    }

    // ── 36. Database invariants ───────────────────────────────────────

    @Test
    fun `invariant check accepts a consistent ledger`() {
        val jan = month("2025-01", 600)
        val feb = month("2025-02", 600)
        val allocations = listOf(alloc(1, jan.id, 600), alloc(1, feb.id, 200))
        LedgerEngine.verifyInvariants(listOf(jan, feb), allocations, mapOf(1L to 800L))
    }

    @Test
    fun `invariant check rejects allocations exceeding the payment amount`() {
        val jan = month("2025-01", 600)
        val allocations = listOf(alloc(1, jan.id, 600))
        val error = runCatching {
            LedgerEngine.verifyInvariants(listOf(jan), allocations, mapOf(1L to 300L))
        }.exceptionOrNull()
        assertTrue(error is AccountingException)
    }

    @Test
    fun `invariant check rejects a month allocated beyond its rent`() {
        val jan = month("2025-01", 600)
        val allocations = listOf(alloc(1, jan.id, 400), alloc(2, jan.id, 400))
        val error = runCatching {
            LedgerEngine.verifyInvariants(listOf(jan), allocations, mapOf(1L to 400L, 2L to 400L))
        }.exceptionOrNull()
        assertTrue(error is AccountingException)
    }

    @Test
    fun `invariant check rejects duplicate allocation rows`() {
        val jan = month("2025-01", 600)
        val allocations = listOf(alloc(1, jan.id, 100), alloc(1, jan.id, 100))
        val error = runCatching {
            LedgerEngine.verifyInvariants(listOf(jan), allocations, mapOf(1L to 200L))
        }.exceptionOrNull()
        assertTrue(error is AccountingException)
    }

    @Test
    fun `invariant check rejects an orphan allocation`() {
        val jan = month("2025-01", 600)
        val allocations = listOf(alloc(1, 9999L, 100))
        val error = runCatching {
            LedgerEngine.verifyInvariants(listOf(jan), allocations, mapOf(1L to 100L))
        }.exceptionOrNull()
        assertTrue(error is AccountingException)
    }

    @Test
    fun `no month can ever report a negative outstanding`() {
        val months = listOf(month("2025-01", 600), month("2025-02", 400))
        val allocations = listOf(alloc(1, months[0].id, 600), alloc(2, months[1].id, 400))
        LedgerEngine.computeStates(months, allocations).forEach {
            assertTrue(it.outstanding >= 0L)
        }
    }
}
