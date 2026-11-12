# Icon pack support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users pick an installed Android icon pack, apply appfilter mappings, fill gaps with a manual picker and default-agent suggestions (reviewed), and show the result on all catalog Android app icons.

**Architecture:** `IconPackStore` discovers packs and parses `appfilter` + drawable catalogs. Resolution is override → appfilter → system inside `AndroidApps`. Overrides live in a per-pack TOML under launcher config. AI fill opens `Agents.default` via `withAgent` with a prompt that writes a suggestions file; a review UI merges accepts into overrides.

**Tech Stack:** Kotlin, PackageManager/Resources, Style/Toml, Compose, existing Linux agent terminal prompts, JVM unit tests.

**Spec:** [docs/superpowers/specs/2026-11-12-icon-pack-support-design.md](../specs/2026-11-12-icon-pack-support-design.md)

## Global Constraints

- Emulator-only installs; never `installDebug` on the Fold 6
- No new third-party icon-pack library; no separate cloud AI API
- Appfilter hits use named drawable as-is (no iconback/mask/upon)
- AI suggestions on-demand only; never auto-apply without review
- UI strings in all style locales; German lower-case / du
- Existing installs without `icons.pack` / overrides keep working
- Follow `AGENTS.md`

## Review Focus

- `assets/appfilter.xml`-only packs still load
- `ComponentInfo{…}` and `.ShortActivity` both match
- Override for pack A never applies while pack B is selected
- Agent absent → Agents screen; suggestions file malformed → no crash, empty review
- Monochrome still masks pack/override drawables

## File structure

| File | Role |
| --- | --- |
| `theme/IconPackAppfilter.kt` | Parse appfilter |
| `theme/IconPackStore.kt` | Discover, catalog, resolve drawable |
| `theme/IconOverrides.kt` | Per-pack override + suggestions IO |
| `theme/Style.kt` | `icons.pack` |
| `widgets/IconPackPrompts.kt` (or under `theme/`) | Default-agent prompt for suggestions |
| `AndroidApps.kt` | Resolve drawable order |
| `ui/StyleScreen.kt` | Pack picker + entry points |
| `ui/IconPackIconsScreen.kt` (name flex) | Manual picker + suggestion review |
| `strings_style.xml` (+ locales) | Copy |
| tests | Parser, overrides precedence, style round-trip |

---

### Task 1: Appfilter parser + Style setting

**Files:** `IconPackAppfilter.kt`, tests, `Style.kt`, `StyleTest.kt`

**Produces:** `IconPackAppfilter.parse(InputStream): Map<String,String>`; `Style.ICON_PACK`

- [ ] Failing parser tests (ComponentInfo, `.MainActivity`, ignore iconback)
- [ ] Implement parse + `ICON_PACK`
- [ ] Tests green; commit

---

### Task 2: IconPackStore (discover + resolve + drawable catalog)

**Produces:** `installedPacks()`, `setPackage`, `drawableFor(ComponentName)`, `drawableNames(): List<String>` (for picker/AI)

Discovery: `org.adw.launcher.THEMES`, `com.gau.go.launcherex.theme`, `com.novalauncher.THEME`

- [ ] Tests for empty pack / map hit seam
- [ ] Implement; commit

---

### Task 3: Overrides store + resolution in AndroidApps

**Produces:** read/write `icon-overrides.toml` keyed by pack; resolution override → appfilter → system; reload on change

- [ ] Unit tests: precedence and pack isolation
- [ ] Wire `AndroidApps` app icon load (not ShortcutInfo)
- [ ] Commit

---

### Task 4: Style pack picker UI + strings

- [ ] Localized strings; StyleScreen chooser near monochrome
- [ ] Changing pack calls store + reload
- [ ] assembleDebug + lintDebug; commit

---

### Task 5: Manual per-app icon picker

- [ ] Screen: apps (missing-first or all) → grid of pack drawables → save override → reload one/all icons
- [ ] Entry from style when a pack is selected
- [ ] Emulator smoke; commit

---

### Task 6: Default-agent suggestions + review UI

**Produces:** staging brief + `IconPackPrompts.suggestMissingCommand(bin)`; suggestions file; review accept → overrides

- [ ] Prompt asks agent to write only the suggestions file (path in launcher config)
- [ ] “Suggest missing” uses `withAgent`; no default → Agents screen
- [ ] Review UI: preview pairs, accept one/all/dismiss
- [ ] Emulator check with agent if available; without agent, confirm Agents redirect
- [ ] Full AGENTS.md gradle targets; commit

---

## Done when

- Spec v1 behaviors work on emulator
- Overrides and AI review share one store
- Pack works without Linux/agent; AI is optional fill
- CI gradle targets green
