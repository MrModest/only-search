#!/usr/bin/env bash
# Installs, or checks, the pinned toolchain for the Swift bridge: Swift, Swift SDK for Android, NDK.
# Idempotent, user-level (no sudo). Needs curl and the Android SDK (cmdline-tools' sdkmanager). Run once, then ./gradlew.
set -euo pipefail
. "$(dirname "$0")/bridge/env.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
dl=https://download.swift.org/swift-$SWIFT_VERSION-release

if [ ! -x "$SWIFT_DIR/usr/bin/swift" ]; then
  echo "Installing Swift $SWIFT_VERSION into $SWIFT_DIR"
  mkdir -p "$SWIFT_DIR"
  if [ "$(uname)" = Darwin ]; then
    curl -fsSL -o "$tmp/swift.pkg" "$dl/xcode/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-osx.pkg"
    pkgutil --expand-full "$tmp/swift.pkg" "$tmp/pkg"
    ditto "$tmp"/pkg/*/Payload "$SWIFT_DIR"
  else
    curl -fsSL "$dl/ubuntu2404/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-ubuntu24.04.tar.gz" | tar xz -C "$SWIFT_DIR" --strip-components=1
  fi
fi
swift --version 2>&1 | grep -q "Swift version $SWIFT_VERSION " || { echo "$SWIFT_DIR does not hold Swift $SWIFT_VERSION:" >&2; swift --version >&2; exit 1; }

if [ ! -f "$ANDROID_NDK_HOME/source.properties" ]; then
  sdkmanager=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
  [ -x "$sdkmanager" ] || sdkmanager=$(command -v sdkmanager) || { echo "sdkmanager not found (install Android cmdline-tools)" >&2; exit 1; }
  { yes || true; } | "$sdkmanager" --sdk_root="$ANDROID_HOME" "ndk;$NDK_VERSION" > /dev/null
fi
grep -q "Pkg.Revision = $NDK_VERSION" "$ANDROID_NDK_HOME/source.properties" || { echo "bad NDK at $ANDROID_NDK_HOME" >&2; exit 1; }

sdk=swift-$SWIFT_VERSION-RELEASE_android
if ! swift sdk list | grep -qx "$sdk"; then
  curl -fsSL -o "$tmp/$sdk.artifactbundle.tar.gz" "$dl/android-sdk/swift-$SWIFT_VERSION-RELEASE/$sdk.artifactbundle.tar.gz"
  swift sdk install "$tmp/$sdk.artifactbundle.tar.gz"
  # Links the NDK sysroot into the Swift SDK.
  bash "$(find ~/Library/org.swift.swiftpm/swift-sdks ~/.swiftpm/swift-sdks ~/.config/swiftpm/swift-sdks -path "*$sdk*" -name setup-android-sdk.sh 2>/dev/null | head -1)"
fi
echo "Toolchain OK: Swift $SWIFT_VERSION ($SWIFT_DIR), $sdk, NDK $NDK_VERSION ($ANDROID_NDK_HOME)"
