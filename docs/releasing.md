# Releasing Sukashi Map

## Android signing

Release builds are unsigned unless a release keystore is configured. The
debug key is never used for a release. Configure one of:

- `keystore.properties` in the repository root (git-ignored), or
- `~/.config/sukashi-map/keystore.properties`, or
- `SUKASHI_KEYSTORE_PATH`, `SUKASHI_KEYSTORE_PASSWORD`, `SUKASHI_KEY_ALIAS`,
  `SUKASHI_KEY_PASSWORD` environment variables.

See `keystore.properties.example`. Generate a key once and keep it outside the
repository:

```bash
keytool -genkeypair -v \
  -keystore "$HOME/.config/sukashi-map/release.jks" \
  -alias sukashi-map -keyalg RSA -keysize 4096 -validity 10000
```

Build and verify:

```bash
./gradlew :app:assembleRelease
bash scripts/verify-release-signing.sh app/build/outputs/apk/release/app-release.apk
```

`v0.3.1` and earlier were distributed with the Android debug certificate. A
build signed with the new release key cannot update those installations in
place; users must uninstall the old app once before installing the release-key
build.

The verification script pins the distribution certificate SHA-256. When
rotating the key, update `SUKASHI_RELEASE_CERT_SHA256` (or the default in
`scripts/verify-release-signing.sh`) as part of the release.

## CI signing

`.github/workflows/android.yml` builds a signed release when these repository
secrets exist:

- `ANDROID_KEYSTORE_BASE64` — base64 of the `.jks` file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow prints the certificate and fails if it is unsigned, signed with
`CN=Android Debug`, or signed with a SHA-256 other than
`vars.RELEASE_CERT_SHA256` (or the pinned default in the script).

## Generated data

Regenerate and commit the generated assets after changing source data or
dependencies:

```bash
cd web
npm run fonts:generate
npm run map:generate
npm run rail:generate
```

`npm test` includes a regression test that fails if the Tokaido line is merged
across companies or if a route falls back to every branch on the line.
