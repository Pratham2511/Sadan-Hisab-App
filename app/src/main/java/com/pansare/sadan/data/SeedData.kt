package com.pansare.sadan.data

import com.pansare.sadan.domain.MonthKey

/**
 * Supplied present-day tenant/rent records and unpaid ranges.
 * No historical receipts or paid-month data is fabricated.
 * REAL NAMES REMOVED for Data Privacy.
 */
object SeedData {

    private data class Seed(
        val display: String,
        val name: String,
        val rent: Long,
        val shopNote: String = ""
    )

    private val a = listOf(
        Seed("A-01", "Tenant A-01", 600),
        Seed("A-02", "Tenant A-02", 600),
        Seed("A-03", "Tenant A-03", 600),
        Seed("A-04", "Tenant A-04", 380),
        Seed("A-05", "Tenant A-05", 600),
        Seed("A-06", "Tenant A-06", 600),
        Seed("A-07", "Tenant A-07", 500),
        Seed("A-08", "Tenant A-08", 600),
        Seed("A-09", "Tenant A-09", 600),
        Seed("A-10", "Tenant A-10", 500),
        Seed("A-11", "Tenant A-11", 500),
        Seed("A-12", "Tenant A-12", 600),
        Seed("A-13", "Tenant A-13", 500),
        Seed("A-14", "Tenant A-14", 400, shopNote = "Shop rent"),
        Seed("A-15", "Tenant A-15", 400),
        Seed("A-16", "Tenant A-16", 300),
        Seed("A-17", "Tenant A-17", 300),
        Seed("A-18", "Tenant A-18", 300),
        Seed("A-19", "Tenant A-19", 400),
        Seed("A-20", "Tenant A-20", 400),
        Seed("A-21", "Tenant A-21", 300),
        Seed("A-22", "Tenant A-22", 300),
        Seed("A-23", "Tenant A-23", 175),
        Seed("A-24", "Tenant A-24", 175),
        Seed("A-25", "Tenant A-25", 300),
        Seed("A-26", "Tenant A-26", 350),
        Seed("A-27(A)", "Tenant A-27(A)", 420),
        Seed("A-27(B)", "Tenant A-27(B)", 420)
    )

    private val b = listOf(
        Seed("B-01", "Tenant B-01", 500),
        Seed("B-02", "Tenant B-02", 400),
        Seed("B-03", "Tenant B-03", 300),
        Seed("B-04", "Tenant B-04", 400),
        Seed("B-05", "Tenant B-05", 400),
        Seed("B-06", "Tenant B-06", 400),
        Seed("B-07", "Tenant B-07", 400),
        Seed("B-08", "Tenant B-08", 300),
        Seed("B-09", "Tenant B-09", 400),
        Seed("B-10", "Tenant B-10", 300),
        Seed("B-11", "Tenant B-11", 400),
        Seed("B-12", "Tenant B-12", 360),
        Seed("B-13", "Tenant B-13", 600),
        Seed("B-14", "Tenant B-14", 400),
        Seed("B-15", "Tenant B-15", 350),
        Seed("B-16", "Tenant B-16", 300),
        Seed("B-17", "Tenant B-17", 400),
        Seed("B-18", "Tenant B-18", 310),
        Seed("B-19", "Tenant B-19", 600),
        Seed("B-20", "Tenant B-20", 500)
    )

    private val unpaidStarts = mapOf(
        "A-01" to "2019-08", "A-02" to "2014-07", "A-03" to "2026-01",
        "A-04" to "2019-11", "A-05" to "2014-07", "A-06" to "2019-09",
        "A-07" to "2026-01", "A-08" to "2017-05", "A-09" to "2017-05",
        "A-10" to "2026-04", "A-11" to "2002-01", "A-12" to "2014-07",
        "A-13" to "2017-01", "A-14" to "2017-01", "A-15" to "2014-07",
        "A-16" to "2025-01", "A-17" to "2017-05", "A-18" to "2017-07",
        "A-19" to "2025-01", "A-20" to "2025-01", "A-21" to "2019-02",
        "A-22" to "2025-01", "A-23" to "2023-01", "A-24" to "2026-04",
        "A-25" to "2021-01", "A-26" to "2026-01", "A-27(A)" to "2017-03",
        "A-27(B)" to "2017-03",
        "B-01" to "2026-04", "B-02" to "2024-07", "B-03" to "2019-01",
        "B-04" to "2026-01", "B-05" to "2022-05", "B-06" to "2019-02",
        "B-07" to "2024-01", "B-08" to "2026-01", "B-09" to "2025-08",
        "B-10" to "2017-03", "B-11" to "2017-03", "B-12" to "2017-01",
        "B-13" to "2021-02", "B-14" to "2017-01", "B-15" to "2016-03",
        "B-16" to "2018-06", "B-17" to "2017-03", "B-18" to "2016-10",
        "B-19" to "2025-01", "B-20" to "2026-04"
    )

    suspend fun insert(db: AppDatabase) {
        val allSeeds = a.map { "A" to it } + b.map { "B" to it }
        val tenantRows = mutableListOf<TenantEntity>()

        allSeeds.forEach { (wing, seed) ->
            val roomId = db.roomDao().insert(
                RoomEntity(
                    wing = wing,
                    roomNumber = seed.display.removePrefix("$wing-"),
                    displayRoomNumber = seed.display
                )
            )
            val remark = buildString {
                append("Imported supplied unpaid range; no historical receipts or paid-month data was fabricated.")
                if (seed.shopNote.isNotEmpty()) append(" ${seed.shopNote}.")
            }
            tenantRows += TenantEntity(
                roomId = roomId,
                tenantName = seed.name,
                monthlyRent = seed.rent,
                occupancyStartMonth = unpaidStarts[seed.display],
                remarks = remark
            )
        }

        val allSeedsList = a + b
        tenantRows.forEachIndexed { index, tenant ->
            val id = db.tenantDao().insert(tenant)
            val seed = allSeedsList[index]
            val start = requireNotNull(unpaidStarts[seed.display])
            val months = MonthKey.betweenInclusive(start, "2026-07")

            db.rentChangeDao().insert(
                RentChangeEntity(
                    tenantId = id,
                    effectiveFromMonth = start,
                    monthlyRent = seed.rent,
                    note = "Imported supplied rate; historical changes not supplied."
                )
            )

            db.ledgerDao().insertAll(
                months.map { month ->
                    MonthlyLedgerEntity(
                        tenantId = id,
                        month = month,
                        applicableRent = seed.rent,
                        amountDue = seed.rent,
                        balance = seed.rent,
                        notes = "Imported supplied unpaid month; payment history unknown."
                    )
                }
            )
        }

        // Record known data discrepancies
        db.validationDao().insertAll(
            listOf(
                ImportValidationIssueEntity(
                    roomDisplay = "A-11",
                    issue = "Supplied 307 months conflicts with inclusive range calculation.",
                    sourceValue = "Jan 2002–Jul 2026 = 295 months, not 307"
                ),
                ImportValidationIssueEntity(
                    roomDisplay = "A-20",
                    issue = "Supplied total is inconsistent with rent × months.",
                    sourceValue = "₹400 × 19 = ₹7,600; source says ₹6,650"
                ),
                ImportValidationIssueEntity(
                    roomDisplay = "A-23",
                    issue = "Conflicting rent notation retained for review.",
                    sourceValue = "₹175 and ₹300 both mentioned in source"
                ),
                ImportValidationIssueEntity(
                    roomDisplay = "A-27(B)",
                    issue = "Conflicting rent notation retained for review.",
                    sourceValue = "₹420 and ₹500 both mentioned in source"
                ),
                ImportValidationIssueEntity(
                    roomDisplay = "B-07",
                    issue = "Supplied total is inconsistent with rent × months.",
                    sourceValue = "₹400 × 31 = ₹12,400; source says ₹10,230"
                ),
                ImportValidationIssueEntity(
                    roomDisplay = "B-08",
                    issue = "Supplied total is inconsistent with rent × months.",
                    sourceValue = "₹300 × 7 = ₹2,100; source says ₹2,800"
                )
            )
        )
    }
}
