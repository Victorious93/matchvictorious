<p align="center"><img src="assets/logo.jpg" alt="MatchVictorious" width="320"></p>

# MatchVictorious

A privacy-first **data-import hub** for dating platforms (Android, Kotlin + Jetpack Compose, package `com.victorious.match`).

No dating platform offers a legitimate public API, so MatchVictorious works only with the **data exports you request yourself** (GDPR/CCPA). Exports are parsed on-device, stored encrypted, and never leave the phone. The app has no internet permission.

## Features
- Screens: Profiles, Matches, Messages (read-only), Filters, Import, Request-your-data guide, Help, Settings (Delete All Data), plus a consent screen on first launch
- `DatingProvider` interface with capabilities (`canImport`, `canDeepLink`, `canLiveSync = false`)
- `TinderImportProvider`: Tinder `data.json` / ZIP (User, Messages, Usage). Tolerates missing fields
- `GenericImportProvider`: ZIP/JSON/HTML fallback for Hinge, Bumble, OkCupid, Match, POF, Grindr, Feeld, CMB, Badoo
- Deep links open the official app, or its Play Store page
- Import through the Storage Access Framework; AES-256-GCM `EncryptedFile` storage
- No passwords, automation, accessibility service, notification reading or network
- Neon cyan-to-magenta ring theme, dark
- Fleet: EXTERNAL_NODE API for droidcommand-AI (`STATUS`, `PING`, `OPEN_SCREEN`) guarded by a signature permission. See [docs/MANUAL.md](docs/MANUAL.md#fleet-api)

## Build
    ./gradlew testDebugUnitTest assembleDebug
The APK lands in `app/build/outputs/apk/debug/`. CI (GitHub Actions) runs the unit tests, builds the debug APK and uploads it as the `matchvictorious-debug-apk` artifact.

## Signing
- `app/debug.keystore` is a **debug-only** key committed on purpose, so fleet builds share a stable signature. Store/key password: `android`, alias `androiddebugkey`.
- Release keys are never committed (`*.jks` and `release*.keystore` are in .gitignore). Release signing reads `MV_RELEASE_STORE_FILE`, `MV_RELEASE_STORE_PASSWORD`, `MV_RELEASE_KEY_ALIAS` and `MV_RELEASE_KEY_PASSWORD` from the environment.

## Docs
- [User manual](docs/MANUAL.md) (also shown in-app under Help)
- [Privacy policy](PRIVACY.md)

## Disclaimer
Not affiliated with or endorsed by any dating platform. Platform names are used only to identify export formats.
