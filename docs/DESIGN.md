# UI design decision

**Decision (2026-10-18): direction D "Merged" won.** Mock-up: [`prototype/mockups.html`](../prototype/mockups.html), which opens on D by default (`#d`). A, B and C are kept in the same file as rejected references. Published copy: https://claude.ai/artifact/S5WgbqSAMfz3R2NKnzpenx (private).

The prototype is throwaway HTML. Rebuild the screens properly in Compose; don't port the markup.

## How we got here
- A "Glass Spotlight" (iOS-like icon grid, search on top): user liked the **home screen with apps and widgets**.
- B "Plain Text" (no icons, scratchpad calc): not chosen.
- C "Command Bar" (Raycast-like): user liked the **search bar always at the bottom, within thumb reach**, and the list-style results.
- User feedback: **hates big padding and round corners.** Keep padding minimal and corners close to square, but not fully square.
- User feedback on D: put the home search bar **above the pinned app row (dock)**, not below it.

## Style rules
- Padding 6–10 px at the screen edges and inside blocks; sections stacked tightly.
- Corner radius: about 3 px on keys and kbd hints, 4–6 px on widgets, bars and the calc card, 8–9 px on app icons (soft square). No pills, no large rounded cards.
- Home and drawer sit on the wallpaper. Widgets use a translucent dark blur. Search and calc use a solid dark sheet (#17191c), with an amber accent (#f5b04a) for the caret, the selected row and the calc answer.
- Fonts in the mock: Figtree (UI), IBM Plex Mono (labels, numbers, section headers).

## Screens (D)
1. **Home**, top to bottom: status bar; clock (large) with date and weather on the right; 2 half-width widgets (today's agenda, now playing); full-width hourly weather strip; flexible space; 4×2 app grid; **search bar** ("Search apps, math, dates", with an "↑ apps" hint); **dock row** of 4 apps.
2. **App drawer**: dark blurred scrim over the wallpaper; "Frequent" row of 5; A–Z sections in a 5-column grid with letter headers; A–Z scrubber on the right edge; **filter bar at the bottom**, in the same position as the home search bar.
3. **Search results** (query "ca"): solid dark sheet. "Apps" section as a list (small icon, name with the match underlined); first row selected with "↵ Open". "Shortcuts" section (app shortcuts, e.g. Calendar › New event, Camera › Selfie). Search bar at the bottom, directly above the keyboard, with a "⋯ actions" hint.
4. **Calculations** (query "65 kg in pounds"): "Calculator" section with eval time (e.g. 0.3 ms). Card shows `65 kg (Kilograms) → 143.3 lb (Pounds)`; table of alternative units (stones, ounces, grams); "History" list of past expressions → results; footer actions "↵ Copy answer" / "⇧↵ Copy expression"; search bar above the keyboard.

Calc values in the mock are hand-worked, not SoulverCore output. Currency and crypto use placeholder rates.

## Next step: APK
Plan, not started yet:
1. Install the Android SDK/NDK, Gradle, a JDK, an emulator image (x86_64), and the Swift 6.3+ toolchain with the Swift SDK for Android.
2. Download the SoulverCore-Multiplatform Android release (`.so` and `SoulverCore_SoulverCore.resources`). Work out its runtime dependencies (`readelf -d`).
3. Write a small Swift shim that exposes `evaluate(String) -> String` (plus init with the resources path) over JNI, and package it with the Swift runtime `.so` files in `jniLibs/`. Ship the resources bundle as assets, copied to `filesDir` on first run.
4. Kotlin + Compose launcher (HOME intent filter): app list via `LauncherApps`, fuzzy search, app shortcuts, calc evaluated off the main thread on each keystroke.
5. Measure cold init, warm eval latency, memory and APK size on the emulator. Check 16 KB page alignment.
