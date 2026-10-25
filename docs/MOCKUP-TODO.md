# Mock-up 2026-10-24: remaining features

Each item is checked only after a UI test on the emulator.

- [x] 1. Settings hub (search settings, sections, entry from home menu)
- [x] 2. Settings · Home screen (columns, rows, icon size, icon shape, labels, lock layout)
- [x] 3. Home pages on a cell grid + page dots + edit mode (carousel, add/delete/reorder pages, default page, Wallpaper / Widgets / Layout / Settings, pinch in)
- [x] 3b. Drag & drop (user request): move apps on the grid, between pages and to/from the dock, drag from the drawer; move and resize widgets
- [x] 4. App context menu extras (home dim, pointer tail, Widgets, Edit, Hide, drag a shortcut out to pin it)
- [x] 5. Widget picker: hold & drag to place
- [x] 6. Drawer groups (All / custom tabs, new group, ⋮ options) + Settings · App drawer & folders
- [x] 7. Search: Settings and People results, top hit "opened … ago", web search + Settings · Sources & ranking
- [x] 8. Settings · Search & calculator (preview, query types, compute time, defaults) + live currency rates
- [x] 9. Settings · Dock & search bar
- [x] 10. Settings · Gestures (all mappings, lock screen via accessibility, notifications, type anywhere, reset query)
- [x] 11. Settings · Look & feel (accent, themed icons)
- [x] 12. Next calendar event under the clock + Today card
- [x] 13. Settings · Backup & restore (JSON file)

## Next (user requests, 2026-10-25)

- [x] 14. Calendar widget settings: choose which calendars it shows
- [x] 15. Built-in home widgets become normal widgets, added from the widget menu
- [x] 16. Long-press a widget: "Widget settings" (reconfigure) like Nova
- [x] 17. Smaller widget padding, so the widget gets the space
- [x] 18. Near-square widget corners (only the search bar stays round)
- [x] 19. Home folders (needed for the Nova import)
- [x] 20. Import from Nova Launcher (.novabackup via file picker): pages, icons, widgets, folders, dock, drawer groups, hidden apps, matching settings. Half-cell layouts import on a doubled grid (5×7 → 10×14). Test file: docs/2026-10-25_07-41.novabackup

Decisions: the clock, next-event line and Today card all become built-in widgets in the picker.

Notes:
- Crypto: SoulverCore never asks the rate provider about crypto, so the bridge registers crypto as custom units priced in USD (BTC, ETH, SOL, XRP, DOGE, ADA, LTC, USDT, BNB, DOT).
- Weather is left out on purpose; the "Next event" built-in widget shows the next calendar event.
- Built-in widgets are grid cells keyed "b:<kind>:<n>" (clock, event, today); an upgrade moves the old clock/agenda rows into the grid and adds rows for them.
- Home folders are cells keyed "f:<n>", stored in the "folders" pref. Dropping an app on an app makes a folder (this replaced swapping).
- Nova import: the backup's widgets are queued in "novaWidgets" and bound one by one after the restart. Apps take `set.appSpan` cells (2 on a doubled grid).
