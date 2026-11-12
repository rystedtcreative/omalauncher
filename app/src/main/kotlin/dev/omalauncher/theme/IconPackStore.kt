package dev.omalauncher.theme

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log

data class IconPackInfo(
    val packageName: String,
    val label: String,
)

/**
 * Discovers installed ADW/GO/Nova-style icon packs, loads their appfilter and
 * drawable catalog, and resolves drawables for a [ComponentName]. Overrides
 * from [IconOverrides] win over appfilter hits.
 */
class IconPackStore(
    private val context: Context,
    private val overrides: IconOverrides,
) {
    /** Active pack package, or null for system icons. */
    var packageName by mutableStateOf<String?>(null)
        private set

    private var appfilter: Map<String, String> = emptyMap()
    private var catalog: List<String> = emptyList()
    private var packResources: Resources? = null

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (action in THEME_INTENTS) {
            val intent = Intent(action)
            for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                val pkg = ri.activityInfo.packageName
                if (pkg in seen) continue
                val label = ri.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                seen[pkg] = IconPackInfo(pkg, label)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Activates [pkg] (null or blank = system). Reloads appfilter and catalog.
     * Returns false if [pkg] was set but could not be loaded (falls back to system).
     */
    fun setPackage(pkg: String?): Boolean {
        val want = pkg?.trim()?.takeIf { it.isNotEmpty() }
        if (want == null) {
            clearPack()
            return true
        }
        return loadPack(want).also { ok ->
            if (!ok) clearPack()
        }
    }

    fun catalogNames(): List<String> = catalog

    fun appfilterOf(component: ComponentName): String? = appfilter[componentKey(component)]

    /** Override, then appfilter; null means use the system icon. */
    fun drawableNameFor(component: ComponentName): String? {
        val pack = packageName ?: return null
        val key = componentKey(component) ?: return null
        return overrides.get(pack, key) ?: appfilter[key]
    }

    fun drawableFor(component: ComponentName): Drawable? {
        val name = drawableNameFor(component) ?: return null
        return drawable(name)
    }

    fun drawable(name: String): Drawable? {
        val res = packResources ?: return null
        val pack = packageName ?: return null
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    fun hasAppfilter(component: ComponentName): Boolean = appfilterOf(component) != null

    private fun clearPack() {
        packageName = null
        appfilter = emptyMap()
        catalog = emptyList()
        packResources = null
    }

    private fun loadPack(pkg: String): Boolean {
        val pm = context.packageManager
        val res =
            runCatching { pm.getResourcesForApplication(pkg) }.getOrElse {
                Log.w(TAG, "cannot load resources for $pkg", it)
                return false
            }
        val filter = loadAppfilter(res, pkg)
        if (filter.isEmpty()) Log.w(TAG, "no appfilter.xml in $pkg")
        val names =
            loadDrawableCatalog(res, pkg)
                .plus(filter.values)
                .distinct()
                .sorted()
        packageName = pkg
        packResources = res
        appfilter = filter
        catalog = names
        return true
    }

    private fun loadAppfilter(
        res: Resources,
        pkg: String,
    ): Map<String, String> {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        if (id != 0) {
            runCatching { IconPackAppfilter.parse(res.getXml(id)) }.getOrNull()?.let { return it }
        }
        return runCatching { res.assets.open("appfilter.xml").use { IconPackAppfilter.parse(it) } }.getOrDefault(emptyMap())
    }

    private fun loadDrawableCatalog(
        res: Resources,
        pkg: String,
    ): List<String> {
        for (name in listOf("drawable", "iconpack")) {
            val id = res.getIdentifier(name, "xml", pkg)
            if (id != 0) {
                runCatching { parseDrawableXml(res.getXml(id)) }.getOrNull()?.let { return it }
            }
            runCatching { res.assets.open("$name.xml").use { parseDrawableNames(it) } }.getOrNull()?.let { return it }
        }
        return emptyList()
    }

    private fun parseDrawableXml(xpp: org.xmlpull.v1.XmlPullParser): List<String> {
        val out = linkedSetOf<String>()
        var event = xpp.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && (xpp.name == "item" || xpp.name == "icon")) {
                xpp.getAttributeValue(null, "drawable")?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
            }
            event = xpp.next()
        }
        return out.toList()
    }

    companion object {
        private const val TAG = "IconPackStore"

        val THEME_INTENTS =
            listOf(
                "org.adw.launcher.THEMES",
                "com.gau.go.launcherex.theme",
                "com.novalauncher.THEME",
            )

        fun componentKey(component: ComponentName): String? =
            IconPackAppfilter.flatten("${component.packageName}/${component.className}")

        private val DRAWABLE_ITEM = Regex("""<(?:item|icon)\b([^>]*?)\/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val ATTR = Regex("""([A-Za-z_:][\w:.-]*)\s*=\s*"([^"]*)"""")

        fun parseDrawableNames(xml: java.io.InputStream): List<String> {
            val text = xml.bufferedReader().use { it.readText() }
            val out = linkedSetOf<String>()
            for (match in DRAWABLE_ITEM.findAll(text)) {
                val attrs = ATTR.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
                attrs["drawable"]?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
            }
            return out.toList()
        }
    }
}
