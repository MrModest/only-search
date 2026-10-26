package app.onlysearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    @Test fun cellFitsRejectsOutOfBoundsAndOverlap() {
        val items = listOf(Cell("a", 1, 1, 2, 2))
        assertTrue(cellFits(items, 0, 0, 1, 1, 4, 4))
        assertTrue(cellFits(items, 3, 1, 1, 2, 4, 4))
        assertFalse(cellFits(items, 2, 2, 1, 1, 4, 4))
        assertFalse(cellFits(items, 3, 0, 2, 1, 4, 4))
        assertFalse(cellFits(items, -1, 0, 1, 1, 4, 4))
        assertFalse(cellFits(items, 0, 3, 1, 2, 4, 4))
    }

    @Test fun cellFitsIgnoresTheItemBeingMoved() {
        val items = listOf(Cell("a", 1, 1, 2, 2))
        assertTrue(cellFits(items, 1, 1, 2, 2, 4, 4, ignore = "a"))
    }

    @Test fun findSpotFillsFromTopOrBottom() {
        assertEquals(0 to 0, findSpot(emptyList(), 1, 1, 4, 3, fromBottom = false))
        assertEquals(0 to 2, findSpot(emptyList(), 1, 1, 4, 3, fromBottom = true))
    }

    @Test fun findSpotSkipsOccupiedCellsAndFitsSize() {
        val items = listOf(Cell("a", 0, 0), Cell("b", 1, 0), Cell("c", 0, 1, 1, 1))
        assertEquals(2 to 0, findSpot(items, 1, 1, 3, 2, fromBottom = false))
        assertEquals(1 to 1, findSpot(items, 2, 1, 3, 2, fromBottom = false))
    }

    @Test fun findSpotNullWhenFullOrTooBig() {
        val full = listOf(Cell("a", 0, 0), Cell("b", 1, 0))
        assertNull(findSpot(full, 1, 1, 2, 1, fromBottom = false))
        assertNull(findSpot(emptyList(), 5, 1, 4, 4, fromBottom = false))
        assertNull(findSpot(emptyList(), 1, 5, 4, 4, fromBottom = true))
    }
}
