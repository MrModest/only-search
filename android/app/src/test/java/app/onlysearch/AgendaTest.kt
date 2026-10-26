package app.onlysearch

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaTest {
    private fun ev(begin: Long, end: Long, allDay: Boolean = false) = Event("e", begin, end, allDay, 1, 0)
    private fun utc(y: Int, m: Int, d: Int) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m, d) }.timeInMillis

    @Test fun zeroLengthEventTerminates() {
        val (lane, lanes) = assignLanes(listOf(ev(1000, 1000)))
        assertEquals(0, lane[0]); assertEquals(1, lanes[0])
    }

    @Test fun sameTimestampEventsTerminate() {
        val (lane, lanes) = assignLanes(listOf(ev(1000, 1000), ev(1000, 1000), ev(1000, 2000), ev(1000, 2000)))
        assertEquals(4, lane.size); assertEquals(4, lanes.size)
        assertTrue(lanes.all { it >= 1 })
        assertNotEquals(lane[2], lane[3])
    }

    @Test fun overlappingEventsGetSeparateLanes() {
        val (lane, lanes) = assignLanes(listOf(ev(0, 100), ev(50, 150), ev(100, 200), ev(300, 400)))
        assertEquals(listOf(0, 1, 0, 0), lane.toList())
        assertEquals(listOf(2, 2, 2, 1), lanes.toList())
    }

    @Test fun allDayFilterUsesLocalDate() {
        val tz = TimeZone.getTimeZone("Europe/Berlin")
        val now = Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.OCTOBER, 26, 9, 0) }.timeInMillis
        val day = 86_400_000L
        assertFalse(coversToday(ev(utc(2026, 9, 25), utc(2026, 9, 25) + day, true), now, tz))
        assertTrue(coversToday(ev(utc(2026, 9, 26), utc(2026, 9, 26) + day, true), now, tz))
        assertTrue(coversToday(ev(utc(2026, 9, 25), utc(2026, 9, 25) + 3 * day, true), now, tz))
    }

    @Test fun dayStartAlignsToLocalHourInKolkata() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.OCTOBER, 26, 15, 45, 30) }.timeInMillis
        val start = dayStart(now, 2, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = start }
        assertEquals(13, c.get(Calendar.HOUR_OF_DAY)); assertEquals(0, c.get(Calendar.MINUTE)); assertEquals(0, c.get(Calendar.SECOND))
    }
}
