package app.onlysearch

import app.onlysearch.search.anyDecimal
import app.onlysearch.search.calcKinds
import app.onlysearch.search.homeQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {
    @Test fun prefixBeatsWordBeatsSubstringBeatsFuzzy() {
        assertEquals(100 to 0..2, match("Camera", "cam"))
        assertEquals(80 to 7..9, match("Google Maps", "map"))
        assertEquals(60 to 1..3, match("Gmail", "mai"))
        assertEquals(30 to null, match("Calendar", "cdr"))
    }

    @Test fun caseAndSurroundingSpacesAreIgnored() = assertEquals(100 to 0..4, match("CLOCK", "  Clock "))

    @Test fun noMatch() {
        assertNull(match("Camera", "xyz"))
        assertNull(match("Camera", ""))
        assertNull(match("Camera", "   "))
    }

    @Test fun fuzzyCanBeOffAndNeedsTwoChars() {
        assertNull(match("Calendar", "cdr", fuzzy = false))
        assertNull(match("Calendar", "z"))
        assertEquals(60 to 1..1, match("Calendar", "a", fuzzy = false))
    }

    @Test fun regexCharactersAreLiteral() {
        assertNull(match("Camera", ".*"))
        assertEquals(80 to 2..2, match("My.App", "."))
    }

    @Test fun calcKindsRecognisesEachKind() {
        assertEquals(setOf("math"), calcKinds("2+2*3"))
        assertEquals(setOf("currency"), calcKinds("100 usd"))
        assertEquals(setOf("currency", "finance"), calcKinds("\$20 + 5%"))
        assertEquals(setOf("units"), calcKinds("5 km in miles"))
        assertEquals(setOf("dates"), calcKinds("tomorrow"))
        assertEquals(setOf("finance"), calcKinds("20% tip"))
    }

    @Test fun calcKindsNaturalLanguageAndPlaces() {
        assertEquals(setOf("currency", "natural"), calcKinds("\$10 for lunch"))
        assertEquals(setOf("dates"), calcKinds("time in Tokyo"))
        assertEquals(setOf("math"), calcKinds("sqrt(16) + pi"))
        assertEquals(setOf("math"), calcKinds(""))
    }

    @Test fun bareAmountsConvertToHomeUnits() {
        assertEquals("12 USD in EUR", homeQuery("12 USD", "EUR", "metric"))
        assertEquals("12 USD + 5 EUR in EUR", homeQuery("12 USD + 5 EUR", "EUR", "metric"))
        assertEquals("5 ft in m", homeQuery("5 ft", "EUR", "metric"))
        assertEquals("5 ft 10 in in m", homeQuery("5 ft 10 in", "EUR", "metric"))
        assertEquals("6 inches in cm", homeQuery("6 inches", "EUR", "metric"))
        assertEquals("10 lb in kg", homeQuery("10 lb", "EUR", "metric"))
        assertEquals("70 F in celsius", homeQuery("70 F", "EUR", "metric"))
        assertEquals("3 km in mi", homeQuery("3 km", "GBP", "imperial"))
        assertEquals("5 m in ft", homeQuery("5 m", "USD", "customaryUS"))
        assertEquals("2 kg in lb", homeQuery("2 kg", "USD", "customaryUS"))
    }

    @Test fun explicitTargetsHomeUnitsAndOtherMathAreLeftAlone() {
        assertNull(homeQuery("12 USD in GBP", "EUR", "metric"))
        assertNull(homeQuery("5 ft to cm", "EUR", "metric"))
        assertNull(homeQuery("3 km", "EUR", "metric"))
        assertNull(homeQuery("10 lb", "USD", "customaryUS"))
        assertNull(homeQuery("2+2", "EUR", "metric"))
        assertNull(homeQuery("20% of 50", "EUR", "metric"))
        assertNull(homeQuery("tomorrow + 3 days", "EUR", "imperial"))
        assertNull(homeQuery("3:45pm + 4h 10m", "EUR", "imperial"))
        assertNull(homeQuery("time in Tokyo", "EUR", "metric"))
        listOf("today + 1", "12:00 + 1h 35m", "12:00 Berlin in Moscow", "1 pm -> 13:00", "msk time", "12:30 + 0.5h", "5 min").forEach {
            assertNull(it, homeQuery(it, "EUR", "imperial"))
        }
    }

    @Test fun dotAndCommaAreBothDecimalPoints() {
        assertEquals("12,5 + 0,5", anyDecimal("12.5 + 0.5", ','))
        assertEquals("12.5 + 0.5", anyDecimal("12,5 + 0,5", '.'))
        assertEquals("1,000 + 1", anyDecimal("1.000 + 1", ','))
        assertEquals("3.14 * 2", anyDecimal("3,14 * 2", '.'))
        assertEquals("€2.5 + 5.5kg", anyDecimal("€2,5 + 5,5kg", '.'))
    }

    @Test fun separatorsOutsideSingleNumbersStay() {
        assertEquals("12, 13", anyDecimal("12, 13", '.'))
        assertEquals("max(1, 2)", anyDecimal("max(1, 2)", '.'))
        assertEquals("1,000,000 + 1.234,56", anyDecimal("1,000,000 + 1.234,56", '.'))
        assertEquals("26.10.2026 + 3 days", anyDecimal("26.10.2026 + 3 days", ','))
        assertEquals("12:30 + 0.5h", anyDecimal("12:30 + 0,5h", '.'))
        assertEquals("1 pm -> 13:00", anyDecimal("1 pm -> 13:00", ','))
    }
}
