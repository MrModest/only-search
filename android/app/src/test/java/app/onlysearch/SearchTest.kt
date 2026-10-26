package app.onlysearch

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
}
