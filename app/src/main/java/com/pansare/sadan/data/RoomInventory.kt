package com.pansare.sadan.data

/**
 * The physical room inventory of Pansare Sadan.
 *
 * This is the ONLY data a fresh installation creates. It contains no tenants, no payments,
 * no ledger entries and no personal information of any kind — the property owner adds their
 * own tenants through the app.
 *
 * A Wing: A-01 .. A-26, then A-27(A) and A-27(B)   = 28 records
 *          Room 27 exists only as its two sub-units, so there is no plain "A-27".
 * B Wing: B-01 .. B-20                             = 20 records
 * Total                                            = 48 records
 */
object RoomInventory {

    const val TOTAL_ROOMS = 48

    /** Stable, ordered list of every room's display number. */
    val ALL: List<String> = buildList {
        (1..26).forEach { add("A-%02d".format(it)) }
        // Room 27 is physically divided into two lettable units.
        add("A-27(A)")
        add("A-27(B)")
        (1..20).forEach { add("B-%02d".format(it)) }
    }

    val A_WING: List<String> get() = ALL.filter { it.startsWith("A") }
    val B_WING: List<String> get() = ALL.filter { it.startsWith("B") }

    fun wingOf(display: String): String = display.substring(0, 1)

    /**
     * Sort key so that A-01 < A-09 < A-26 < A-27(A) < A-27(B) < B-01, independent of
     * database row order. Numeric part is zero-padded, suffix keeps sub-units together.
     */
    fun sortKey(display: String): String {
        val wing = wingOf(display)
        val rest = display.removePrefix("$wing-")
        val number = rest.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val suffix = rest.dropWhile { it.isDigit() }
        return "%s-%03d-%s".format(wing, number, suffix)
    }

    /** The room number portion stored on the entity, e.g. "01" or "27(A)". */
    fun roomNumberOf(display: String): String = display.removePrefix("${wingOf(display)}-")

    /** Builds the 48 room rows inserted on first launch. */
    fun buildRooms(): List<RoomEntity> = ALL.map { display ->
        RoomEntity(
            wing = wingOf(display),
            roomNumber = roomNumberOf(display),
            displayRoomNumber = display,
            sortKey = sortKey(display)
        )
    }
}
