package dev.omalauncher.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class IconPackAppfilterTest {
    @Test fun mapsComponentInfoItems() {
        val xml =
            """
            <?xml version="1.0"?>
            <resources>
              <item component="ComponentInfo{com.android.settings/com.android.settings.Settings}" drawable="settings" />
              <item component="ComponentInfo{com.example.app/.MainActivity}" drawable="example" />
            </resources>
            """.trimIndent()
        val map = IconPackAppfilter.parse(xml.byteInputStream())
        assertEquals("settings", map["com.android.settings/com.android.settings.Settings"])
        assertEquals("example", map["com.example.app/com.example.app.MainActivity"])
    }

    @Test fun ignoresNonItemTags() {
        val xml =
            """
            <resources>
              <iconback img1="back" />
              <item component="ComponentInfo{a/b}" drawable="d" />
            </resources>
            """.trimIndent()
        assertEquals(mapOf("a/b" to "d"), IconPackAppfilter.parse(xml.byteInputStream()))
    }

    @Test fun skipsIncompleteItems() {
        val xml =
            """
            <resources>
              <item component="ComponentInfo{a/b}" />
              <item drawable="only" />
              <item component="ComponentInfo{c/d}" drawable="ok" />
            </resources>
            """.trimIndent()
        assertEquals(mapOf("c/d" to "ok"), IconPackAppfilter.parse(xml.byteInputStream()))
    }
}
