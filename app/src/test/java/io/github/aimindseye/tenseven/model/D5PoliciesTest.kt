package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class D5PoliciesTest {
    @Test fun monthGridMondayFirstAndFullWeeks() {
        val cells = D5CalendarBusy.monthCells(YearMonth.of(2026, 11))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(LocalDate.of(2026, 11, 30), cells[35])
    }

    @Test fun overlapsDoNotDoubleCount() {
        val z = ZoneId.of("UTC")
        val d = LocalDate.of(2026, 11, 8)
        val a =
            d
                .atTime(9, 0)
                .atZone(z)
                .toInstant()
                .toEpochMilli()
        val b =
            d
                .atTime(10, 0)
                .atZone(z)
                .toInstant()
                .toEpochMilli()
        val c =
            d
                .atTime(11, 0)
                .atZone(z)
                .toInstant()
                .toEpochMilli()
        assertEquals(120, D5CalendarBusy.bookedMinutes(d, listOf(D5TimeSpan(a, b, false), D5TimeSpan(a + 30 * 60_000, c, false)), z))
    }

    @Test fun dialInputIsBounded() {
        assertEquals("+123", D5PhoneDialPolicy.append("+12", "3"))
        assertEquals("12", D5PhoneDialPolicy.append("12", "x"))
        assertEquals("+12", D5PhoneDialPolicy.append("+12", "+"))
        assertFalse(D5PhoneDialPolicy.canDial("*#"))
        assertTrue(D5PhoneDialPolicy.canDial("911"))
        assertEquals(48, D5PhoneDialPolicy.normalize("1".repeat(99)).length)
    }
}
