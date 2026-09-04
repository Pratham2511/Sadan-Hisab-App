package com.pansare.sadan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthKeyTest {

    @Test
    fun `parses canonical yyyy-MM correctly`() {
        val ym = MonthKey.parse("2026-09")
        assertEquals(YearMonth.of(2026, 9), ym)
        assertEquals("2026-09", MonthKey.format(ym))
    }

    @Test
    fun `parses human readable month names and formats`() {
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("September 2026"))
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("Sep 2026"))
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("September-2026"))
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("Sep-2026"))
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("09/2026"))
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("9/2026"))
        assertEquals(YearMonth.of(2026, 9), MonthKey.parse("Sep 26"))
    }

    @Test
    fun `normalize returns canonical yyyy-MM string`() {
        val variants = listOf(
            "September 2026",
            "Sep 2026",
            "September-2026",
            "Sep-2026",
            "09/2026",
            "2026-09",
            "Sep-26"
        )
        for (v in variants) {
            assertEquals("Failed for variant: $v", "2026-09", MonthKey.normalize(v))
        }
    }

    @Test
    fun `isValid accepts both canonical and human-readable month strings`() {
        assertTrue(MonthKey.isValid("2026-09"))
        assertTrue(MonthKey.isValid("September 2026"))
        assertTrue(MonthKey.isValid("Sep 2026"))
        assertTrue(MonthKey.isValid("09/2026"))
    }
}
