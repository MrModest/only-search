package app.onlysearch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val pagesJson = """[{"items":[{"k":"a","x":0,"y":1,"w":1,"h":2}]},{"items":[]}]"""

    @Test fun roundTripKeepsEveryType() {
        val all = mapOf("history" to "text\twith\"quotes", "count:a" to 7, "last:a" to (1L shl 40), "set.labels" to true, "pages" to pagesJson,
            "folders" to """{"f:1":{"name":"F","apps":["a"]}}""", "groups" to """[{"name":"G","apps":["a"]}]""", "defaultPage" to 1, "set.appSpan" to 2,
            "dock" to "a\nb", "label:a" to "A", "cals:w:1" to "1,2", "range:w:1" to "4,20", "trim:w:1" to "0,6", "set.gDown" to "search",
            "asked.p" to true, "lastBackup" to 5L, "seeded" to true, "set.theme" to "dark")
        assertEquals(all, parseBackup(backupJson(all)))
    }

    @Test fun skipsDeviceOnlyUnknownKeysAndTypes() {
        val device = mapOf("rates" to "x", "ratesAt" to 1L, "pendingWidget" to 3, "widgetSpot" to "0,0,0,2,2", "novaWidgets" to "[]")
        val out = parseBackup(backupJson(device + mapOf("set" to setOf("a"), "f" to 1.5f, "keep" to 1, "dock" to "a")))
        assertEquals(mapOf("dock" to "a"), out)
    }

    @Test fun dropsDeviceOnlyAndUnknownKeysEvenWhenMalformed() {
        assertEquals(emptyMap<String, Any>(), parseBackup(wrap("""{"pendingWidget":{"t":"s","v":"oops"}}""")))
        assertEquals(emptyMap<String, Any>(), parseBackup(wrap("""{"novaWidgets":{"t":"s","v":"oops"}}""")))
        assertEquals(emptyMap<String, Any>(), parseBackup(wrap("""{"widgetSpot":{"t":"i","v":1},"rates":{"t":"x"},"whatever":{"t":"s","v":"x"}}""")))
    }

    @Test fun backupIsTaggedAndEmptyRestores() {
        val json = backupJson(emptyMap<String, Any>())
        assertEquals("only-search", JSONObject(json).getString("app"))
        assertEquals(emptyMap<String, Any>(), parseBackup(json))
    }

    @Test fun rejectsForeignFiles() {
        assertThrows(IllegalArgumentException::class.java) { parseBackup("""{"app":"other","prefs":{}}""") }
        assertThrows(IllegalArgumentException::class.java) { parseBackup("""{"prefs":{}}""") }
        assertThrows(Exception::class.java) { parseBackup("not json") }
        assertThrows(Exception::class.java) { parseBackup("""{"app":"only-search"}""") }
    }

    @Test fun rejectsUnknownValueTypeAndBadPayloads() {
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"count:a":{"t":"x","v":1}}""")) }
        assertThrows(Exception::class.java) { parseBackup(wrap("""{"count:a":{"t":"i"}}""")) }
        assertThrows(Exception::class.java) { parseBackup(wrap("""{"pages":{"t":"s","v":"[{\"items\":[{\"k\":\"a\"}]}]"}}""")) }
        assertThrows(Exception::class.java) { parseBackup(wrap("""{"groups":{"t":"s","v":"oops"}}""")) }
    }

    @Test fun validBackupWithLayoutPrefsPasses() {
        val folders = """{"f:1":{"name":"F","apps":["a","b"]}}"""
        val groups = """[{"name":"G","apps":["a"]}]"""
        val out = parseBackup(backupJson(mapOf("pages" to pagesJson, "folders" to folders, "groups" to groups)))
        assertFalse(out.isEmpty())
    }

    @Test fun rejectsKnownKeysOutOfRangeOrOfTheWrongType() {
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"set.drawerColumns":{"t":"i","v":0}}""")) }
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"set.iconSize":{"t":"i","v":500}}""")) }
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"set.columns":{"t":"s","v":"8"}}""")) }
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"set.labels":{"t":"i","v":1}}""")) }
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"pages":{"t":"i","v":1}}""")) }
    }

    @Test fun validSettingsPass() {
        val all = mapOf("set.drawerColumns" to 5, "set.iconSize" to 90, "set.columns" to 8, "set.labels" to false, "set.theme" to "dark",
            "set.gDown" to "search", "last:a" to 5L, "count:a" to 3, "asked.x" to true)
        assertEquals(all, parseBackup(backupJson(all)))
    }

    private fun wrap(prefs: String) = """{"app":"only-search","version":1,"prefs":$prefs}"""
}

class ParseLayoutTest {
    @Test fun parsePages() {
        val pages = parsePages("""[{"items":[{"k":"a","x":1,"y":2,"w":3,"h":4},{"k":"w:5","x":0,"y":0,"w":1,"h":1}]},{"items":[]}]""")
        assertEquals(2, pages.size)
        assertEquals(listOf(Cell("a", 1, 2, 3, 4), Cell("w:5", 0, 0)), pages[0].items)
        assertEquals(emptyList<Cell>(), pages[1].items)
    }

    @Test fun parsePagesEmptyListGivesOneEmptyPage() {
        assertEquals(1, parsePages("[]").size)
        assertEquals(emptyList<Cell>(), parsePages("[]")[0].items)
    }

    @Test fun parsePagesBadInputThrows() {
        assertThrows(Exception::class.java) { parsePages("""[{"items":[{"k":"a"}]}]""") }
        assertThrows(Exception::class.java) { parsePages("{}") }
    }

    @Test fun parseGroups() {
        assertEquals(listOf(AppGroup("A", listOf("x", "y")), AppGroup("B", emptyList())),
            parseGroups("""[{"name":"A","apps":["x","y"]},{"name":"B","apps":[]}]"""))
        assertEquals(emptyList<AppGroup>(), parseGroups("[]"))
        assertThrows(Exception::class.java) { parseGroups("""[{"apps":[]}]""") }
    }

    @Test fun parseFolders() {
        val out = parseFolders("""{"f:1":{"name":"One","apps":["a"]},"f:2":{"name":"Two","apps":[]}}""")
        assertEquals(mapOf("f:1" to Folder("One", listOf("a")), "f:2" to Folder("Two", emptyList())), out)
        assertEquals(emptyMap<String, Folder>(), parseFolders("{}"))
        assertThrows(Exception::class.java) { parseFolders("""{"f:1":{"apps":[]}}""") }
    }
}

class FineGridTest {
    private val pagesJson = """[{"items":[{"k":"a","x":1,"y":5,"w":1,"h":1},{"k":"w:3","x":0,"y":0,"w":4,"h":2}]},{"items":[{"k":"f:1","x":3,"y":2,"w":1,"h":1}]}]"""

    @Test fun oldLayoutIsDoubledAndMarked() {
        val out = fineGrid(mapOf("set.columns" to 5, "set.rows" to 7, "set.appSpan" to 1, "pages" to pagesJson, "dock" to "a\nb"))
        assertEquals(10, out["set.columns"])
        assertEquals(14, out["set.rows"])
        assertEquals(2, out["set.appSpan"])
        assertEquals("a\nb", out["dock"])
        val pages = parsePages(out["pages"] as String)
        assertEquals(listOf(Cell("a", 2, 10, 2, 2), Cell("w:3", 0, 0, 8, 4)), pages[0].items)
        assertEquals(listOf(Cell("f:1", 6, 4, 2, 2)), pages[1].items)
    }

    @Test fun missingSizesAndMarkerUseTheOldDefaults() {
        val out = fineGrid(mapOf("pages" to pagesJson))
        assertEquals(8, out["set.columns"])
        assertEquals(12, out["set.rows"])
        assertEquals(2, out["set.appSpan"])
        assertEquals(Cell("a", 2, 10, 2, 2), parsePages(out["pages"] as String)[0].items[0])
    }

    @Test fun freshInstallGetsTheDefaultGrid() {
        assertEquals(mapOf("set.columns" to 8, "set.rows" to 12, "set.appSpan" to 2), fineGrid(emptyMap()))
    }

    @Test fun markedLayoutStaysAsItIsAndMigrationRunsOnce() {
        val fine = mapOf("set.columns" to 9, "set.rows" to 13, "set.appSpan" to 2, "pages" to pagesJson)
        assertEquals(fine, fineGrid(fine))
        val once = fineGrid(mapOf("set.columns" to 4, "pages" to pagesJson))
        assertEquals(once, fineGrid(once))
    }

    @Test fun oldBackupRestoresDoubled() {
        val out = fineGrid(parseBackup(backupJson(mapOf("set.columns" to 4, "set.rows" to 6, "pages" to pagesJson))))
        assertEquals(8, out["set.columns"])
        assertEquals(Cell("w:3", 0, 0, 8, 4), parsePages(out["pages"] as String)[0].items[1])
    }
}
