package dev.omalauncher.theme

import java.io.File

/**
 * Per-pack drawable overrides for components the appfilter missed or the user
 * remapped. Stored as `[[override]]` tables so component names (with `/`) fit
 * the launcher's TOML subset:
 *
 * ```
 * [[override]]
 * pack = "com.example.iconpack"
 * component = "com.app/com.app.Main"
 * drawable = "some_drawable"
 * ```
 *
 * Agent suggestions use the same shape in [SUGGESTIONS] until accepted here.
 */
class IconOverrides(
    dir: File,
) {
    private val file = File(dir, FILE)
    private val suggestionsFile = File(dir, SUGGESTIONS)

    /** pack → (component → drawable). */
    private var table = LinkedHashMap<String, LinkedHashMap<String, String>>()

    init {
        load()
    }

    fun get(
        pack: String,
        component: String,
    ): String? = table[pack]?.get(component)

    fun forPack(pack: String): Map<String, String> = table[pack]?.toMap() ?: emptyMap()

    fun set(
        pack: String,
        component: String,
        drawable: String?,
    ) {
        val section = table.getOrPut(pack) { linkedMapOf() }
        if (drawable.isNullOrBlank()) section.remove(component) else section[component] = drawable.trim()
        if (section.isEmpty()) table.remove(pack)
        save()
    }

    fun putAll(
        pack: String,
        mappings: Map<String, String>,
    ) {
        if (mappings.isEmpty()) return
        val section = table.getOrPut(pack) { linkedMapOf() }
        for ((c, d) in mappings) {
            val drawable = d.trim()
            if (drawable.isNotEmpty()) section[c] = drawable
        }
        save()
    }

    fun readSuggestions(): Map<String, String> {
        val text = runCatching { suggestionsFile.readText() }.getOrNull() ?: return emptyMap()
        return runCatching { parseSuggestionMap(Toml.parse(text)) }.getOrDefault(emptyMap())
    }

    fun clearSuggestions() {
        suggestionsFile.delete()
    }

    fun suggestionsPath(): String = suggestionsFile.absolutePath

    fun reload() = load()

    private fun load() {
        table = LinkedHashMap()
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val parsed = runCatching { Toml.parse(text) }.getOrNull() ?: return
        for (row in rows(parsed["override"])) {
            val pack = row["pack"] as? String ?: continue
            val component = row["component"] as? String ?: continue
            val drawable = (row["drawable"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            table.getOrPut(pack) { linkedMapOf() }[component] = drawable
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val sb = StringBuilder("# Icon pack overrides: pack + component → drawable name.\n")
        for ((pack, section) in table.toSortedMap()) {
            for ((component, drawable) in section.toSortedMap()) {
                sb.append("[[override]]\n")
                sb.append("pack = ").append(Toml.format(pack)).append('\n')
                sb.append("component = ").append(Toml.format(component)).append('\n')
                sb.append("drawable = ").append(Toml.format(drawable)).append('\n')
                sb.append('\n')
            }
        }
        file.writeText(sb.toString())
    }

    companion object {
        const val FILE = "icon-overrides.toml"
        const val SUGGESTIONS = "icon-suggestions.toml"

        /**
         * Suggestions may be `[[override]]` rows (component + drawable; pack optional)
         * or a flat `component = "drawable"` map.
         */
        fun parseSuggestionMap(parsed: Map<String, Any>): Map<String, String> {
            val out = linkedMapOf<String, String>()
            for (row in rows(parsed["override"])) {
                val component = row["component"] as? String ?: continue
                val drawable = (row["drawable"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                out[component] = drawable
            }
            for ((k, v) in parsed) {
                if (k == "override" || v !is String || v.isBlank()) continue
                out[k] = v.trim()
            }
            return out
        }

        @Suppress("UNCHECKED_CAST")
        private fun rows(value: Any?): List<Map<String, Any>> =
            when (value) {
                is List<*> -> value.mapNotNull { it as? Map<String, Any> }
                is Map<*, *> -> listOf(value as Map<String, Any>)
                else -> emptyList()
            }
    }
}
