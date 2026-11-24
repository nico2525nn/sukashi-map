#!/usr/bin/env bash
# Fail when a release APK is unsigned or signed with the Android debug key.
set -euo pipefail

apk="${1:?usage: verify-release-signing.sh path/to/app-release.apk}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
apksigner="$(find "$sdk/build-tools" -name apksigner -type f | sort -V | tail -1)"
if [[ -z "$apksigner" ]]; then
  echo "apksigner not found under $sdk/build-tools" >&2
  exit 1
fi

certs="$("$apksigner" verify --print-certs "$apk")"
echo "$certs"
if grep -q "CN=Android Debug" <<<"$certs"; then
  echo "Refusing a release APK signed with the Android debug key" >&2
  exit 1
fi
if ! grep -q "certificate DN:" <<<"$certs"; then
  echo "APK is not signed" >&2
  exit 1
fi

# Pin the distribution certificate, not merely "some valid certificate".
expected="${SUKASHI_RELEASE_CERT_SHA256:-571142B858A2903BBC145EB875928AE46CBA0966F11C4D99B5A16EF735974602}"
actual="$(grep -m1 "certificate SHA-256 digest:" <<<"$certs" | awk '{print $NF}' | tr -d ':' | tr '[:lower:]' '[:upper:]')"
if [[ "$actual" != "${expected//:/}" ]]; then
  echo "Release certificate mismatch: expected $expected, got $actual" >&2
  exit 1
fi
echo "Release signature OK"
