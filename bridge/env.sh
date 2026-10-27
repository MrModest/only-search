# Pinned toolchain and where setup.sh puts it. Sourced by setup.sh and bridge/build.sh.
SWIFT_VERSION=6.3.3 # must match the compiler SoulverCore was built with
NDK_VERSION=27.3.13750724 # r27d, same as SoulverCore
SOULVER_VERSION=3.5.1
SWIFT_DIR=${SWIFT_DIR:-$HOME/.local/swift-$SWIFT_VERSION}
if [ "$(uname)" = Darwin ]; then ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}; else ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}; fi
export ANDROID_HOME ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$NDK_VERSION
export PATH=$SWIFT_DIR/usr/bin:$PATH # never Xcode's swift
