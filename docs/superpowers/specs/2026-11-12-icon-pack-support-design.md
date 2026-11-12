# Icon pack support — design

## Intent

Omalauncher should let you pick an installed Android icon pack in style
settings. That choice themes Android app icons everywhere the launcher
already draws them from the app catalog: the drawer / app picker, home app
tiles, the recent strip, long-press app menus, pin-shortcut previews that use
catalog icons, and widget `app:` images that go through the same path.

Default stays system icons. Pack choice is remembered until you change it or
the pack disappears.

For apps the pack does not map in `appfilter.xml`, the user can:

1. **Manually** pick a drawable from the pack, app by app
2. **Ask the default coding agent** to suggest pack icons for the gaps; the
   user reviews and keeps what they want

Both paths write the same per-pack override store.

## Why

Other launchers treat icon packs as a first-class style control. Omalauncher
already has monochrome silhouettes; packs are the colored counterpart for
people who install ADW/Nova/GO-compatible packs from Play. Omalauncher is
agent-first, so filling gaps should use the same default agent path as widget
work—not a separate cloud API.

## Goals

- Discover installed icon packs via the usual theme intents
- Persist the choice in `style.toml` as `icons.pack` (package name; empty =
  system)
- Resolve icons with `appfilter.xml` (`ComponentInfo{pkg/activity}` → drawable)
- Persist **per-pack** manual/AI overrides and apply them above appfilter
- Manual icon picker UI for any Android app while a pack is selected
- On-demand “suggest missing icons” via `Agents.default` (terminal + prompt);
  no agent → Agents settings screen (same as `withAgent`)
- Unmatched apps with no override keep system icons
- Changing pack (or uninstalling it) reloads catalog icons; overrides are
  keyed by pack so they do not leak across packs
- Monochrome mode still builds silhouettes from whatever drawable we use
- Test on the emulator only (no Fold 6 installs for this work)

## Non-goals

- Authoring or editing icon pack APKs
- Theming Linux apps, agent logos, or OWL-drawn art
- Wallpaper / Material You work
- Dynamic calendar-date or clock icons (v1)
- Generating icons for gaps via `iconback` / `iconmask` / `iconupon` bitmap
  compositing (v1)
- Auto-running the agent whenever a pack is selected (suggestions are
  on-demand only)
- A separate hosted AI / API key path outside the user’s default agent

## Prior art (builder context)

Open-source launchers (KISS, Flick, Lawnchair-family packs) all follow the
same contract for discovery and appfilter. Per-app overrides and agent fill
are Omalauncher-specific; agent sessions follow existing
`LauncherState.withAgent` / `WidgetPrompts` patterns (open default agent in a
terminal on a prepared prompt; structured results land in launcher config
files the app already watches or reloads).

We implement pack parsing ourselves in-tree (no new dependency).

## Behavior

### Selection and persistence

- New style setting: `icons.pack` (`Setting.Name`), default `""` (system)
- Style UI near the existing monochrome control: “System” plus each installed
  pack’s label
- Writing the setting triggers a full Android icon reload
- If the saved package is no longer installed when style/icons load: fall back
  to system icons for drawing; clear or ignore the stale value on next save
  (builder picks the quieter of the two)

### Resolution order (my call)

1. **Override** for `(packPackage, component)` if present  
2. Else **appfilter** hit → named drawable **as-is** (no iconback/mask/upon)  
3. Else **system** `LauncherActivityInfo.getIcon`

Mapped appfilter drawables are already finished icons in real packs;
iconback/mask/upon exist mainly to fabricate icons for unmapped apps, which
we handle via picker/agent instead.

### Overrides store

File under launcher config (guest-visible like other Omalauncher config), e.g.
`icon-overrides.toml`, keyed by pack package name then component → drawable
name. Manual picks and accepted AI suggestions write the same format.

### Manual picker

While a pack is selected, a style (or icons) screen lists Android apps (at
least those missing an appfilter hit; listing all is fine if simpler).
Choosing an app opens a grid of pack drawables (from the pack’s icon list /
`drawable.xml` / `appfilter` drawable names). Confirm writes an override and
reloads that icon.

### AI suggestions (default agent)

On-demand action: “suggest icons for apps missing from this pack”.

1. Require `runtime.agents.default`; else open Agents settings (`withAgent`)
2. Write a staging brief the agent can read: unmapped apps (label, package,
   component) + pack drawable catalog (names; labels if available)
3. Open the default agent in a terminal with a prompt that asks it to write
   suggested mappings into a known suggestions file (JSON or TOML) in launcher
   config—**not** silently into the live overrides file
4. When the user returns / taps review, show proposed pairs (app ↔ drawable
   preview); accept all / accept one / dismiss
5. Accepted rows merge into the overrides store and reload icons

The agent does not need network beyond whatever that agent already uses. Linux
must be installed for agents to run; without Linux, the action leads to Linux
/ Agents setup the same way other agent entry points do.

### What gets themed

| Surface | v1 |
| --- | --- |
| Android apps (drawer, home tiles, strip, `app:` widgets) | Pack / override / system |
| Pinned / offered `ShortcutInfo` icons | System shortcut artwork |
| Linux / agents / OWL | Unchanged |
| Monochrome | Silhouette from the drawable we chose |

### Profiles and clones

Badging (work / clone marks) stays in `AndroidApps.render` after the drawable
is chosen.

## Integration points

- **New:** `IconPackAppfilter`, `IconPackStore` (discover, appfilter, drawable
  catalog, `drawableFor`)
- **New:** overrides reader/writer + suggestions review state
- **New:** agent prompt helper (alongside `WidgetPrompts` style)
- **Modify:** `AndroidApps.loadIcon` — resolve override → appfilter → system
- **Modify:** `Style` + `StyleScreen` (+ strings in every `values-*`) — pack
  picker, entry to manual picker, “suggest missing” action
- **Modify:** pack change / uninstall → reload icons; uninstall clears active
  pack resolution (overrides for that pack may remain on disk unused)

## Existing installs

`icons.pack` absent → system. No overrides file → appfilter/system only. No
migration step; do not delete old readers.

## Success

On the emulator, with a pack installed (or a fixture):

1. Style shows System + the pack; selecting changes mapped icons
2. An unmapped app can get a manual pack icon; it persists for that pack
3. With a default agent available, “suggest missing” opens the agent; after
   suggestions exist, review can accept into overrides
4. Switching pack stops applying the previous pack’s overrides
5. Unit tests cover appfilter parsing, override precedence, and style round-trip

## Anti-goals

- Double-masking every mapped icon with iconback/upon
- Auto-applying agent output without review
- Touching the Fold 6 install while building this
- Blocking pack selection on Linux/agent availability (packs work without AI;
  AI is an optional fill for gaps)
