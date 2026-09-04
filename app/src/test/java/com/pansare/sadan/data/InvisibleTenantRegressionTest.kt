package com.pansare.sadan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pansare.sadan.domain.MonthKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InvisibleTenantRegressionTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RentRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RentRepository(db)
        repo.initialiseIfEmpty()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `adding 3 tenants to Wing A returns all 3 in Wing A queries`() = runBlocking {
        val allRooms = repo.allRooms().sortedBy { it.sortKey }
        val wingARooms = allRooms.filter { it.wing == "A" }

        repo.addTenant(wingARooms[0].id, "Tenant Alpha", "9876543210", 5000, MonthKey.current())
        repo.addTenant(wingARooms[1].id, "Tenant Beta", "9876543211", 5500, MonthKey.current())
        repo.addTenant(wingARooms[2].id, "Tenant Gamma", "9876543212", 6000, MonthKey.current())

        val rows = repo.roomsWithTenants()
        val wingARows = rows.filter { it.wing == "A" }
        val occupiedA = wingARows.filter { it.isOccupied }

        assertEquals(28, wingARows.size)
        assertEquals(3, occupiedA.size)
        assertEquals("Tenant Alpha", occupiedA[0].tenantName)
        assertEquals("Tenant Beta", occupiedA[1].tenantName)
        assertEquals("Tenant Gamma", occupiedA[2].tenantName)
    }

    @Test
    fun `adding 10 tenants to Wing A scales cleanly without arbitrary truncation`() = runBlocking {
        val allRooms = repo.allRooms().sortedBy { it.sortKey }
        val wingARooms = allRooms.filter { it.wing == "A" }

        for (i in 0 until 10) {
            repo.addTenant(
                roomId = wingARooms[i].id,
                name = "Tenant $i",
                mobile = "987654321$i",
                monthlyRent = 5000L + (i * 100),
                occupancyStartMonth = MonthKey.current()
            )
        }

        val rows = repo.roomsWithTenants()
        val wingARows = rows.filter { it.wing == "A" }
        val occupiedA = wingARows.filter { it.isOccupied }

        assertEquals(28, wingARows.size)
        assertEquals(10, occupiedA.size)
    }
}
