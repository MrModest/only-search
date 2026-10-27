#!/usr/bin/env bash
# Build a release-signed arm64 APK locally; key comes from 1Password (op).
# Usage: ./local-build.sh [--install] [N]   N = build number (default: latest CI run + 1)
set -euo pipefail

install=0
if [ "${1:-}" = "--install" ]; then install=1; shift; fi

root=$(cd "$(dirname "$0")" && pwd)
item=${OP_ITEM:-op://KDev/Only Search}
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
sdk=~/Library/Android/sdk
cert=a67aee0ea98c1cbf3f33d81107d6422827eefdb458c009a271a66959fada3cf3 # pinned release signer SHA-256

command -v op >/dev/null || { echo "op (1Password CLI) not installed" >&2; exit 1; }
[ -d "$JAVA_HOME" ] || { echo "JAVA_HOME not found: $JAVA_HOME" >&2; exit 1; }
[ -d "$root/android/app/src/main/jniLibs" ] || { echo "Missing android/app/src/main/jniLibs (prebuilt SoulverCore libs, see docs/ANDROID.md)" >&2; exit 1; }

n=${1:-$(( $(gh run list --workflow build.yml -L 1 --json number -q '.[0].number') + 1 ))}
echo "Version: 0.2.$n"

tmp=$(mktemp -d)
chmod 700 "$tmp"
trap 'rm -rf "$tmp"' EXIT

op read "$item/release.p12" --out-file "$tmp/release.p12"
RELEASE_KEYSTORE_PASSWORD=$(op read "$item/release.password")
export RELEASE_KEYSTORE="$tmp/release.p12" RELEASE_KEYSTORE_PASSWORD GITHUB_RUN_NUMBER=$n

(cd "$root/android" && ./gradlew assembleRelease -q)

apk="$root/android/app/build/outputs/apk/release/app-arm64-v8a-release.apk"
apksigner=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)apksigner
certs=$("$apksigner" verify --print-certs "$apk") # set -e: a failed verify aborts here
grep -qi "^Signer #1 certificate SHA-256 digest: $cert\$" <<<"$certs" || { echo "APK signer cert does not match pinned release cert $cert" >&2; exit 1; }

out=~/Downloads/only-search-0.2.$n-arm64.apk
cp "$apk" "$out"
echo "$out"

if [ "$install" = 1 ]; then "$sdk/platform-tools/adb" install -r "$out"; fi
