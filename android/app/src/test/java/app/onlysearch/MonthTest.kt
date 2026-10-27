package app.onlysearch

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun utc(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun local(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun allDay(title: String, from: LocalDate, until: LocalDate) = Event(title, utc(from), utc(until), true, 1, 0)
    private fun timed(title: String, d: LocalDate, h: Int, hours: Int = 1) = Event(title, local(d, h), local(d, h) + hours * HourMs, false, 1, 0)

    @Test fun gridFollowsFirstDayOfWeek() {
        val oct = LocalDate.of(2026, 10, 27)
        assertEquals(LocalDate.of(2026, 9, 28), gridStart(oct, DayOfWeek.MONDAY))
        assertEquals(5, gridWeeks(oct, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 27), gridStart(oct, DayOfWeek.SUNDAY))
        assertEquals(5, gridWeeks(oct, DayOfWeek.SUNDAY))
        // February 2027 starts on a Monday and fills exactly four weeks; August 2026 needs six from Monday.
        assertEquals(4, gridWeeks(LocalDate.of(2027, 2, 10), DayOfWeek.MONDAY))
        assertEquals(6, gridWeeks(LocalDate.of(2026, 8, 1), DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 3, 1), gridStart(LocalDate.of(2026, 3, 15), DayOfWeek.SUNDAY))
        // Paging from today across the year end: January 2027 starts on a Friday.
        val jan = LocalDate.of(2026, 10, 27).plusMonths(3)
        assertEquals(LocalDate.of(2026, 12, 28), gridStart(jan, DayOfWeek.MONDAY))
        assertEquals(5, gridWeeks(jan, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 12, 26), gridStart(jan, DayOfWeek.SATURDAY))
        assertEquals(6, gridWeeks(jan, DayOfWeek.SATURDAY))
    }

    @Test fun multiDayEventSplitsAtWeekBoundary() {
        val start = gridStart(LocalDate.of(2026, 10, 1), DayOfWeek.MONDAY)
        // Fri 9 Oct to Tue 13 Oct inclusive: Fri–Sun in week 1, Mon–Tue in week 2.
        val bars = monthBars(listOf(allDay("trip", LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 14))), start, 5, zone)
        assertEquals(listOf(Bar(bars[0].event, 1, 4, 7, 0), Bar(bars[0].event, 2, 0, 2, 0)), bars)
    }

    @Test fun barsClipToGridAndTimedEventsTakeOneDay() {
        val start = gridStart(LocalDate.of(2026, 10, 1), DayOfWeek.MONDAY)
        val bars = monthBars(listOf(
            allDay("before", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 30)),
            timed("late", LocalDate.of(2026, 10, 1), 23),
            Event("overnight", local(LocalDate.of(2026, 10, 2), 22), local(LocalDate.of(2026, 10, 3), 2), false, 1, 0),
        ), start, 5, zone)
        assertEquals(mapOf("before" to (0 to 2), "late" to (3 to 4), "overnight" to (4 to 6)), bars.associate { it.event.title to (it.from to it.to) })
    }

    @Test fun overlappingBarsStackInLanes() {
        val start = gridStart(LocalDate.of(2026, 10, 1), DayOfWeek.MONDAY)
        val d = LocalDate.of(2026, 10, 6)
        val bars = monthBars(listOf(
            timed("a", d, 9), timed("b", d, 11),
            allDay("long", LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 10)),
            timed("c", LocalDate.of(2026, 10, 8), 9),
        ), start, 5, zone)
        assertEquals(mapOf("long" to 0, "a" to 1, "b" to 2, "c" to 1), bars.associate { it.event.title to it.lane })
    }
}
