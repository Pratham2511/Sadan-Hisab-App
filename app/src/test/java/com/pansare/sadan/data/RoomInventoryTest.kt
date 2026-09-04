package com.pansare.sadan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fresh-install inventory: exactly 48 rooms and nothing else. */
class RoomInventoryTest {

    @Test
    fun `the property has exactly 48 rooms`() {
        assertEquals(48, RoomInventory.ALL.size)
        assertEquals(48, RoomInventory.TOTAL_ROOMS)
        assertEquals(48, RoomInventory.buildRooms().size)
    }

    @Test
    fun `A wing runs A-01 to A-26 plus the two units of room 27`() {
        val a = RoomInventory.A_WING
        assertEquals(28, a.size)
        assertTrue(a.contains("A-01"))
        assertTrue(a.contains("A-26"))
        // Room 27 is let as two separate units, so there is no plain A-27 record.
        assertFalse(a.contains("A-27"))
        assertTrue(a.contains("A-27(A)"))
        assertTrue(a.contains("A-27(B)"))
    }

    @Test
    fun `B wing runs from B-01 to B-20`() {
        val b = RoomInventory.B_WING
        assertEquals(20, b.size)
        assertTrue(b.contains("B-01"))
        assertTrue(b.contains("B-20"))
        assertFalse(b.contains("B-21"))
    }

    @Test
    fun `room identities are unique`() {
        assertEquals(RoomInventory.ALL.size, RoomInventory.ALL.toSet().size)
        val keys = RoomInventory.ALL.map { RoomInventory.sortKey(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `rooms sort naturally and not lexicographically`() {
        val sorted = RoomInventory.ALL.sortedBy { RoomInventory.sortKey(it) }
        assertEquals("A-01", sorted.first())
        assertEquals("B-20", sorted.last())
        // A-09 must precede A-10, which naive string sorting also gets right,
        // but A-27 must precede A-27(A) and both must precede B-01.
        assertTrue(sorted.indexOf("A-26") < sorted.indexOf("A-27(A)"))
        assertTrue(sorted.indexOf("A-27(A)") < sorted.indexOf("A-27(B)"))
        assertTrue(sorted.indexOf("A-27(B)") < sorted.indexOf("B-01"))
        assertTrue(sorted.indexOf("A-02") < sorted.indexOf("A-10"))
    }

    @Test
    fun `wing and room number are parsed correctly`() {
        assertEquals("A", RoomInventory.wingOf("A-01"))
        assertEquals("B", RoomInventory.wingOf("B-20"))
        assertEquals("01", RoomInventory.roomNumberOf("A-01"))
        assertEquals("27(A)", RoomInventory.roomNumberOf("A-27(A)"))
    }

    @Test
    fun `a fresh install creates rooms only and never a tenant`() {
        val rooms = RoomInventory.buildRooms()
        assertEquals(48, rooms.size)
        assertTrue(rooms.all { it.active })
        // Rooms carry no tenant-identifying information whatsoever.
        assertTrue(rooms.all { it.remarks.isBlank() })
    }
}
