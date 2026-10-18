# Project brief (as given by the user, 2026-10-18)

Goal: a **modern, minimal Android launcher with an iPhone Spotlight-like search experience**: search for and launch apps, with inline results from a powerful calculator.

Deliverables, in order:
1. HTML mock-ups of the main screens: Home, Application Drawer, Search results, Calculations. **Done, see [DESIGN.md](DESIGN.md).**
2. An installable, working APK. Any tooling needed to build and test may be installed.

Stack preference: **Kotlin + Jetpack Compose**.

## Calculator requirements

Must cover **the full breadth of SoulverCore's capabilities**, not just arithmetic:
- Natural-language-style expressions and contextual words.
- Dates, calendar arithmetic, durations, time-zone conversions.
- Currency and cryptocurrency conversions.
- Unit conversions, percentages, rates, financial calculations.
- Fast, reliable, local evaluation suitable for search-as-you-type.

**Hard constraint:** limited arithmetic engines are not acceptable substitutes. **Do not suggest Fend** (also referred to as "fest").

Documented SoulverCore expressions (use as acceptance examples):
```text
65 kg in pounds
40 is what % of 90
$150 is 25% on what
3:45pm + 4 hr 10 min
9:35am in New York to Japan
January 30 2020 + 3 months 2 weeks 5 days
$25k over 10 years at 7.5%
$10 for lunch + 15% tip
$25/hour * 14 hours of work
```

Other documented capabilities: variables (incl. multi-word names), custom units and natural-language functions, configurable formatting, locale-sensitive number/date parsing, English plus DE/RU/FR/ES/IT/JA/KO/zh-Hans (additive to English), natural-language date parsing APIs, currency-rate provider integration (standard customization has hard-coded rates; an included ECB provider updates 33 fiat currencies; other live coverage needs a provider).

Vendor performance figures (not Android measurements): most expressions < 0.5 ms; 7,000+ calcs/s on Apple silicon.

Licensing: personal/private use permitted; contact vendor for public/commercial projects. Terms for this launcher unconfirmed.

## Raycast background

- Reverse-engineering report for Raycast Beta 0.60.0 lists `calculator-worker.mjs`, `Calculator.node`, `SoulverCore.framework/` (native addon bridges to SoulverCore, calculation runs in a worker thread). Source: https://github.com/yetone/native-feel-skill/blob/master/references/07-evidence-raycast.md
- No verified public reconstruction of Raycast's calculator engine. `4evy/raycast` `src/native/calculator.mts` only has token/result/history interface types: https://github.com/4evy/raycast/blob/master/src/native/calculator.mts

## SoulverCore on Android

- Engine: https://github.com/soulverteam/SoulverCore (closed source Swift, compiled libraries; repo holds integration docs + samples).
- Multiplatform: https://github.com/soulverteam/SoulverCore-Multiplatform lists **Android aarch64 and x86_64** as `.so` shared libraries, built with the official **Swift SDK for Android, Swift 6.3+**. Release zips (Releases page) contain the dynamic library plus the **`SoulverCore_SoulverCore.resources`** bundle, required at runtime.
- Swift Android docs (shared-library packaging, `swift-java` interop): https://www.swift.org/documentation/articles/swift-sdk-for-android-getting-started.html

Checked 2026-10-18 on the Multiplatform README, which does **not** state: a Kotlin/JNI wrapper or C API, required Swift runtime libs (libswiftCore, Foundation, ICU…), minimum Android API, or how to place/load the resources bundle on Android.

Still unverified: 16 KB page-size compatibility, Android feature parity, locale/time-zone behavior, cold init and warm eval latency, memory use, APK size impact, licensing for a public launcher.
