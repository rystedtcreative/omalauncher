package dev.omalauncher.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IconOverridesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripsPerPackOverrides() {
        val store = IconOverrides(tmp.root)
        store.set("com.pack.a", "com.app/com.app.Main", "icon_a")
        store.set("com.pack.b", "com.app/com.app.Main", "icon_b")
        assertEquals("icon_a", store.get("com.pack.a", "com.app/com.app.Main"))
        assertEquals("icon_b", store.get("com.pack.b", "com.app/com.app.Main"))
        val again = IconOverrides(tmp.root)
        assertEquals("icon_a", again.get("com.pack.a", "com.app/com.app.Main"))
        assertNull(again.get("com.pack.a", "missing/x"))
    }

    @Test fun packIsolation() {
        val store = IconOverrides(tmp.root)
        store.set("com.pack.a", "com.app/act", "one")
        assertNull(store.get("com.pack.other", "com.app/act"))
        assertEquals(mapOf("com.app/act" to "one"), store.forPack("com.pack.a"))
    }

    @Test fun parsesSuggestionRows() {
        val text =
            """
            [[override]]
            component = "com.app/com.app.Main"
            drawable = "suggested"
            """.trimIndent()
        assertEquals(
            mapOf("com.app/com.app.Main" to "suggested"),
            IconOverrides.parseSuggestionMap(Toml.parse(text)),
        )
    }

    @Test fun removingClearsEntry() {
        val store = IconOverrides(tmp.root)
        store.set("com.pack", "a/b", "d")
        store.set("com.pack", "a/b", null)
        assertNull(store.get("com.pack", "a/b"))
    }
}
