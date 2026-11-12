package dev.omalauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.omalauncher.AndroidApp
import dev.omalauncher.R
import dev.omalauncher.Runtime
import dev.omalauncher.theme.IconPackPrompts
import dev.omalauncher.theme.IconPackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manual icon picks and review of agent suggestions for the active icon pack.
 */
@Composable
fun IconPackIconsScreen(
    runtime: Runtime,
    onDismiss: () -> Unit,
    onAgent: (title: String, command: (bin: String) -> String) -> Unit,
) {
    val packs = runtime.iconPacks
    val pack = packs.packageName
    if (pack == null) {
        OverlayPanel(stringResource(R.string.style_icon_pack), onDismiss) {
            BasicText(stringResource(R.string.style_icon_pack_system), style = Mono.copy(color = Palette.muted))
        }
        return
    }
    var picking by remember { mutableStateOf<AndroidApp?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val apps = runtime.androidApps.apps.toList()
    val missing =
        remember(pack, apps, packs.catalogNames()) {
            apps.filter { app ->
                val key = IconPackStore.componentKey(app.component) ?: return@filter false
                runtime.iconOverrides.get(pack, key) == null && !packs.hasAppfilter(app.component)
            }
        }
    when {
        picking != null ->
            DrawablePicker(
                packs = packs,
                app = picking!!,
                onDismiss = { picking = null },
                onPick = { name ->
                    val app = picking!!
                    val key = IconPackStore.componentKey(app.component) ?: return@DrawablePicker
                    runtime.iconOverrides.set(pack, key, name)
                    picking = null
                    runtime.androidApps.reloadIcons()
                },
            )
        reviewing ->
            SuggestionsReview(
                runtime = runtime,
                pack = pack,
                packs = packs,
                onDismiss = { reviewing = false },
            )
        else ->
            OverlayPanel(stringResource(R.string.style_icon_pack_manage), onDismiss) {
                Column {
                    val suggestTitle = stringResource(R.string.style_icon_pack_suggest)
                    BasicText(
                        suggestTitle,
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val brief = File(runtime.iconOverrides.suggestionsPath()).resolveSibling("icon-suggest-brief.txt")
                                    val rows =
                                        missing.mapNotNull { app ->
                                            val key = IconPackStore.componentKey(app.component) ?: return@mapNotNull null
                                            app.label to key
                                        }
                                    withContext(Dispatchers.IO) {
                                        IconPackPrompts.writeBrief(brief, pack, rows, packs.catalogNames())
                                    }
                                    onAgent(suggestTitle) { bin ->
                                        IconPackPrompts.suggestMissingCommand(bin, brief, File(runtime.iconOverrides.suggestionsPath()))
                                    }
                                }
                            }
                            .padding(vertical = 10.dp),
                        style = Mono.copy(color = Palette.accent),
                    )
                    BasicText(
                        stringResource(R.string.style_icon_pack_review),
                        Modifier
                            .fillMaxWidth()
                            .clickable { reviewing = true }
                            .padding(vertical = 10.dp),
                        style = Mono.copy(color = Palette.accent),
                    )
                    SectionLabel(stringResource(R.string.style_icon_pack_missing))
                    LazyColumn {
                        items(missing, key = { it.key }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { picking = app }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PackIconBitmap(runtime.androidApps.icons[app.key], 40.dp)
                                BasicText(app.label, Modifier.padding(start = 12.dp), style = Mono.copy(color = Palette.foreground))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(text, Modifier.padding(top = 16.dp, bottom = 8.dp), style = Mono.copy(color = Palette.muted))
}

@Composable
private fun DrawablePicker(
    packs: IconPackStore,
    app: AndroidApp,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val names = packs.catalogNames()
    OverlayPanel(app.label, onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(names, key = { it }) { name ->
                val bmp by produceState<ImageBitmap?>(null, name) {
                    value = withContext(Dispatchers.IO) { packs.drawable(name)?.toBitmap(96, 96)?.asImageBitmap() }
                }
                Box(
                    Modifier
                        .clickable { onPick(name) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PackIconBitmap(bmp, 48.dp)
                }
            }
        }
    }
}

@Composable
private fun SuggestionsReview(
    runtime: Runtime,
    pack: String,
    packs: IconPackStore,
    onDismiss: () -> Unit,
) {
    val catalog = packs.catalogNames().toHashSet()
    val suggestions = remember { runtime.iconOverrides.readSuggestions().filterValues { it in catalog } }
    OverlayPanel(stringResource(R.string.style_icon_pack_review), onDismiss) {
        if (suggestions.isEmpty()) {
            BasicText(stringResource(R.string.style_icon_pack_no_suggestions), style = Mono.copy(color = Palette.muted))
            return@OverlayPanel
        }
        BasicText(
            stringResource(R.string.style_icon_pack_accept_all),
            Modifier
                .fillMaxWidth()
                .clickable {
                    runtime.iconOverrides.putAll(pack, suggestions)
                    runtime.iconOverrides.clearSuggestions()
                    runtime.androidApps.reloadIcons()
                    onDismiss()
                }
                .padding(vertical = 10.dp),
            style = Mono.copy(color = Palette.accent),
        )
        LazyColumn {
            items(suggestions.entries.toList(), key = { it.key }) { (component, drawable) ->
                val label =
                    runtime.androidApps.apps
                        .firstOrNull { IconPackStore.componentKey(it.component) == component }
                        ?.label ?: component
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            runtime.iconOverrides.set(pack, component, drawable)
                            runtime.androidApps.reloadIcons()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bmp by produceState<ImageBitmap?>(null, drawable) {
                        value = withContext(Dispatchers.IO) { packs.drawable(drawable)?.toBitmap(96, 96)?.asImageBitmap() }
                    }
                    PackIconBitmap(bmp, 40.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        BasicText(label, style = Mono.copy(color = Palette.foreground))
                        BasicText(drawable, style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIconBitmap(
    icon: ImageBitmap?,
    size: Dp,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.size(size))
        else BasicText("·", style = Mono.copy(color = Palette.muted))
    }
}
