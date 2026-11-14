package dev.omalauncher.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.omalauncher.R
import dev.omalauncher.Runtime
import dev.omalauncher.apps.AndroidApp
import dev.omalauncher.apps.IconOverrides
import dev.omalauncher.apps.IconPack
import dev.omalauncher.apps.IconPackPrompts
import dev.omalauncher.apps.iconComponentKey
import dev.omalauncher.storage.LauncherData
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.BracketAction
import dev.omalauncher.ui.Mono
import dev.omalauncher.ui.OverlayPanel
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.SectionLabel
import dev.omalauncher.ui.TextSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Apps the current icon pack doesn't cover, and the ones given another of its drawables.
 * A pick, a clear or an accepted suggestion moves the app between the two lists on this screen.
 */
@Composable
fun IconPackIconsScreen(
    runtime: Runtime,
    onDismiss: () -> Unit,
    onAgent: (title: String, command: (bin: String) -> String) -> Unit,
) {
    val context = LocalContext.current
    val overrides = runtime.androidApps.iconOverrides
    val apps = runtime.androidApps.apps
    val dpi = context.resources.displayMetrics.densityDpi
    val packName = runtime.style.style[Style.ICON_PACK]
    val loadedState =
        produceState<IconPack?>(null, packName) {
            value = packName?.let { pkg -> withContext(Dispatchers.IO) { IconPack.load(context, pkg) } }
        }
    val catalogState =
        produceState<List<String>?>(null, loadedState.value) {
            val pack = loadedState.value
            value = pack?.let { withContext(Dispatchers.IO) { it.catalog() } }
        }
    // revision is snapshot state; the override map is not. Reading it here is what moves an app between the lists.
    val overridden by remember {
        derivedStateOf {
            overrides.revision
            val pack = runtime.style.style[Style.ICON_PACK]
            if (pack == null) emptyList() else apps.filter { overrides.get(pack, iconComponentKey(it.component)) != null }
        }
    }
    val missing by remember {
        derivedStateOf {
            overrides.revision
            val pack = runtime.style.style[Style.ICON_PACK]
            val iconPack = loadedState.value
            if (pack == null || iconPack == null) {
                emptyList()
            } else {
                apps.filter { app -> overrides.get(pack, iconComponentKey(app.component)) == null && !iconPack.covers(app.component) }
            }
        }
    }
    var picking by remember { mutableStateOf<AndroidApp?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler {
        when {
            picking != null -> picking = null
            reviewing -> reviewing = false
            else -> onDismiss()
        }
    }
    val suggestTitle = stringResource(R.string.style_icon_pack_suggest)
    val chosen = picking
    val chosenPack = loadedState.value
    when {
        chosen != null && chosenPack != null ->
            DrawablePicker(
                pack = chosenPack,
                names = catalogState.value.orEmpty(),
                app = chosen,
                dpi = dpi,
                onDismiss = { picking = null },
                onPick = { name ->
                    val pack = runtime.style.style[Style.ICON_PACK]
                    if (pack != null) {
                        overrides.set(pack, iconComponentKey(chosen.component), name)
                        runtime.androidApps.reloadIcons()
                    }
                    picking = null
                },
                onClear = {
                    val pack = runtime.style.style[Style.ICON_PACK]
                    if (pack != null) {
                        overrides.set(pack, iconComponentKey(chosen.component), null)
                        runtime.androidApps.reloadIcons()
                    }
                    picking = null
                },
            )
        reviewing ->
            SuggestionsReview(
                runtime = runtime,
                pack = runtime.style.style[Style.ICON_PACK].orEmpty(),
                catalog = catalogState.value,
                dpi = dpi,
                iconPack = loadedState.value,
                onDismiss = { reviewing = false },
            )
        else ->
            OverlayPanel(stringResource(R.string.style_icon_pack_manage), onDismiss, onBack = onDismiss) {
                BasicText(
                    suggestTitle,
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val pack = loadedState.value ?: return@clickable
                            val name = runtime.style.style[Style.ICON_PACK] ?: return@clickable
                            val rows = missing.map { it.label to iconComponentKey(it.component) }
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    IconPackPrompts.writeBrief(
                                        File(LauncherData.styleDir(context), IconPackPrompts.BRIEF),
                                        name,
                                        rows,
                                        pack.catalog(),
                                    )
                                }
                                onAgent(suggestTitle) { bin -> IconPackPrompts.suggestMissingCommand(bin) }
                            }
                        }.padding(vertical = 10.dp),
                    style = Mono.copy(color = Palette.accent),
                )
                BasicText(
                    stringResource(R.string.style_icon_pack_review),
                    Modifier.fillMaxWidth().clickable { reviewing = true }.padding(vertical = 10.dp),
                    style = Mono.copy(color = Palette.accent),
                )
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (overridden.isNotEmpty()) {
                        item(key = "overridden") { SectionLabel(stringResource(R.string.style_icon_pack_overridden)) }
                        items(overridden, key = { "o-${it.key}" }) { app ->
                            OverrideRow(overrides, runtime.style.style[Style.ICON_PACK].orEmpty(), app, loadedState.value, dpi) {
                                if (loadedState.value != null) picking = app
                            }
                        }
                    }
                    item(key = "missing") { SectionLabel(stringResource(R.string.style_icon_pack_missing)) }
                    items(missing, key = { "m-${it.key}" }) { app ->
                        AppRow(app.label, runtime.androidApps.icons[app.key]) { if (loadedState.value != null) picking = app }
                    }
                }
            }
    }
}

@Composable
private fun OverrideRow(
    overrides: IconOverrides,
    pack: String,
    app: AndroidApp,
    iconPack: IconPack?,
    dpi: Int,
    onClick: () -> Unit,
) {
    // Subscribes to the counter so a new drawable shows without the row leaving the list.
    val drawable = overrides.revision.let { overrides.get(pack, iconComponentKey(app.component)) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (drawable != null && iconPack != null) PackDrawable(iconPack, drawable, dpi, 40.dp) else PackIcon(null, 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            BasicText(app.label, style = Mono.copy(color = Palette.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (drawable != null) BasicText(drawable, style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppRow(
    label: String,
    icon: ImageBitmap?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackIcon(icon, 40.dp)
        BasicText(label, Modifier.padding(start = 12.dp).weight(1f), style = Mono.copy(color = Palette.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DrawablePicker(
    pack: IconPack,
    names: List<String>,
    app: AndroidApp,
    dpi: Int,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    OverlayPanel(app.label, onDismiss, onBack = onDismiss) {
        BracketAction(stringResource(R.string.style_icon_pack_clear), Palette.accent, onClick = onClear)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(names, key = { it }) { name ->
                Box(Modifier.clickable { onPick(name) }.padding(4.dp), contentAlignment = Alignment.Center) {
                    PackDrawable(pack, name, dpi, 48.dp)
                }
            }
        }
    }
}

@Composable
private fun SuggestionsReview(
    runtime: Runtime,
    pack: String,
    catalog: List<String>?,
    dpi: Int,
    iconPack: IconPack?,
    onDismiss: () -> Unit,
) {
    val overrides = runtime.androidApps.iconOverrides
    var rows by remember(catalog) {
        mutableStateOf(
            catalog?.let { names ->
                val known = names.toHashSet()
                overrides.readSuggestions().filterValues { it in known }.map { it.key to it.value }
            },
        )
    }
    OverlayPanel(stringResource(R.string.style_icon_pack_review), onDismiss, onBack = onDismiss) {
        val shown = rows
        when {
            shown == null -> {}
            shown.isEmpty() -> BasicText(stringResource(R.string.style_icon_pack_no_suggestions), style = Mono.copy(color = Palette.muted))
            else -> {
                BasicText(
                    stringResource(R.string.style_icon_pack_accept_all),
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            overrides.putAll(pack, shown.toMap())
                            overrides.clearSuggestions()
                            runtime.androidApps.reloadIcons()
                            onDismiss()
                        }.padding(vertical = 10.dp),
                    style = Mono.copy(color = Palette.accent),
                )
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(shown, key = { it.first }) { (component, drawable) ->
                        val label = runtime.androidApps.apps.firstOrNull { iconComponentKey(it.component) == component }?.label ?: component
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    overrides.set(pack, component, drawable)
                                    overrides.dropSuggestion(component)
                                    rows = rows?.filterNot { it.first == component }
                                    runtime.androidApps.reloadIcons()
                                }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (iconPack != null) PackDrawable(iconPack, drawable, dpi, 40.dp) else PackIcon(null, 40.dp)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                BasicText(label, style = Mono.copy(color = Palette.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(drawable, style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackDrawable(
    pack: IconPack,
    name: String,
    dpi: Int,
    size: Dp,
) {
    val bitmap by produceState<ImageBitmap?>(null, pack, name, dpi) {
        value =
            withContext(Dispatchers.IO) {
                runCatching { pack.drawable(name, dpi)?.toIconBitmap(96) }.getOrNull()
            }
    }
    PackIcon(bitmap, size)
}

@Composable
private fun PackIcon(
    icon: ImageBitmap?,
    size: Dp,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.size(size)) else BasicText("·", style = Mono.copy(color = Palette.muted))
    }
}

private fun Drawable.toIconBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
