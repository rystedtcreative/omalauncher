package dev.omalauncher.theme

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses an icon pack's appfilter.xml into flattened component → drawable name.
 * Keys are `package/activity` with no `ComponentInfo{}` wrapper; a leading `.`
 * on the activity is expanded to the package.
 *
 * [parse] on an [InputStream] uses a tag scanner so JVM unit tests need no
 * Android XmlPullParser; [parse] on an [XmlPullParser] is for `res/xml` on device.
 */
object IconPackAppfilter {
    private val COMPONENT_INFO = Regex("""^ComponentInfo\{([^}/]+)/([^}]+)\}$""")
    private val ITEM = Regex("""<item\b([^>]*?)\/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ATTR = Regex("""([A-Za-z_:][\w:.-]*)\s*=\s*"([^"]*)"""")

    fun parse(xml: InputStream): Map<String, String> {
        val text = xml.bufferedReader().use { it.readText() }
        val out = linkedMapOf<String, String>()
        for (match in ITEM.findAll(text)) {
            val attrs = ATTR.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val component = attrs["component"]
            val drawable = attrs["drawable"]?.trim()?.takeIf { it.isNotEmpty() }
            if (component != null && drawable != null) {
                flatten(component)?.let { out[it] = drawable }
            }
        }
        return out
    }

    fun parse(xpp: XmlPullParser): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var event = xpp.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xpp.name == "item") {
                val component = xpp.getAttributeValue(null, "component")
                val drawable = xpp.getAttributeValue(null, "drawable")?.trim()?.takeIf { it.isNotEmpty() }
                if (component != null && drawable != null) {
                    flatten(component)?.let { out[it] = drawable }
                }
            }
            event = xpp.next()
        }
        return out
    }

    /** `package/fully.qualified.Activity`, or null if [raw] is not a usable component string. */
    fun flatten(raw: String): String? {
        val inner =
            COMPONENT_INFO.matchEntire(raw.trim())?.destructured?.let { (pkg, act) -> pkg to act }
                ?: raw.trim().takeIf { '/' in it }?.let {
                    val i = it.indexOf('/')
                    it.substring(0, i) to it.substring(i + 1)
                } ?: return null
        val (pkg, act) = inner
        if (pkg.isEmpty() || act.isEmpty()) return null
        val activity = if (act.startsWith('.')) pkg + act else act
        return "$pkg/$activity"
    }
}
