#!/usr/bin/env bash
# Builds libSoulverBridge.so per ABI and copies it, SoulverCore and every Swift runtime .so it needs
# into the Android app. Needs: swift 6.3.3 + its Android Swift SDK installed, ANDROID_NDK_HOME.
set -euxo pipefail
SOULVER_VERSION=3.5.1
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP=$ROOT/android/app/src/main
SDK_NAME=$(swift sdk list | grep -i android | head -1)
SDK_DIR=$(find ~/.swiftpm/swift-sdks ~/.config/swiftpm/swift-sdks -maxdepth 1 -name "$SDK_NAME*" 2>/dev/null | head -1 || true)
mkdir -p "$ROOT/build/dl"

for pair in aarch64:arm64-v8a x86_64:x86_64; do
  ARCH=${pair%%:*}; ABI=${pair##*:}; TRIPLE=$ARCH-unknown-linux-android28
  ZIP=$ROOT/build/dl/SoulverCore-$ARCH.zip
  [ -f "$ZIP" ] || curl -fsSL -o "$ZIP" "https://github.com/soulverteam/SoulverCore-Multiplatform/releases/download/$SOULVER_VERSION/SoulverCore.$SOULVER_VERSION-Android-$ARCH.zip"
  rm -rf "$ROOT/bridge/soulver" && mkdir -p "$ROOT/bridge/soulver/SoulverCore.swiftmodule"
  unzip -qo "$ZIP" -d "$ROOT/bridge/soulver"
  for t in $ARCH-unknown-linux-android $TRIPLE; do
    cp "$ROOT/bridge/soulver/SoulverCore.swiftinterface" "$ROOT/bridge/soulver/SoulverCore.swiftmodule/$t.swiftinterface"
  done

  (cd "$ROOT/bridge" && swift build -c release --swift-sdk "$TRIPLE")

  OUT=$APP/jniLibs/$ABI; rm -rf "$OUT"; mkdir -p "$OUT"
  cp "$ROOT/bridge/.build/$TRIPLE/release/libSoulverBridge.so" "$ROOT/bridge/soulver/libSoulverCoreDynamic.so" "$OUT/"
  LIBC=$(find "$ANDROID_NDK_HOME" -path "*sysroot/usr/lib/$ARCH-linux-android/libc++_shared.so" | head -1)
  python3 "$ROOT/bridge/collect_deps.py" "$OUT" "$LIBC" $(find "$SDK_DIR" -type d -path "*android*" -name "$ARCH" ; find "$SDK_DIR" -type f -name libswiftCore.so -path "*$ARCH*" -exec dirname {} \;)
done

rm -rf "$APP/assets/soulver" && mkdir -p "$APP/assets/soulver"
cp -r "$ROOT/bridge/soulver/SoulverCore_SoulverCore.resources" "$APP/assets/soulver/"
ls -la "$APP"/jniLibs/*
