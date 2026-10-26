package app.onlysearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitGridTest {
    private fun valid(pages: List<List<Cell>>, cols: Int, rows: Int) = pages.all { items ->
        items.indices.all { i -> items[i].let { c -> cellFits(items.filterIndexed { j, _ -> j != i }, c.x, c.y, c.w, c.h, cols, rows) } }
    }

    @Test fun shrinkKeepsEveryItem() {
        val page = listOf(Cell("w:1", 0, 0, 5, 2)) + (0 until 5).map { Cell("a$it", it, 5) } + Cell("a5", 4, 4)
        val out = fitPages(listOf(page), 4, 6)
        assertEquals(page.map { it.key }.toSet(), out.flatten().map { it.key }.toSet())
        assertEquals(page.size, out.flatten().size)
        assertEquals(Cell("w:1", 0, 0, 4, 2), out.flatten().first { it.key == "w:1" })
        assertTrue(valid(out, 4, 6))
    }

    @Test fun overflowGoesToOtherPageThenNewPage() {
        val full = (0 until 4).flatMap { y -> (0 until 4).map { x -> Cell("p$x$y", x, y) } }
        val second = (0 until 3).map { Cell("q$it", it, 0) }
        val out = fitPages(listOf(full, second), 3, 3)
        assertEquals(19, out.flatten().size)
        assertEquals(3, out.size)
        assertEquals(listOf(9, 9, 1), out.map { it.size })
        assertTrue(out[1].containsAll(second))
        assertTrue(valid(out, 3, 3))
    }

    @Test fun widgetTallerThanGridIsClampedAndPlaced() {
        val out = fitPages(listOf(listOf(Cell("a", 0, 0), Cell("w:2", 0, 1, 4, 6))), 4, 3)
        assertEquals(Cell("w:2", 0, 0, 4, 3), out[1].single())
        assertTrue(valid(out, 4, 3))
    }

    @Test fun unchangedWhenEverythingFits() {
        val pages = listOf(listOf(Cell("a", 0, 0), Cell("w:1", 1, 1, 2, 2)))
        assertEquals(pages, fitPages(pages, 4, 6))
    }
}
