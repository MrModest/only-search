# Android build and SoulverCore integration

Status (2026-10-18): working APK built by GitHub Actions (`.github/workflows/build.yml`) in the private repo `MrModest/only-search`.
The local dev machine is too small to build (3 GB RAM, ~3.5 GB free disk, no KVM), so **all builds and emulator tests run in CI**.

## Getting the APK
Pre-release with the arm64 APK: https://github.com/MrModest/only-search/releases/tag/v0.1.0 (user's phone: Pixel 9 Pro, Android 17).
Or `gh run download <run> -n only-search-apk` after a green run. Emulator screenshots and SoulverCore timings: `-n emulator-results-34` / `-n emulator-results-37.2`.

CI emulators: Android 14 (required) and Android 17 / 37.2 with 16 KB pages (best-effort, it has timed out while booting on every run so far). Both use the Pixel 6 AVD resized to the Pixel 9 Pro screen (1280x2856 @ 495 dpi) because the runner has no pixel_9_pro profile.

## Pipeline
1. Swift **6.3.3** toolchain + Swift SDK for Android 6.3.3 (must match the compiler SoulverCore 3.5.1 was built with), NDK **r27d** (27.3.13750724), linked via the bundle's `swift-android/scripts/setup-android-sdk.sh`.
2. `bridge/build.sh`: downloads the SoulverCore-Multiplatform 3.5.1 Android zips, builds `bridge/` (SwiftPM, `--swift-sdk <triple>` e.g. `aarch64-unknown-linux-android28`; in 6.3 the SDK id is the triple), then `bridge/collect_deps.py` copies every NEEDED Swift runtime `.so` + `libc++_shared.so` into `android/app/src/main/jniLibs/<abi>/`, and the resources bundle into `assets/soulver/`.
3. Gradle 8.11.1 / AGP 8.7.3 / Kotlin 2.1.0 / Compose BOM 2024.12.01 builds per-ABI APKs (arm64-v8a, x86_64), release build signed with the debug key.
4. Emulator job (API 34 x86_64): runs `SoulverTest` (documented examples + timings), takes screenshots of home, drawer, search (`--es query ca`), calc.

## Facts verified on Android (answers to BRIEF.md "still unverified")
- SoulverCore has **no C/JNI API**; only Swift. We wrap it in our own Swift `@_cdecl` JNI functions (`bridge/Sources/SoulverBridge/Bridge.swift`), importing `<jni.h>` through a tiny C target.
- Built for **API 28+**, NDK r27d, ELF segments **16 KB aligned**.
- Runtime deps: libswiftCore, _Concurrency, _StringProcessing, RegexBuilder, _RegexParser, _Builtin_float, _math, Synchronization, swiftAndroid, BlocksRuntime, dispatch, swiftDispatch, Foundation, FoundationEssentials, FoundationInternationalization (+ their transitive deps, e.g. _FoundationICU) and libc++_shared.
- **Resources bundle gotcha:** passing `ResourceBundle(url:)` is not enough; SoulverCore also goes through SwiftPM's `Bundle.module`, which looks next to the main executable (`/system/bin/app_process`) and fatal-errors otherwise. Fix: before init, overwrite CoreFoundation's cached process path via `_CFGetProcessPath()` (dlsym from libFoundation.so) to `<filesDir>/onlysearch`, and unpack the bundle to `<filesDir>/SoulverCore_SoulverCore.resources`.
- Time zone: we `Os.setenv("TZ", <device tz>)` before loading; "9:35am in New York to Japan" → 10:35 PM is correct.
- APK size: 84.5 MB arm64 (lib_FoundationICU.so alone is 40 MB, stored uncompressed). Compressing native libs or trimming ICU data could shrink it.
- Measured on the CI emulator (x86_64, software GPU, slow): init ~380 ms, first eval ~670 ms (now warmed up during init), warm evals ~0.7–8 ms (500-eval average 7.7 ms). Real-device numbers still to measure.
- Currency: SoulverCore's built-in standard rates don't cover crypto, so the bridge sets its own `CurrencyRateProvider` (`Rates` in `Bridge.swift`). Kotlin (`Soulver.refreshRates`) fetches fiat from open.er-api.com and the top 10 crypto from CoinGecko (no keys) when the cached set is older than 6 h, and passes them as `CODE=perUSD;…` through `nativeSetRates`. Until the first fetch succeeds the standard rates apply. Unit system (metric / imperial / US customary) goes through `nativeSetUnitSystem`; both rebuild the `Calculator` because its customization is fixed at creation.
- A local build with native libs extracted from an older CI APK lacks those two calls; `Soulver` ignores the `UnsatisfiedLinkError`, so everything else still works.

## Signing and versions
Releases are signed with a permanent PKCS12 key, alias `onlysearch`, certificate SHA-256 `a67aee0e…3cf3`.
- Stored as GitHub secrets `RELEASE_KEYSTORE_B64` and `RELEASE_KEYSTORE_PASSWORD`.
- Local backup: `~/.config/only-search/release.p12` + `release.password`. **Losing it means users must uninstall to update.**
- versionCode = `GITHUB_RUN_NUMBER`, versionName `0.2.<run>`.
- Builds before v0.2.32 used per-run debug keys, so each of those needed an uninstall.

## Motion (Nova-inspired, v0.2.0)
Finger-tracked drawer (`Animatable` 0..1, spring settle), home scale/fade behind drawer and search, window background blur, `ActivityOptions.makeScaleUpAnimation` launches, return-home zoom, press-scale, long-press shortcut popup, scrubber bubble.
Two gotchas found the hard way:
- The drawer layer must stay composed (parked off-screen). Adding it on top of the home screen mid-swipe cancels the drag.
- The home screen's gesture lambdas must be stable (`HomeActions` remembered once). Recomposing it mid-swipe also cancels the drag.
- Window blur changes are only made when no finger is down, to avoid disturbing gestures.
CI emulator gesture tests are unreliable: the runner is CPU-starved, `input swipe` gets batched, and System UI ANR dialogs appear. Verify motion on a real device.

## Known gaps / next
- Alternative-units table from the mock-up (needs a second conversion per target unit).
- Licensing with Soulver before any public distribution.
- Weather in the home header: left out on purpose (no data source wanted); the line under the clock shows the next calendar event when enabled.
