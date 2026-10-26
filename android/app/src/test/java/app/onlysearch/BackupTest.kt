package app.onlysearch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val pagesJson = """[{"items":[{"k":"a","x":0,"y":1,"w":1,"h":2}]},{"items":[]}]"""

    @Test fun roundTripKeepsEveryType() {
        val all = mapOf("s" to "text\nwith\"quotes", "i" to 7, "l" to (1L shl 40), "b" to true, "f" to 1.5f, "pages" to pagesJson)
        assertEquals(all, parseBackup(backupJson(all)))
    }

    @Test fun skipsRatesAndUnknownTypes() {
        val out = parseBackup(backupJson(mapOf("rates" to "x", "ratesAt" to 1L, "set" to setOf("a"), "keep" to 1)))
        assertEquals(mapOf("keep" to 1), out)
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
        assertThrows(IllegalArgumentException::class.java) { parseBackup(wrap("""{"k":{"t":"x","v":1}}""")) }
        assertThrows(Exception::class.java) { parseBackup(wrap("""{"k":{"t":"i"}}""")) }
        assertThrows(Exception::class.java) { parseBackup(wrap("""{"pages":{"t":"s","v":"[{\"items\":[{\"k\":\"a\"}]}]"}}""")) }
        assertThrows(Exception::class.java) { parseBackup(wrap("""{"groups":{"t":"s","v":"oops"}}""")) }
    }

    @Test fun validBackupWithLayoutPrefsPasses() {
        val folders = """{"f:1":{"name":"F","apps":["a","b"]}}"""
        val groups = """[{"name":"G","apps":["a"]}]"""
        val out = parseBackup(backupJson(mapOf("pages" to pagesJson, "folders" to folders, "groups" to groups)))
        assertFalse(out.isEmpty())
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
