# Only Search

An Android launcher built around one search bar. Type to find apps, shortcuts, contacts and settings, or to calculate: maths, units, currencies (including crypto), dates and time zones, powered by [SoulverCore](https://soulver.app/core).

<p>
  <img src="docs/screenshots/home.png" width="200" alt="Home screen">
  <img src="docs/screenshots/drawer.png" width="200" alt="App drawer with groups">
  <img src="docs/screenshots/search.png" width="200" alt="Search results">
  <img src="docs/screenshots/calc.png" width="200" alt="Unit conversion">
</p>
<p>
  <img src="docs/screenshots/popup.png" width="200" alt="App menu">
  <img src="docs/screenshots/currency.png" width="200" alt="Currency conversion">
  <img src="docs/screenshots/edit-home.png" width="200" alt="Edit home pages">
  <img src="docs/screenshots/settings.png" width="200" alt="Settings">
</p>

## Features

- **Search** apps (prefix and fuzzy), app shortcuts, contacts, system settings, and the web. The top hit opens with ↵.
- **Calculator** in the search bar: arithmetic, percentages, money, units, live fiat and crypto rates, dates, times and time zones. Answers show while you type, with a history of past calculations.
- **Home pages** on a cell grid with widgets, a dock and page dots. Drag apps between cells, pages and the dock. Move and resize widgets.
- **Edit mode** (pinch in, or long-press → Edit home) to add, reorder, delete pages and choose the default one.
- **App menu** on long-press: shortcuts (drag one out to pin it), Info, Widgets, Rename, Hide, Uninstall.
- **App drawer** with frequent apps, custom groups and an A–Z scrubber.
- **Gestures**: swipe up/down, two-finger swipe, double-tap, pinch, Home press, each mapped to an action. Lock screen through an optional accessibility service.
- **Agenda widgets**: next event, a Today list, and a full-page Day timeline (events drawn to scale over an adjustable range, 4 hours back and 20 ahead by default). Each picks its own calendars.
- **Settings** for grid size, icon shape and size, dock and search bar position, accent colour, themed icons, search sources and calculator options. Backup and restore to a JSON file.

Contacts and calendar permissions are optional and asked only when a feature needs them. Internet is used only for currency rates.

## Install

Download the APK from [Releases](https://github.com/MrModest/only-search/releases) (arm64, Android 9+). Install it, press Home and choose Only Search as the default launcher.

## Build

The Swift bridge to SoulverCore is built in GitHub Actions (`.github/workflows/build.yml`). Every push to `main` produces a signed APK and runs the emulator tests. The screenshots above come from `ScreenshotTest`, run by the manual `screenshots` workflow. See [docs/ANDROID.md](docs/ANDROID.md) for the pipeline and [docs/DESIGN.md](docs/DESIGN.md) for the design.
